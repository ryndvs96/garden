package com.garden.planner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garden.planner.project.BedConfig;
import com.garden.planner.project.GardenProject;
import com.garden.planner.project.GardenZone;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes the project.json manifest inside a .gardenplan directory.
 * Individual bed files are managed by StateSerializer; this class only touches the manifest.
 */
public class ProjectSerializer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final StateSerializer stateSerializer = new StateSerializer();

    /** Write (or overwrite) project.json and ensure the beds/ subdirectory exists. */
    public void saveManifest(GardenProject project) throws IOException {
        Files.createDirectories(project.bedsDir());

        ObjectNode root = mapper.createObjectNode();
        root.put("name", project.getName());
        root.put("formatVersion", project.getFormatVersion());

        ArrayNode zonesArray = root.putArray("zones");
        for (GardenZone zone : project.getZones()) {
            ObjectNode zoneNode = mapper.createObjectNode();
            zoneNode.put("id", zone.id());
            zoneNode.put("name", zone.name());
            ArrayNode bedsArray = zoneNode.putArray("beds");
            for (BedConfig bed : zone.beds()) {
                ObjectNode bedNode = mapper.createObjectNode();
                bedNode.put("id", bed.id());
                bedNode.put("displayName", bed.displayName());
                bedNode.put("fileName", bed.fileName());
                bedsArray.add(bedNode);
            }
            zonesArray.add(zoneNode);
        }

        mapper.writerWithDefaultPrettyPrinter()
              .writeValue(project.manifestFile().toFile(), root);
    }

    /** Load a GardenProject from a .gardenplan directory (must contain project.json). */
    public GardenProject loadProject(Path projectDir) throws IOException {
        Path manifest = projectDir.resolve("project.json");
        JsonNode root = mapper.readTree(manifest.toFile());

        GardenProject project = new GardenProject(root.get("name").asText(), projectDir);

        JsonNode zonesNode = root.get("zones");
        if (zonesNode != null) {
            for (JsonNode zoneNode : zonesNode) {
                List<BedConfig> beds = new ArrayList<>();
                JsonNode bedsNode = zoneNode.get("beds");
                if (bedsNode != null) {
                    for (JsonNode bedNode : bedsNode) {
                        beds.add(new BedConfig(
                                bedNode.get("id").asText(),
                                bedNode.get("displayName").asText(),
                                bedNode.get("fileName").asText()
                        ));
                    }
                }
                GardenZone zone = new GardenZone(
                        zoneNode.get("id").asText(),
                        zoneNode.get("name").asText(),
                        beds
                );
                project.getZones().add(zone);
            }
        }

        return project;
    }

    /**
     * Import a legacy single-bed .json file as a new project.
     * Creates a .gardenplan directory next to the file, copies the bed JSON in,
     * and writes a project.json manifest.
     */
    public GardenProject importLegacyBed(File jsonFile) throws IOException {
        String bedName = stateSerializer.getBedName(jsonFile);
        if (bedName == null || bedName.isBlank()) bedName = jsonFile.getName().replace(".json", "");

        String slug = bedName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        Path dir = jsonFile.toPath().getParent().resolve(slug + ".gardenplan");
        Files.createDirectories(dir.resolve("beds"));

        GardenProject project = new GardenProject(bedName, dir);
        GardenZone zone = project.addZone("Imported");
        BedConfig bed = new BedConfig(UUID.randomUUID().toString(), bedName, jsonFile.getName());
        zone.beds().add(bed);

        Files.copy(jsonFile.toPath(), dir.resolve("beds").resolve(jsonFile.getName()),
                StandardCopyOption.REPLACE_EXISTING);

        saveManifest(project);
        return project;
    }
}

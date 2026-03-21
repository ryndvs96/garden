package com.garden.planner.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutable top-level model for a .gardenplan project directory.
 * zones/beds are backed by mutable lists; call ProjectSerializer.saveManifest()
 * after every structural mutation.
 */
public class GardenProject {

    private String name;
    private final String formatVersion = "1";
    private final List<GardenZone> zones = new ArrayList<>();
    /** Not serialized — set by ProjectSerializer after loading. */
    private transient Path projectDir;

    public GardenProject() {}

    public GardenProject(String name, Path projectDir) {
        this.name = name;
        this.projectDir = projectDir;
    }

    // --- accessors ---

    public String getName()                { return name; }
    public void setName(String name)       { this.name = name; }
    public String getFormatVersion()       { return formatVersion; }
    public List<GardenZone> getZones()     { return zones; }
    public Path getProjectDir()            { return projectDir; }
    public void setProjectDir(Path dir)    { this.projectDir = dir; }
    public Path bedsDir()                  { return projectDir.resolve("beds"); }
    public Path manifestFile()             { return projectDir.resolve("project.json"); }

    // --- mutators ---

    public GardenZone addZone(String name) {
        GardenZone zone = new GardenZone(UUID.randomUUID().toString(), name);
        zones.add(zone);
        return zone;
    }

    public void removeZone(String zoneId) {
        zones.removeIf(z -> z.id().equals(zoneId));
    }

    /**
     * Creates a BedConfig with a unique fileName slug and adds it to the specified zone.
     * Returns the new BedConfig, or null if the zone was not found.
     */
    public BedConfig addBed(String zoneId, String displayName) {
        String base = displayName.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        String fileName = base + ".json";
        int suffix = 2;
        while (fileNameExists(fileName)) {
            fileName = base + "-" + suffix + ".json";
            suffix++;
        }
        BedConfig bed = new BedConfig(UUID.randomUUID().toString(), displayName, fileName);
        findZone(zoneId).ifPresent(z -> z.beds().add(bed));
        return bed;
    }

    public void removeBed(String bedId) {
        for (GardenZone zone : zones) {
            zone.beds().removeIf(b -> b.id().equals(bedId));
        }
    }

    public Optional<GardenZone> findZone(String zoneId) {
        return zones.stream().filter(z -> z.id().equals(zoneId)).findFirst();
    }

    public Optional<BedConfig> findBed(String bedId) {
        return zones.stream()
                .flatMap(z -> z.beds().stream())
                .filter(b -> b.id().equals(bedId))
                .findFirst();
    }

    private boolean fileNameExists(String fileName) {
        return zones.stream()
                .flatMap(z -> z.beds().stream())
                .anyMatch(b -> b.fileName().equals(fileName));
    }
}

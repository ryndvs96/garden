package com.garden.planner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garden.planner.project.SeedBank;
import com.garden.planner.project.SeedEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves and loads the global seed bank at ~/.garden-planner/seedbank.json.
 */
public class SeedBankSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Load existing file if present, or return an empty SeedBank. */
    public SeedBank loadOrCreate(Path filePath) throws IOException {
        SeedBank bank = new SeedBank(filePath);
        if (!Files.exists(filePath)) return bank;

        JsonNode root = mapper.readTree(filePath.toFile());
        JsonNode entries = root.get("entries");
        if (entries == null) return bank;

        for (JsonNode node : entries) {
            bank.observableEntries().add(new SeedEntry(
                    node.get("id").asText(),
                    node.path("zone").asText("Back"),
                    node.path("plantType").asText(""),
                    node.path("plantName").asText(""),
                    node.path("widthIn").asInt(12),
                    node.path("heightIn").asInt(12),
                    node.path("isStrict").asBoolean(true),
                    node.path("notes").asText(""),
                    node.path("quantity").asInt(25)
            ));
        }
        return bank;
    }

    public void save(SeedBank bank) throws IOException {
        Files.createDirectories(bank.getFilePath().getParent());

        ObjectNode root = mapper.createObjectNode();
        ArrayNode entries = root.putArray("entries");
        for (SeedEntry e : bank.observableEntries()) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", e.id());
            node.put("zone", e.zone());
            node.put("plantType", e.plantType());
            node.put("plantName", e.plantName());
            node.put("widthIn", e.widthIn());
            node.put("heightIn", e.heightIn());
            node.put("isStrict", e.isStrict());
            node.put("notes", e.notes());
            node.put("quantity", e.quantity());
            entries.add(node);
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(bank.getFilePath().toFile(), root);
        bank.setDirty(false);
    }
}

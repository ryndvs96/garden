package com.garden.planner.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the last 5 opened project directories in ~/.garden-planner/recent.json.
 */
public class RecentProjects {

    private static final int MAX = 5;
    private final Path filePath;
    private final ObjectMapper mapper = new ObjectMapper();

    public RecentProjects() {
        this.filePath = Path.of(System.getProperty("user.home"), ".garden-planner", "recent.json");
    }

    /** Returns up to MAX paths, most-recent first, skipping any that no longer exist. */
    public List<Path> getAll() {
        List<Path> result = new ArrayList<>();
        if (!Files.exists(filePath)) return result;
        try {
            JsonNode root = mapper.readTree(filePath.toFile());
            JsonNode recentNode = root.get("recent");
            if (recentNode == null) return result;
            for (JsonNode node : recentNode) {
                Path p = Path.of(node.asText());
                if (Files.exists(p)) result.add(p);
            }
        } catch (Exception ignored) {}
        return result;
    }

    /** Prepend path (de-duplicating) and persist. */
    public void add(Path projectDir) {
        List<Path> all = getAll();
        all.removeIf(p -> p.equals(projectDir));
        all.add(0, projectDir);
        if (all.size() > MAX) all = all.subList(0, MAX);
        persist(all);
    }

    private void persist(List<Path> paths) {
        try {
            Files.createDirectories(filePath.getParent());
            ObjectNode root = mapper.createObjectNode();
            ArrayNode arr = root.putArray("recent");
            for (Path p : paths) arr.add(p.toString());
            mapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), root);
        } catch (Exception ignored) {}
    }
}

package com.garden.planner.data;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Saves and loads PlacementState to/from JSON using Jackson.
 */
public class StateSerializer {

    private final ObjectMapper mapper = new ObjectMapper();

    public void save(PlacementState state, String bedName, File file) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("bedName", bedName);
        root.put("score", state.getScore());
        root.put("gridRows", state.getGridRows());
        root.put("gridCols", state.getGridCols());
        root.put("penaltyMode", state.getPenaltyMode().name());

        ArrayNode placedArray = root.putArray("placed");
        for (PlacedPlant pp : state.getPlaced()) {
            ObjectNode ppNode = mapper.createObjectNode();
            ppNode.put("row", pp.row());
            ppNode.put("col", pp.col());
            ppNode.put("locked", pp.locked());
            ppNode.set("plant", plantInstanceToJson(pp.plant()));
            ArrayNode cellsNode = ppNode.putArray("cells");
            for (GridCell cell : pp.cells()) {
                ObjectNode cellNode = mapper.createObjectNode();
                cellNode.put("r", cell.r());
                cellNode.put("c", cell.c());
                cellsNode.add(cellNode);
            }
            placedArray.add(ppNode);
        }

        ArrayNode unplacedArray = root.putArray("unplaced");
        for (PlantInstance p : state.getUnplaced()) {
            unplacedArray.add(plantInstanceToJson(p));
        }

        file.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, root);
    }

    public PlacementState load(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        int gridRows = root.get("gridRows").asInt();
        int gridCols = root.get("gridCols").asInt();
        PenaltyMode mode = PenaltyMode.valueOf(root.get("penaltyMode").asText("CELL"));

        List<PlacedPlant> placed = new ArrayList<>();
        for (JsonNode ppNode : root.get("placed")) {
            int row = ppNode.get("row").asInt();
            int col = ppNode.get("col").asInt();
            boolean locked = ppNode.get("locked").asBoolean(false);
            PlantInstance plant = plantInstanceFromJson(ppNode.get("plant"));
            // Recompute cells from (row, col) using the current algorithm so old saves
            // are upgraded to the correct hex-disk footprint on load.
            Set<GridCell> cells = HexGrid.computeCells(row, col, plant.widthIn(), plant.isStrict(),
                    gridRows, gridCols);
            if (cells == null) {
                // Fallback: read saved cells (e.g. plant moved out of strict zone bounds)
                cells = new LinkedHashSet<>();
                for (JsonNode cellNode : ppNode.get("cells")) {
                    cells.add(new GridCell(cellNode.get("r").asInt(), cellNode.get("c").asInt()));
                }
            }
            placed.add(new PlacedPlant(plant, row, col, cells, locked));
        }

        List<PlantInstance> unplaced = new ArrayList<>();
        for (JsonNode pNode : root.get("unplaced")) {
            unplaced.add(plantInstanceFromJson(pNode));
        }

        return new PlacementState(placed, unplaced, gridRows, gridCols, mode);
    }

    public String getBedName(File file) throws IOException {
        JsonNode root = mapper.readTree(file);
        return root.get("bedName").asText("");
    }

    private ObjectNode plantInstanceToJson(PlantInstance p) {
        ObjectNode node = mapper.createObjectNode();
        node.put("zone", p.zone());
        node.put("plantType", p.plantType());
        node.put("plantName", p.plantName());
        node.put("widthIn", p.widthIn());
        node.put("heightIn", p.heightIn());
        node.put("isStrict", p.isStrict());
        node.put("instanceIdx", p.instanceIdx());
        node.put("code", p.code());
        return node;
    }

    private PlantInstance plantInstanceFromJson(JsonNode node) {
        return new PlantInstance(
            node.get("zone").asText(),
            node.get("plantType").asText(),
            node.get("plantName").asText(),
            node.get("widthIn").asInt(),
            node.get("heightIn").asInt(),
            node.get("isStrict").asBoolean(),
            node.get("instanceIdx").asInt(),
            node.get("code").asText()
        );
    }
}

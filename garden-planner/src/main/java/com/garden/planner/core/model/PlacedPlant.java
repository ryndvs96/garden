package com.garden.planner.core.model;

import java.util.Set;

public record PlacedPlant(
    PlantInstance plant,
    int row,
    int col,
    Set<GridCell> cells,
    boolean locked
) {
    public PlacedPlant withLocked(boolean locked) {
        return new PlacedPlant(plant, row, col, cells, locked);
    }
}

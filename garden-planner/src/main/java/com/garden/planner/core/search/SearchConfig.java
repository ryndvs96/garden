package com.garden.planner.core.search;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.PenaltyMode;

public record SearchConfig(
    int nStarts,
    int nIters,
    PenaltyMode penaltyMode,
    long timeoutMs,
    long baseSeed,
    int nPositions,
    int gridRows,
    int gridCols
) {
    public static SearchConfig defaults() {
        return new SearchConfig(
            30, 400, PenaltyMode.CELL, 30000L, 42L, 150,
            HexGrid.GRID_ROWS, HexGrid.GRID_COLS
        );
    }
}

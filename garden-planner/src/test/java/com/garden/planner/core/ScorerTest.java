package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.LocalSearchEngine;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class ScorerTest {

    @Test
    void scoringWeights_placedPlant_addsDelta() {
        PlantInstance p = new PlantInstance("Back", "Veg", "Tomato", 6, 36, true, 1, "T");
        PlacementState state = new PlacementState(List.of(), List.of(p),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(p, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta = state.addPlant(pp);

        // n_placed=1.0 + n_unique=1.0 = 2.0 (no overlaps)
        assertThat(delta).isCloseTo(2.0, within(0.001));
    }

    @Test
    void scoringWeights_twoSameSpecies_noUniqueBonus() {
        PlantInstance p1 = new PlantInstance("Back", "Veg", "Tomato", 6, 36, true, 1, "T");
        PlantInstance p2 = new PlantInstance("Back", "Veg", "Tomato", 6, 36, true, 2, "T");
        PlacementState state = new PlacementState(List.of(), List.of(p1, p2),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        PlacedPlant pp1 = LocalSearchEngine.makePlacedPlant(p1, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta1 = state.addPlant(pp1);
        assertThat(delta1).isCloseTo(2.0, within(0.001)); // n_placed + n_unique

        // Second of same species: only n_placed, no n_unique
        PlacedPlant pp2 = LocalSearchEngine.makePlacedPlant(p2, 4, 50,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta2 = state.addPlant(pp2);
        assertThat(delta2).isCloseTo(1.0, within(0.001)); // n_placed only
    }

    @Test
    void scoringWeights_strictOverlap_penalty() {
        PlantInstance p1 = new PlantInstance("Back", "Veg", "Tomato", 2, 36, true, 1, "T");
        PlantInstance p2 = new PlantInstance("Back", "Herb", "Pepper", 2, 36, true, 1, "P");

        // Place them at same position to create overlap
        Set<GridCell> cells = Set.of(new GridCell(4, 10), new GridCell(4, 11));
        PlacedPlant pp1 = new PlacedPlant(p1, 4, 10, cells, false);
        PlacedPlant pp2 = new PlacedPlant(p2, 4, 10, cells, false);

        PlacementState state = new PlacementState(List.of(pp1), List.of(p2),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        double delta2 = state.addPlant(pp2);
        // n_placed=1.0 + n_unique=1.0 + strict_strict_cells penalty for 2 overlapping cells
        // penalty = -2.0 * 1 (each cell had 1 existing strict) * 2 cells = -4.0
        assertThat(delta2).isLessThan(0.0); // should be penalized
    }
}

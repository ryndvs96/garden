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

    /**
     * A loose fill flower placed in completely open space should score higher than the same
     * flower wedged between two strict bok choy plants.
     *
     * Scoring uses W_LOOSE_OPEN_CELLS: +0.5 per loose-plant cell that lands on empty ground.
     *   Overlap case  (1 of 3 cells free): W_N_PLACED(1) + W_N_UNIQUE(1) + 0.5×1 = 2.5
     *   Open space    (3 of 3 cells free): W_N_PLACED(1) + W_N_UNIQUE(1) + 0.5×3 = 3.5
     * Placing anywhere still yields a net positive, but open space is always preferred.
     */
    @Test
    void looseFlower_openSpace_scoresHigherThanOverlappingStrictPlants() {
        // Two strict bok choy plants, side by side, NOT overlapping each other.
        PlantInstance bokChoy1 = new PlantInstance("Back", "Veg", "Bok Choy", 6, 18, true,  1, "BC");
        PlantInstance bokChoy2 = new PlantInstance("Back", "Veg", "Bok Choy", 6, 18, true,  2, "BC");
        PlantInstance flower   = new PlantInstance("Back", "Flower", "Marigold", 6, 12, false, 1, "MG");

        // Bok choy 1: centre (4,10), footprint covers cols 9-11
        Set<GridCell> bc1Cells = Set.of(new GridCell(4, 9), new GridCell(4, 10), new GridCell(4, 11));
        PlacedPlant ppBc1 = new PlacedPlant(bokChoy1, 4, 10, bc1Cells, false);

        // Bok choy 2: centre (4,14), footprint covers cols 13-15 — no overlap with bc1
        Set<GridCell> bc2Cells = Set.of(new GridCell(4, 13), new GridCell(4, 14), new GridCell(4, 15));
        PlacedPlant ppBc2 = new PlacedPlant(bokChoy2, 4, 14, bc2Cells, false);

        // Build two identical base states (both bok choy placed, flower unplaced).
        PlacementState stateOverlap   = new PlacementState(List.of(ppBc1, ppBc2), List.of(flower),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);
        PlacementState stateOpenSpace = new PlacementState(List.of(ppBc1, ppBc2), List.of(flower),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        // Flower wedged between the two bok choy: overlaps cell (4,11) from bc1 and
        // (4,13) from bc2 — only the middle cell (4,12) is empty.
        Set<GridCell> flowerOverlapCells = Set.of(new GridCell(4, 11), new GridCell(4, 12), new GridCell(4, 13));
        PlacedPlant ppFlowerOverlap = new PlacedPlant(flower, 4, 12, flowerOverlapCells, false);

        // Flower in open space far from both bok choy — all 3 cells are empty.
        Set<GridCell> flowerOpenCells = Set.of(new GridCell(4, 40), new GridCell(4, 41), new GridCell(4, 42));
        PlacedPlant ppFlowerOpen = new PlacedPlant(flower, 4, 41, flowerOpenCells, false);

        double deltaOverlap   = stateOverlap.addDelta(ppFlowerOverlap);
        double deltaOpenSpace = stateOpenSpace.addDelta(ppFlowerOpen);

        // Both placements are net positive (placing anywhere beats not placing).
        assertThat(deltaOverlap).isGreaterThan(0.0);
        assertThat(deltaOpenSpace).isGreaterThan(0.0);

        // Open space earns the bonus on all 3 cells; overlap earns it on only 1.
        assertThat(deltaOpenSpace).isGreaterThan(deltaOverlap);

        // Spot-check exact values: overlap=2.5, open=3.5
        assertThat(deltaOverlap).isCloseTo(2.5, within(0.001));
        assertThat(deltaOpenSpace).isCloseTo(3.5, within(0.001));
    }
}

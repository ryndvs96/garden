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
        PlantInstance p = PlantInstance.builder().zone("Back").plantType("Veg").plantName("Tomato").widthIn(6).heightIn(36).isStrict(true).instanceIdx(1).code("T").build();
        PlacementState state = new PlacementState(List.of(), List.of(p),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(p, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta = state.addPlant(pp);

        // n_placed=1.0 + n_unique=1.0 + 37 cells × cs(1,0)=3 each = 113.0
        assertThat(delta).isCloseTo(113.0, within(0.001));
    }

    @Test
    void scoringWeights_twoSameSpecies_noUniqueBonus() {
        PlantInstance p1 = PlantInstance.builder().zone("Back").plantType("Veg").plantName("Tomato").widthIn(6).heightIn(36).isStrict(true).instanceIdx(1).code("T").build();
        PlantInstance p2 = p1.toBuilder().instanceIdx(2).build();
        PlacementState state = new PlacementState(List.of(), List.of(p1, p2),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        PlacedPlant pp1 = LocalSearchEngine.makePlacedPlant(p1, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta1 = state.addPlant(pp1);
        assertThat(delta1).isCloseTo(113.0, within(0.001)); // n_placed + n_unique + 37 cells×3

        // Second of same species: only n_placed, no n_unique
        PlacedPlant pp2 = LocalSearchEngine.makePlacedPlant(p2, 4, 50,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        double delta2 = state.addPlant(pp2);
        assertThat(delta2).isCloseTo(112.0, within(0.001)); // n_placed only + 37 cells×3
    }

    @Test
    void scoringWeights_strictOverlap_penalty() {
        PlantInstance p1 = PlantInstance.builder().zone("Back").plantType("Veg").plantName("Tomato").widthIn(2).heightIn(36).isStrict(true).instanceIdx(1).code("T").build();
        PlantInstance p2 = PlantInstance.builder().zone("Back").plantType("Herb").plantName("Pepper").widthIn(2).heightIn(36).isStrict(true).instanceIdx(1).code("P").build();

        // Place them at same position to create overlap
        Set<GridCell> cells = Set.of(new GridCell(4, 10), new GridCell(4, 11));
        PlacedPlant pp1 = PlacedPlant.builder().plant(p1).row(4).col(10).cells(cells).locked(false).build();
        PlacedPlant pp2 = PlacedPlant.builder().plant(p2).row(4).col(10).cells(cells).locked(false).build();

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
     * Scoring uses cellScore(s, l): cs(0,1)=+2 per open cell, cs(1,k)=0 under strict.
     *   Overlap case  (1 of 3 cells open): W_N_PLACED(1) + W_N_UNIQUE(1) + 2×1 = 4.0
     *   Open space    (3 of 3 cells open): W_N_PLACED(1) + W_N_UNIQUE(1) + 2×3 = 8.0
     * Placing anywhere still yields a net positive, but open space is always preferred.
     */
    @Test
    void looseFlower_openSpace_scoresHigherThanOverlappingStrictPlants() {
        // Two strict bok choy plants, side by side, NOT overlapping each other.
        PlantInstance bokChoy1 = PlantInstance.builder().zone("Back").plantType("Veg").plantName("Bok Choy").widthIn(6).heightIn(18).isStrict(true).instanceIdx(1).code("BC").build();
        PlantInstance bokChoy2 = bokChoy1.toBuilder().instanceIdx(2).build();
        PlantInstance flower   = PlantInstance.builder().zone("Back").plantType("Flower").plantName("Marigold").widthIn(6).heightIn(12).isStrict(false).instanceIdx(1).code("MG").build();

        // Bok choy 1: centre (4,10), footprint covers cols 9-11
        Set<GridCell> bc1Cells = Set.of(new GridCell(4, 9), new GridCell(4, 10), new GridCell(4, 11));
        PlacedPlant ppBc1 = PlacedPlant.builder().plant(bokChoy1).row(4).col(10).cells(bc1Cells).locked(false).build();

        // Bok choy 2: centre (4,14), footprint covers cols 13-15 — no overlap with bc1
        Set<GridCell> bc2Cells = Set.of(new GridCell(4, 13), new GridCell(4, 14), new GridCell(4, 15));
        PlacedPlant ppBc2 = PlacedPlant.builder().plant(bokChoy2).row(4).col(14).cells(bc2Cells).locked(false).build();

        // Build two identical base states (both bok choy placed, flower unplaced).
        PlacementState stateOverlap   = new PlacementState(List.of(ppBc1, ppBc2), List.of(flower),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);
        PlacementState stateOpenSpace = new PlacementState(List.of(ppBc1, ppBc2), List.of(flower),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);

        // Flower wedged between the two bok choy: overlaps cell (4,11) from bc1 and
        // (4,13) from bc2 — only the middle cell (4,12) is empty.
        Set<GridCell> flowerOverlapCells = Set.of(new GridCell(4, 11), new GridCell(4, 12), new GridCell(4, 13));
        PlacedPlant ppFlowerOverlap = PlacedPlant.builder().plant(flower).row(4).col(12).cells(flowerOverlapCells).locked(false).build();

        // Flower in open space far from both bok choy — all 3 cells are empty.
        Set<GridCell> flowerOpenCells = Set.of(new GridCell(4, 40), new GridCell(4, 41), new GridCell(4, 42));
        PlacedPlant ppFlowerOpen = PlacedPlant.builder().plant(flower).row(4).col(41).cells(flowerOpenCells).locked(false).build();

        double deltaOverlap   = stateOverlap.addDelta(ppFlowerOverlap);
        double deltaOpenSpace = stateOpenSpace.addDelta(ppFlowerOpen);

        // Both placements are net positive (placing anywhere beats not placing).
        assertThat(deltaOverlap).isGreaterThan(0.0);
        assertThat(deltaOpenSpace).isGreaterThan(0.0);

        // Open space earns cs(0,1)=2 on all 3 cells; overlap earns it on only 1.
        assertThat(deltaOpenSpace).isGreaterThan(deltaOverlap);

        // Spot-check exact values: overlap=4.0, open=8.0
        assertThat(deltaOverlap).isCloseTo(4.0, within(0.001));
        assertThat(deltaOpenSpace).isCloseTo(8.0, within(0.001));
    }
}

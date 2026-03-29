package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that regenerate (LocalSearchEngine with fixedPlants) correctly relocates
 * unlocked plants away from locked plant positions.
 */
class RegenerateTest {

    /**
     * Two locked strict bok-choy plants sit side by side.
     * A loose flower is initially wedged between them, overlapping both.
     * After running the search with the bok choys as fixedPlants, the flower
     * should be relocated to open space (no overlap with either locked plant)
     * and the candidate score should exceed the baseline.
     */
    @Test
    void regenerate_loosePlantRelocatesAwayFromLockedStrictPlants() {
        int rows = HexGrid.GRID_ROWS, cols = HexGrid.GRID_COLS;

        PlantInstance bokChoy1 = PlantInstance.builder()
                .zone("Back").plantType("Veg").plantName("Bok Choy")
                .widthIn(6).heightIn(18).isStrict(true).instanceIdx(1).code("BC")
                .build();
        PlantInstance bokChoy2 = bokChoy1.toBuilder().instanceIdx(2).build();
        PlantInstance flower = PlantInstance.builder()
                .zone("Back").plantType("Flower").plantName("Marigold")
                .widthIn(6).heightIn(12).isStrict(false).instanceIdx(1).code("MG")
                .build();

        // Place bok choys side by side and lock them
        PlacedPlant ppBc1 = LocalSearchEngine.makePlacedPlant(bokChoy1, 4, 6,  rows, cols);
        PlacedPlant ppBc2 = LocalSearchEngine.makePlacedPlant(bokChoy2, 4, 12, rows, cols);
        assertThat(ppBc1).isNotNull();
        assertThat(ppBc2).isNotNull();
        PlacedPlant lockedBc1 = ppBc1.withLocked(true);
        PlacedPlant lockedBc2 = ppBc2.withLocked(true);

        // Place the flower overlapping both bok choys — wedged between them
        Set<GridCell> flowerOverlapCells = Set.copyOf(
                LocalSearchEngine.makePlacedPlant(flower, 4, 9, rows, cols).cells());
        PlacedPlant overlappingFlower = PlacedPlant.builder()
                .plant(flower).row(4).col(9).cells(flowerOverlapCells).locked(false)
                .build();

        // Confirm the initial placement actually overlaps at least one locked plant
        boolean initiallyOverlaps = flowerOverlapCells.stream()
                .anyMatch(c -> lockedBc1.cells().contains(c) || lockedBc2.cells().contains(c));
        assertThat(initiallyOverlaps).as("flower should initially overlap a locked plant").isTrue();

        // Compute the baseline score (mirrors doRegenerate's previousScore logic)
        PlacementState baselineState = new PlacementState(
                List.of(lockedBc1, lockedBc2, overlappingFlower), List.of(),
                rows, cols, PenaltyMode.CELL);
        double previousScore = baselineState.snapshot().getScore();

        // Run the search — locked plants fixed, flower is the free plant to optimise
        SearchConfig config = SearchConfig.defaults()
                .nStarts(10).nIters(200).baseSeed(42L)
                .gridRows(rows).gridCols(cols)
                .fixedPlants(List.of(lockedBc1, lockedBc2))
                .build();

        SearchResult result = new LocalSearchEngine()
                .search(List.of(flower), config, new SearchMetrics(), new AtomicBoolean(false));

        PlacementState candidate = result.state();
        double candidateScore = candidate.snapshot().getScore();

        // Score must improve
        assertThat(candidateScore)
                .as("search should find a better score than the overlapping baseline (%.2f)", previousScore)
                .isGreaterThan(previousScore);

        // Flower must be placed (not left in unplaced)
        PlacedPlant resultFlower = candidate.getPlaced().stream()
                .filter(pp -> !pp.locked())
                .findFirst().orElse(null);
        assertThat(resultFlower).as("flower should be placed after search").isNotNull();

        // Flower cells must not overlap either locked bok choy
        for (GridCell cell : resultFlower.cells()) {
            assertThat(lockedBc1.cells().contains(cell) || lockedBc2.cells().contains(cell))
                    .as("flower cell %s overlaps a locked bok choy", cell)
                    .isFalse();
        }
    }

    /**
     * cleanupOverlaps skips loose plants, so without allowRemove they stay heavily overlapping
     * and cells score 0 instead of +2. A 4×6 grid is given 8 loose marigolds (widthIn=3,
     * footprint≈7 cells) — far more than the grid can absorb cleanly. With allowRemove=true
     * the search drops enough plants that remaining ones score +2 per cell, beating the
     * forced all-in result.
     */
    @Test
    void allowRemove_dropsExcessLoosePlantsForBetterScore() {
        int rows = 4, cols = 6;

        List<PlantInstance> plants = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            plants.add(PlantInstance.builder()
                    .zone("Any").plantType("Flower").plantName("Marigold" + i)
                    .widthIn(3).heightIn(12).isStrict(false).instanceIdx(i).code("MG")
                    .build());
        }

        SearchConfig base = SearchConfig.defaults()
                .nStarts(10).nIters(300).baseSeed(42L)
                .gridRows(rows).gridCols(cols)
                .fixedPlants(List.of())
                .build();

        SearchResult forced = new LocalSearchEngine()
                .search(plants, base.toBuilder().allowRemove(false).build(),
                        new SearchMetrics(), new AtomicBoolean(false));
        double forcedScore = forced.state().snapshot().getScore();

        SearchResult trimmed = new LocalSearchEngine()
                .search(plants, base.toBuilder().allowRemove(true).build(),
                        new SearchMetrics(), new AtomicBoolean(false));
        double trimmedScore = trimmed.state().snapshot().getScore();

        assertThat(trimmedScore)
                .as("allowRemove should yield a better score (%.2f) than forcing all 8 loose plants (%.2f)",
                        trimmedScore, forcedScore)
                .isGreaterThan(forcedScore);

        int placedCount = (int) trimmed.state().getPlaced().stream()
                .filter(pp -> !pp.locked()).count();
        assertThat(placedCount)
                .as("some loose plants should have been dropped (placed=%d, total=8)", placedCount)
                .isLessThan(8);
    }
}

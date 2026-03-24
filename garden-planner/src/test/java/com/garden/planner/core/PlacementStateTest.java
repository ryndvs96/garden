package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.LocalSearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PlacementStateTest {

    private PlantInstance strictPlant;
    private PlantInstance loosePlant;
    private PlacementState emptyState;

    @BeforeEach
    void setUp() {
        strictPlant = PlantInstance.builder().zone("Back").plantType("Tomato").plantName("Cherry Tomato").widthIn(6).heightIn(36).isStrict(true).instanceIdx(1).code("T").build();
        loosePlant  = PlantInstance.builder().zone("Back").plantType("Herb").plantName("Basil").widthIn(3).heightIn(12).isStrict(false).instanceIdx(1).code("B").build();
        emptyState = new PlacementState(List.of(), List.of(strictPlant, loosePlant),
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS, PenaltyMode.CELL);
    }

    @Test
    void initialScore_empty() {
        assertThat(emptyState.getScore()).isEqualTo(0.0);
        assertThat(emptyState.getNPlaced()).isEqualTo(0);
        assertThat(emptyState.getUnplaced()).hasSize(2);
    }

    @Test
    void addPlant_thenRemove_returnsToOriginalScore() {
        double initialScore = emptyState.getScore();

        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(strictPlant, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        assertThat(pp).isNotNull();

        double addDelta = emptyState.addPlant(pp);
        assertThat(emptyState.getNPlaced()).isEqualTo(1);
        assertThat(emptyState.getScore()).isEqualTo(initialScore + addDelta, within(0.001));

        double removeDelta = emptyState.removeDelta(0);
        emptyState.removePlant(0);
        assertThat(emptyState.getScore()).isCloseTo(initialScore, within(0.001));
        assertThat(emptyState.getNPlaced()).isEqualTo(0);
        assertThat(emptyState.getUnplaced()).contains(strictPlant);
    }

    @Test
    void removeDelta_isNonMutating() {
        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(strictPlant, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        emptyState.addPlant(pp);

        double scoreBeforeRemoveDelta = emptyState.getScore();
        int placedCountBefore = emptyState.getNPlaced();

        double delta = emptyState.removeDelta(0);

        // State must be unchanged
        assertThat(emptyState.getScore()).isEqualTo(scoreBeforeRemoveDelta);
        assertThat(emptyState.getNPlaced()).isEqualTo(placedCountBefore);
        // Delta should be negative (removing a placed plant)
        assertThat(delta).isLessThan(0);
    }

    @Test
    void snapshot_isIndependentDeepCopy() {
        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(strictPlant, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        emptyState.addPlant(pp);

        PlacementState snap = emptyState.snapshot();

        // Modify original
        emptyState.removePlant(0);

        // Snapshot should be unchanged
        assertThat(snap.getNPlaced()).isEqualTo(1);
        assertThat(snap.getScore()).isGreaterThan(emptyState.getScore());
    }

    @Test
    void speciesCount_tracksCorrectly() {
        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(strictPlant, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        emptyState.addPlant(pp);

        PlantSpecies sp = new PlantSpecies(strictPlant.plantType(), strictPlant.plantName());
        assertThat(emptyState.getSpeciesCount().get(sp)).isEqualTo(1);
        assertThat(emptyState.getNUnique()).isEqualTo(1);
    }

    @Test
    void countStrictOverlaps_noOverlap() {
        PlacedPlant pp = LocalSearchEngine.makePlacedPlant(strictPlant, 4, 10,
                HexGrid.GRID_ROWS, HexGrid.GRID_COLS);
        emptyState.addPlant(pp);
        assertThat(emptyState.countStrictOverlaps()).isEqualTo(0);
    }
}

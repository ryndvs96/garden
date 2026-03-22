package com.garden.planner.core;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.garden.planner.core.model.PenaltyMode;
import com.garden.planner.core.model.PlacedPlant;
import com.garden.planner.core.model.PlacementState;
import com.garden.planner.core.model.PlantInstance;
import com.garden.planner.core.search.LocalSearchEngine;

/**
 * Preference tests for the scoring function.
 *
 * Each test builds two complete bed states and asserts that one scores higher
 * than the other. Use these to lock in behaviours you care about — if a scoring
 * change breaks a preference, the test will fail and you'll know exactly what changed.
 *
 * ── How to add a new preference test ────────────────────────────────────────
 *
 *   @Test
 *   void <preferred state>_over_<worse state>() {
 *       BedScenario better = new BedScenario(rows, cols)
 *           .strict("Tomato",   row, col, widthInches)
 *           .loose ("Marigold", row, col, widthInches);
 *
 *       BedScenario worse = new BedScenario(rows, cols)
 *           .strict("Tomato",   row, col, widthInches)
 *           .loose ("Marigold", row, col, widthInches);  // bad position
 *
 *       better.assertScoresHigherThan(worse, "description of better", "description of worse");
 *   }
 *
 * ── Coordinate guide ────────────────────────────────────────────────────────
 *   BedScenario(rows, cols) sizes the grid in inches (1 cell ≈ 1 inch).
 *   A typical 4 ft × 8 ft raised bed is BedScenario(48, 96).
 *   Plant widthIn sets the hex footprint radius; keep centres away from edges
 *   by at least widthIn/2 rows/cols so strict plants don't go out of bounds.
 * ────────────────────────────────────────────────────────────────────────────
 */
class ScoringPreferenceTest {

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void looseFlower_inOpenSpace_over_looseFlowerOverlappingStrictPlants() {
        // Two bok choy side by side. A marigold placed in open space (all cells free)
        // should score higher than one wedged between the bok choy (overlapping both).
        BedScenario openSpace = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 18, 6)
                .loose ("Marigold", 4, 40, 6);   // clear ground, far right

        BedScenario overlapping = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 18, 6)
                .loose ("Marigold", 4, 14, 6);   // centre between the two bok choy

        openSpace.assertScoresHigherThan(overlapping,
                "marigold in open space",
                "marigold overlapping both bok choy");
    }

    @Test
    void strictPlant_inOpenSpace_over_strictPlantOverlappingAnotherStrict() {
        // One bok choy already placed. A second bok choy in open space should score
        // higher than one placed directly on top of the first.
        BedScenario openSpace = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 40, 6);   // clear ground

        BedScenario overlapping = new BedScenario(28, 48)
                .strict("Bok Choy", 5, 10, 6)
                .strict("Bok Choy", 4, 10, 6);   // a lot of overlap

        openSpace.assertScoresHigherThan(overlapping,
                "two bok choy in separate open spots",
                "two bok choy with a lot of overlap");
    }

    @Test
    void twoDistinctSpecies_over_twoOfSameSpecies() {
        // A bed with a tomato and a pepper (two unique species) should score higher
        // than a bed with two tomatoes, because diversity earns W_N_UNIQUE per species.
        BedScenario distinct = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6)
                .strict("Pepper", 4, 30, 6);

        BedScenario duplicates = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6)
                .strict("Tomato", 4, 30, 6);

        distinct.assertScoresHigherThan(duplicates,
                "tomato + pepper (two distinct species)",
                "tomato + tomato (same species twice)");
    }

    @Test
    void twoPlantsWithRoom_over_onePlant() {
        // A bed with two non-overlapping plants should score higher than the same
        // bed with only one plant — placing more always increases score.
        BedScenario two = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6)
                .strict("Pepper", 4, 40, 6);   // plenty of room, no overlap

        BedScenario one = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6);

        two.assertScoresHigherThan(one,
                "two plants with room to spare",
                "one plant");
    }

    @Test
    void twoStrictPlants_noOverlap_over_oneOverlappingCell() {
        // Two bok choy with no shared cells should score higher than two bok choy
        // whose footprints touch by exactly one cell.
        // widthIn=6 → radius 3. Centers at cols 10 and 17 are just far enough apart
        // that footprints share one cell; cols 10 and 40 share none.
        BedScenario noOverlap = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 40, 6);

        BedScenario oneOverlap = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 16, 6);   // hex distance 6 = 2×radius → exactly 1 shared cell

        noOverlap.assertScoresHigherThan(oneOverlap,
                "two bok choy with no overlapping cells",
                "two bok choy sharing one cell");
    }

    @Test
    void looseFlower_moreFreeCell_over_fewerFreeCells() {
        // A loose flower with all 7 cells in open space should score higher than one
        // where most cells are buried under strict plants.
        // widthIn=7 → radius 3 → 7-cell footprint.
        BedScenario allFree = new BedScenario(28, 48)
                .loose("Alyssum", 4, 40, 7);   // nothing nearby — all 7 cells open

        BedScenario mostBuried = new BedScenario(28, 48)
                .strict("Tomato",  4, 10, 6)
                .strict("Tomato",  4, 16, 6)   // two strict plants crowd the centre
                .loose ("Alyssum", 4, 13, 7);  // flower between them: most cells buried

        allFree.assertScoresHigherThan(mostBuried,
                "loose flower with all cells in open space",
                "loose flower with most cells buried under strict plants");
    }

    @Test
    void twoLoosePlants_noOverlap_over_twoLoosePlantsOverlapping() {
        // Two ground-cover plants placed in separate spots should score higher than
        // two placed on top of each other, penalised by W_LOOSE_LOOSE_CELLS.
        BedScenario noOverlap = new BedScenario(28, 48)
                .loose("Alyssum", 4, 10, 6)
                .loose("Alyssum", 4, 40, 6);   // far apart, no shared cells

        BedScenario overlapping = new BedScenario(28, 48)
                .loose("Alyssum", 4, 10, 6)
                .loose("Alyssum", 4, 10, 6);   // same position — full overlap

        noOverlap.assertScoresHigherThan(overlapping,
                "two loose plants in separate spots",
                "two loose plants stacked on the same spot");
    }

    @Test
    void placingPartiallyOverlappingLoosePlant_over_notPlacingIt() {
        // Even when a loose plant's cells all land on strict-plant ground (earning
        // zero open-cell bonus), placing it still adds W_N_PLACED + W_N_UNIQUE > 0.
        BedScenario withFlower = new BedScenario(28, 48)
                .strict("Tomato",  4, 10, 6)
                .loose ("Alyssum", 4, 10, 6);  // centre coincides with tomato — fully buried

        BedScenario withoutFlower = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6);

        withFlower.assertScoresHigherThan(withoutFlower,
                "bed with a fully-buried loose plant placed",
                "bed with the loose plant absent");
    }

    @Test
    void strictOverlap_worsensWith_moreCells() {
        // Two strict plants sharing 3 cells should score lower than two sharing 1 cell.
        // hex distance 4 (cols 10 & 14) → 3 shared cells
        // hex distance 6 (cols 10 & 16) → 1 shared cell
        BedScenario oneShared = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 16, 6);  // 1 shared cell

        BedScenario threeShared = new BedScenario(28, 48)
                .strict("Bok Choy", 4, 10, 6)
                .strict("Bok Choy", 4, 14, 6);  // 3 shared cells

        oneShared.assertScoresHigherThan(threeShared,
                "two strict plants sharing 1 cell",
                "two strict plants sharing 3 cells");
    }

    @Test
    void threeDistinctSpecies_over_twoDistinctSpecies() {
        // Each new unique species earns W_N_UNIQUE = +1.0, so three distinct species
        // should always outscore two at the same plant count.
        BedScenario three = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6)
                .strict("Pepper", 4, 25, 6)
                .strict("Basil",  4, 40, 6);

        BedScenario two = new BedScenario(28, 48)
                .strict("Tomato", 4, 10, 6)
                .strict("Pepper", 4, 25, 6)
                .strict("Tomato", 4, 40, 6);

        three.assertScoresHigherThan(two,
                "three distinct species",
                "two distinct species");
    }

    @Test
    void fivePlants_over_three_over_one() {
        // Filling a bed beats leaving it sparse when all plants fit without overlap.
        BedScenario five = new BedScenario(28, 84)
                .strict("Bok Choy", 4,  8, 6)
                .strict("Bok Choy", 4, 22, 6)
                .strict("Bok Choy", 4, 36, 6)
                .strict("Bok Choy", 4, 50, 6)
                .strict("Bok Choy", 4, 64, 6);

        BedScenario three = new BedScenario(28, 84)
                .strict("Bok Choy", 4,  8, 6)
                .strict("Bok Choy", 4, 22, 6)
                .strict("Bok Choy", 4, 36, 6);

        BedScenario one = new BedScenario(28, 84)
                .strict("Bok Choy", 4,  8, 6);

        five.assertScoresHigherThan(three, "five plants",  "three plants");
        three.assertScoresHigherThan(one,  "three plants", "one plant");
    }

    // ── Add your preference tests below ─────────────────────────────────────

    // ── BedScenario helper ───────────────────────────────────────────────────

    /**
     * Fluent builder for a complete bed state used in score comparisons.
     *
     * @param rows  bed depth in inches (hex rows)
     * @param cols  bed width in inches (hex cols)
     *
     * Usage:
     *   new BedScenario(28, 48)
     *       .strict("Tomato",   4, 10, 6)
     *       .loose ("Marigold", 4, 30, 6)
     *       .assertScoresHigherThan(otherScenario, "this desc", "other desc");
     */
    static class BedScenario {

        private final int rows;
        private final int cols;
        private final List<PlacedPlant> placed = new ArrayList<>();
        private int nextIdx = 1;

        BedScenario(int rows, int cols) {
            this.rows = rows;
            this.cols = cols;
        }

        /** Add a strict plant at (row, col) with the given width in inches. */
        BedScenario strict(String name, int row, int col, int widthIn) {
            return add(name, widthIn, true, row, col);
        }

        /** Add a loose plant at (row, col) with the given width in inches. */
        BedScenario loose(String name, int row, int col, int widthIn) {
            return add(name, widthIn, false, row, col);
        }

        /** Total score of this bed state. */
        double score() {
            return new PlacementState(placed, List.of(), rows, cols, PenaltyMode.CELL).getScore();
        }

        /**
         * Assert that this bed state scores strictly higher than {@code other}.
         */
        void assertScoresHigherThan(BedScenario other, String thisDesc, String otherDesc) {
            double thisScore  = this.score();
            double otherScore = other.score();
            assertThat(thisScore)
                    .as("Expected '%s' (score=%.3f) to score higher than '%s' (score=%.3f)",
                            thisDesc, thisScore, otherDesc, otherScore)
                    .isGreaterThan(otherScore);
        }

        // ── internals ────────────────────────────────────────────────────────

        private BedScenario add(String name, int widthIn, boolean isStrict, int row, int col) {
            PlantInstance pi = new PlantInstance(
                    "Any", isStrict ? "Veg" : "Flower", name,
                    widthIn, isStrict ? 24 : 12, isStrict, nextIdx++,
                    name.substring(0, 1));
            PlacedPlant pp = LocalSearchEngine.makePlacedPlant(pi, row, col, rows, cols);
            assertThat(pp)
                    .as("Could not place '%s' at (%d,%d) — out of bounds for a %dx%d bed?",
                            name, row, col, rows, cols)
                    .isNotNull();
            placed.add(pp);
            return this;
        }
    }
}

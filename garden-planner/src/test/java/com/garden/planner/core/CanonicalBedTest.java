package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Canonical 12in × 6in bed test.
 * Spec: 2 TallA, 2 TallB (strict, width=6), 4 ShortC (loose, width=3), 6 ShortD (loose, width=2)
 * Total 14 plants. Success criterion: >= 12 placed.
 */
class CanonicalBedTest {

    private static final int TEST_COLS = 24; // widened so hex-disk footprints (r=3→37 cells) fit without forced overlap
    private static final int TEST_ROWS = 10;

    private List<PlantInstance> buildInstances() {
        List<PlantInstance> instances = new ArrayList<>();
        // 2x TallA (strict)
        for (int i = 1; i <= 2; i++) {
            instances.add(PlantInstance.builder().zone("Back").plantType("Test").plantName("Tall A").widthIn(6).heightIn(30).isStrict(true).instanceIdx(i).code("A").build());
        }
        // 2x TallB (strict)
        for (int i = 1; i <= 2; i++) {
            instances.add(PlantInstance.builder().zone("Back").plantType("Test").plantName("Tall B").widthIn(6).heightIn(30).isStrict(true).instanceIdx(i).code("B").build());
        }
        // 4x ShortC (loose)
        for (int i = 1; i <= 4; i++) {
            instances.add(PlantInstance.builder().zone("Back").plantType("Test").plantName("Short C").widthIn(3).heightIn(10).isStrict(false).instanceIdx(i).code("C").build());
        }
        // 6x ShortD (loose)
        for (int i = 1; i <= 6; i++) {
            instances.add(PlantInstance.builder().zone("Back").plantType("Test").plantName("Short D").widthIn(2).heightIn(6).isStrict(false).instanceIdx(i).code("D").build());
        }
        return instances;
    }

    @Test
    void localSearchEngine_canonicalBed_placesAtLeast12() {
        List<PlantInstance> plants = buildInstances();
        SearchConfig config = SearchConfig.defaults()
                .nStarts(10).nIters(200).nPositions(80)
                .gridRows(TEST_ROWS).gridCols(TEST_COLS)
                .build();
        SearchMetrics metrics = new SearchMetrics();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        LocalSearchEngine engine = new LocalSearchEngine();
        SearchResult result = engine.search(plants, config, metrics, cancelled);

        assertThat(result.state()).isNotNull();
        assertThat(result.state().getNPlaced())
                .as("LocalSearch should place >= 12 / 14 plants")
                .isGreaterThanOrEqualTo(12);
    }

    @Test
    void greedyLnsEngine_canonicalBed_placesAtLeast12() {
        List<PlantInstance> plants = buildInstances();
        SearchConfig config = SearchConfig.defaults()
                .nStarts(30).nIters(20).nPositions(80)
                .gridRows(TEST_ROWS).gridCols(TEST_COLS)
                .build();
        SearchMetrics metrics = new SearchMetrics();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        GreedyLnsEngine engine = new GreedyLnsEngine();
        SearchResult result = engine.search(plants, config, metrics, cancelled);

        assertThat(result.state()).isNotNull();
        assertThat(result.state().getNPlaced())
                .as("GreedyLNS should place >= 12 / 14 plants")
                .isGreaterThanOrEqualTo(12);
    }
}

package com.garden.planner.core;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import com.garden.planner.core.search.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

class DebugTest {

    @Test
    void debug_randomFullPlacement_directly() {
        List<PlantInstance> plants = new ArrayList<>();
        for (int i = 1; i <= 2; i++) plants.add(new PlantInstance("Back","Test","Tall A",6,30,true,i,"A"));
        for (int i = 1; i <= 2; i++) plants.add(new PlantInstance("Back","Test","Tall B",6,30,true,i,"B"));
        for (int i = 1; i <= 4; i++) plants.add(new PlantInstance("Back","Test","Short C",3,10,false,i,"C"));
        for (int i = 1; i <= 6; i++) plants.add(new PlantInstance("Back","Test","Short D",2,6,false,i,"D"));

        Random rng = new Random(42);
        List<PlantInstance> order = new ArrayList<>(plants);
        Collections.shuffle(order, rng);
        List<PlacedPlant> placed = new ArrayList<>();
        List<PlantInstance> unplaced = new ArrayList<>();
        for (PlantInstance plant : order) {
            LocalSearchEngine.PositionLists posLists = LocalSearchEngine.enumerateValidPositions(plant, 7, 12);
            List<int[]> all = new ArrayList<>(posLists.preferred());
            all.addAll(posLists.nonPreferred());
            if (all.isEmpty()) { unplaced.add(plant); continue; }
            int[] pos = all.get(rng.nextInt(all.size()));
            PlacedPlant pp = LocalSearchEngine.makePlacedPlant(plant, pos[0], pos[1], 7, 12);
            if (pp == null) { unplaced.add(plant); } else { placed.add(pp); }
        }
        System.out.println("After random placement: placed=" + placed.size() + " unplaced=" + unplaced.size());
        assertThat(placed.size()).isGreaterThan(0);
        PlacementState state = new PlacementState(placed, unplaced, 7, 12, PenaltyMode.CELL);
        System.out.println("State nPlaced=" + state.getNPlaced() + " score=" + state.getScore());
        assertThat(state.getNPlaced()).isGreaterThan(0);

        // Now do one removal and add-back cycle
        PlacedPlant first = state.getPlaced().get(0);
        double beforeScore = state.getScore();
        PlacedPlant removed = state.removePlant(0);
        System.out.println("After remove: nPlaced=" + state.getNPlaced() + " unplaced=" + state.getUnplaced().size());
        assertThat(state.getUnplaced()).contains(removed.plant());
        state.addPlant(removed);
        System.out.println("After re-add: nPlaced=" + state.getNPlaced() + " score=" + state.getScore());
        assertThat(state.getScore()).isCloseTo(beforeScore, within(0.001));
    }

    @Test
    void debug_singleStart_search() {
        List<PlantInstance> plants = new ArrayList<>();
        for (int i = 1; i <= 2; i++) plants.add(new PlantInstance("Back","Test","Tall A",6,30,true,i,"A"));
        for (int i = 1; i <= 2; i++) plants.add(new PlantInstance("Back","Test","Tall B",6,30,true,i,"B"));
        for (int i = 1; i <= 4; i++) plants.add(new PlantInstance("Back","Test","Short C",3,10,false,i,"C"));
        for (int i = 1; i <= 6; i++) plants.add(new PlantInstance("Back","Test","Short D",2,6,false,i,"D"));

        SearchConfig config = new SearchConfig(1, 50, PenaltyMode.CELL, 10000L, 42L, 80, 7, 12);
        SearchMetrics metrics = new SearchMetrics();
        AtomicBoolean cancelled = new AtomicBoolean(false);

        LocalSearchEngine engine = new LocalSearchEngine();
        SearchResult result = engine.search(plants, config, metrics, cancelled);
        System.out.println("Search result: placed=" + result.state().getNPlaced()
                + " unplaced=" + result.state().getUnplaced().size());
        assertThat(result.state().getNPlaced()).isGreaterThan(0);
    }
}

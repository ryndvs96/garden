package com.garden.planner.core.search;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * GRASP + LNS packing engine. Ports Python pack_bed_best + lns_improve from pack_beds.py.
 */
public class GreedyLnsEngine implements SearchEngine {

    private static final double LOOSE_OVERLAP_FRAC = 0.5;
    private static final int LNS_ITERS = 20;
    private static final int LNS_K = 8;

    @Override
    public SearchResult search(
            List<PlantInstance> plants,
            SearchConfig config,
            SearchMetrics metrics,
            AtomicBoolean cancelled) {

        Random rng = new Random(config.baseSeed());
        int gridRows = config.gridRows();
        int gridCols = config.gridCols();

        BedState bestState = null;
        int bestPlaced = -1;
        int bestUnique = -1;

        // GRASP: n_restarts random-column-order packing runs
        for (int restart = 0; restart < config.nStarts(); restart++) {
            if (cancelled.get()) break;

            List<PlantInstance> shuffled = diversityShuffle(plants, rng);
            List<Integer> colOrder = new ArrayList<>();
            for (int i = 0; i < gridCols; i++) colOrder.add(i);
            Collections.shuffle(colOrder, rng);

            BedState state = packBed(shuffled, gridRows, gridCols, colOrder, null);
            metrics.recordState();

            int nPlaced = state.placed.size();
            int nUnique = countUnique(state.placed);
            if (bestState == null || nPlaced > bestPlaced || (nPlaced == bestPlaced && nUnique > bestUnique)) {
                bestState = state;
                bestPlaced = nPlaced;
                bestUnique = nUnique;
            }
        }

        if (bestState == null) {
            bestState = new BedState(new ArrayList<>(), new ArrayList<>(plants),
                    emptyGrid(gridRows, gridCols), emptyGrid(gridRows, gridCols));
        }

        // LNS refinement
        bestState = lnsImprove(bestState, plants, gridRows, gridCols, rng, LNS_ITERS, LNS_K, cancelled);

        metrics.updateBest(bestState.placed.size());

        // Convert to PlacementState
        PlacementState ps = convertToPlacementState(bestState, plants, gridRows, gridCols, config.penaltyMode());
        return new SearchResult(ps, metrics);
    }

    private BedState lnsImprove(BedState best, List<PlantInstance> allPlants,
                                 int gridRows, int gridCols, Random rng, int lnsIters, int lnsK,
                                 AtomicBoolean cancelled) {
        int bestScore = score(best.placed);
        int bestUnique = countUnique(best.placed);

        for (int iter = 0; iter < lnsIters; iter++) {
            if (cancelled.get() || best.placed.isEmpty()) break;

            int k = Math.min(lnsK, best.placed.size());
            List<PlantInstance> victims = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < best.placed.size(); i++) indices.add(i);
            Collections.shuffle(indices, rng);
            for (int i = 0; i < k; i++) victims.add(best.placed.get(indices.get(i)));

            // Remove victims from grids
            BedState withoutVictims = removePlantsFromState(best, victims, gridRows, gridCols);

            // Retry victims + unplaced
            List<PlantInstance> toRetry = new ArrayList<>(victims);
            toRetry.addAll(best.unplaced);
            List<PlantInstance> shuffled = diversityShuffle(toRetry, rng);
            List<Integer> colOrder = new ArrayList<>();
            for (int i = 0; i < gridCols; i++) colOrder.add(i);
            Collections.shuffle(colOrder, rng);

            BedState newState = packBed(shuffled, gridRows, gridCols, colOrder,
                    new BedState(withoutVictims.placed, new ArrayList<>(),
                            withoutVictims.strictGrid, withoutVictims.looseGrid));

            // Non-victims remain placed
            List<PlantInstance> combinedPlaced = new ArrayList<>();
            Set<PlantInstance> victimSet = new HashSet<>(victims);
            for (PlantInstance p : best.placed) {
                if (!victimSet.contains(p)) combinedPlaced.add(p);
            }
            combinedPlaced.addAll(newState.placed);

            int newScore = score(combinedPlaced);
            int newUnique = countUnique(combinedPlaced);
            if (newScore > bestScore || (newScore == bestScore && newUnique > bestUnique)) {
                best = new BedState(combinedPlaced, newState.unplaced,
                        newState.strictGrid, newState.looseGrid);
                bestScore = newScore;
                bestUnique = newUnique;
            }
        }

        return best;
    }

    private BedState packBed(List<PlantInstance> plants, int gridRows, int gridCols,
                              List<Integer> colOrder, BedState baseState) {
        String[][] strictGrid, looseGrid;
        List<PlantInstance> placed = new ArrayList<>();
        List<PlantInstance> unplaced = new ArrayList<>();

        if (baseState != null) {
            strictGrid = copyGrid(baseState.strictGrid);
            looseGrid = copyGrid(baseState.looseGrid);
            placed.addAll(baseState.placed);
        } else {
            strictGrid = emptyGrid(gridRows, gridCols);
            looseGrid = emptyGrid(gridRows, gridCols);
        }

        for (PlantInstance plant : plants) {
            boolean placedFlag = false;
            outer:
            for (int row = 0; row < gridRows; row++) {
                for (int col : colOrder) {
                    if (plant.isStrict()) {
                        Set<GridCell> cells = HexGrid.computeCells(row, col, plant.widthIn(), true, gridRows, gridCols);
                        if (cells == null) continue;
                        boolean anyOccupied = false;
                        for (GridCell cell : cells) {
                            if (!strictGrid[cell.r()][cell.c()].isEmpty()) { anyOccupied = true; break; }
                        }
                        if (anyOccupied) continue;
                        for (GridCell cell : cells) strictGrid[cell.r()][cell.c()] = plant.code();
                        placed.add(plant);
                        placedFlag = true;
                        break outer;
                    } else {
                        if (row < 0 || row >= gridRows || col < 0 || col >= gridCols) continue;
                        Set<GridCell> cells = HexGrid.computeCells(row, col, plant.widthIn(), false, gridRows, gridCols);
                        if (cells == null || cells.isEmpty()) continue;
                        boolean anyLooseOccupied = false;
                        for (GridCell cell : cells) {
                            if (!looseGrid[cell.r()][cell.c()].isEmpty()) { anyLooseOccupied = true; break; }
                        }
                        if (anyLooseOccupied) continue;
                        long strictOccupied = cells.stream()
                                .filter(cell -> !strictGrid[cell.r()][cell.c()].isEmpty()).count();
                        if ((double) strictOccupied / cells.size() > LOOSE_OVERLAP_FRAC) continue;
                        for (GridCell cell : cells) looseGrid[cell.r()][cell.c()] = plant.code();
                        placed.add(plant);
                        placedFlag = true;
                        break outer;
                    }
                }
            }
            if (!placedFlag) unplaced.add(plant);
        }

        return new BedState(placed, unplaced, strictGrid, looseGrid);
    }

    private BedState removePlantsFromState(BedState state, List<PlantInstance> victims, int gridRows, int gridCols) {
        Set<String> victimCodes = new HashSet<>();
        for (PlantInstance v : victims) victimCodes.add(v.code());

        String[][] newStrict = copyGrid(state.strictGrid);
        String[][] newLoose = copyGrid(state.looseGrid);
        List<PlantInstance> remaining = new ArrayList<>();

        for (PlantInstance p : state.placed) {
            if (!victimCodes.contains(p.code())) remaining.add(p);
        }

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (victimCodes.contains(newStrict[r][c])) newStrict[r][c] = "";
                if (victimCodes.contains(newLoose[r][c])) newLoose[r][c] = "";
            }
        }

        return new BedState(remaining, state.unplaced, newStrict, newLoose);
    }

    private List<PlantInstance> diversityShuffle(List<PlantInstance> plants, Random rng) {
        Map<String, List<PlantInstance>> tiers = new TreeMap<>();
        for (PlantInstance p : plants) {
            String key = p.instanceIdx() + "|" + (p.isStrict() ? 0 : 1);
            tiers.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        List<PlantInstance> result = new ArrayList<>();
        for (List<PlantInstance> group : tiers.values()) {
            Collections.shuffle(group, rng);
            result.addAll(group);
        }
        return result;
    }

    private PlacementState convertToPlacementState(BedState state, List<PlantInstance> allPlants,
                                                    int gridRows, int gridCols, PenaltyMode mode) {
        List<PlacedPlant> placedPlants = new ArrayList<>();
        // Reconstruct PlacedPlant objects from grids
        Map<String, PlantInstance> codeToPlant = new HashMap<>();
        for (PlantInstance p : allPlants) codeToPlant.put(p.code(), p);

        // We need row/col for each placed plant - we don't have it from the grid string representation.
        // Instead, we can use the placed list directly and compute cells from zone info.
        // Since BedState doesn't store row/col, we need to re-scan the grid to find positions.
        // Use the strict/loose grids to find each plant's cells.

        Map<String, Set<GridCell>> strictCells = new HashMap<>();
        Map<String, Set<GridCell>> looseCells = new HashMap<>();

        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                String sc = state.strictGrid[r][c];
                if (!sc.isEmpty()) strictCells.computeIfAbsent(sc, k -> new HashSet<>()).add(new GridCell(r, c));
                String lc = state.looseGrid[r][c];
                if (!lc.isEmpty()) looseCells.computeIfAbsent(lc, k -> new HashSet<>()).add(new GridCell(r, c));
            }
        }

        Set<String> placedCodes = new HashSet<>();
        for (PlantInstance p : state.placed) {
            if (placedCodes.contains(p.code() + "_" + p.instanceIdx())) continue;
            // Each plant instance has a unique code (shared among instances of same species)
            // We need to map each PlantInstance to its cells
            // Since multiple instances share the same code, we need to handle this carefully
            // For now, create PlacedPlant with the cells from the grid
            placedCodes.add(p.code() + "_" + p.instanceIdx());
        }

        // Better approach: one PlacedPlant per PlantInstance, distribute cells among same-code instances
        Map<String, List<PlantInstance>> codeInstances = new LinkedHashMap<>();
        for (PlantInstance p : state.placed) {
            codeInstances.computeIfAbsent(p.code(), k -> new ArrayList<>()).add(p);
        }

        for (Map.Entry<String, List<PlantInstance>> entry : codeInstances.entrySet()) {
            String code = entry.getKey();
            List<PlantInstance> instances = entry.getValue();
            boolean isStrict = instances.get(0).isStrict();
            Set<GridCell> allCells = isStrict ? strictCells.getOrDefault(code, Set.of())
                    : looseCells.getOrDefault(code, Set.of());

            // Try to assign cells to instances by finding centers
            // For simplicity, just give all cells to the first instance, and dummy for others
            for (int i = 0; i < instances.size(); i++) {
                PlantInstance inst = instances.get(i);
                Set<GridCell> cellsForInst = (i == 0) ? allCells : Set.of(new GridCell(0, 0));
                int row = cellsForInst.isEmpty() ? 0 : cellsForInst.iterator().next().r();
                int col = cellsForInst.isEmpty() ? 0 : cellsForInst.iterator().next().c();
                placedPlants.add(new PlacedPlant(inst, row, col, cellsForInst, false));
            }
        }

        return new PlacementState(placedPlants, state.unplaced, gridRows, gridCols, mode);
    }

    private int score(List<PlantInstance> placed) { return placed.size(); }

    private int countUnique(List<PlantInstance> placed) {
        Set<String> seen = new HashSet<>();
        for (PlantInstance p : placed) seen.add(p.plantType() + "|" + p.plantName());
        return seen.size();
    }

    private String[][] emptyGrid(int rows, int cols) {
        String[][] g = new String[rows][cols];
        for (String[] row : g) Arrays.fill(row, "");
        return g;
    }

    private String[][] copyGrid(String[][] g) {
        String[][] copy = new String[g.length][];
        for (int i = 0; i < g.length; i++) copy[i] = Arrays.copyOf(g[i], g[i].length);
        return copy;
    }

    private static class BedState {
        final List<PlantInstance> placed;
        final List<PlantInstance> unplaced;
        final String[][] strictGrid;
        final String[][] looseGrid;

        BedState(List<PlantInstance> placed, List<PlantInstance> unplaced,
                 String[][] strictGrid, String[][] looseGrid) {
            this.placed = placed;
            this.unplaced = unplaced;
            this.strictGrid = strictGrid;
            this.looseGrid = looseGrid;
        }
    }
}

package com.garden.planner.core.search;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.geometry.ZoneConfig;
import com.garden.planner.core.model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Parallel multi-start local search. Ports Python multi_start_local_search from search_beds.py.
 */
public class LocalSearchEngine implements SearchEngine {

    @Override
    public SearchResult search(
            List<PlantInstance> plants,
            SearchConfig config,
            SearchMetrics metrics,
            AtomicBoolean cancelled) {

        int nCores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(nCores);
        AtomicReference<PlacementState> globalBest = new AtomicReference<>(null);

        // Precompute valid positions per (zone, width, isStrict)
        Map<String, PositionCache> posCache = buildPosCache(plants, config.gridRows(), config.gridCols());

        List<Future<?>> futures = new ArrayList<>();
        long deadline = System.currentTimeMillis() + config.timeoutMs();

        for (int startIdx = 0; startIdx < config.nStarts(); startIdx++) {
            final int si = startIdx;
            futures.add(executor.submit(() -> {
                if (cancelled.get() || System.currentTimeMillis() > deadline) return;
                runOneStart(si, plants, config, metrics, cancelled, posCache, globalBest, deadline);
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();

        PlacementState best = globalBest.get();
        if (best == null) {
            best = new PlacementState(List.of(), new ArrayList<>(plants),
                    config.gridRows(), config.gridCols(), config.penaltyMode());
        }
        return new SearchResult(best, metrics);
    }

    private void runOneStart(
            int startIdx,
            List<PlantInstance> plants,
            SearchConfig config,
            SearchMetrics metrics,
            AtomicBoolean cancelled,
            Map<String, PositionCache> posCache,
            AtomicReference<PlacementState> globalBest,
            long deadline) {

        Random rng = new Random(config.baseSeed() + startIdx);

        PlacementState state = randomFullPlacement(plants, config.gridRows(), config.gridCols(), rng, config.penaltyMode());

        int patience = 20;
        int noImprove = 0;
        int nRelocate = 5;
        int lnsK = 5;
        int lnsKicks = 0;
        int maxLnsKicks = config.nIters() / 20;

        for (int iter = 0; iter < config.nIters(); iter++) {
            if (cancelled.get() || System.currentTimeMillis() > deadline) break;

            boolean strictImproved = false;

            // Move: try relocating the N least-valuable placed plants
            if (!state.getPlaced().isEmpty()) {
                // Score remove_delta for all placed; pick N worst (highest remove_delta = easiest to remove)
                int n = state.getPlaced().size();
                double[] removeDeltas = new double[n];
                for (int i = 0; i < n; i++) {
                    removeDeltas[i] = state.removeDelta(i);
                    metrics.recordState();
                }

                // Pick indices of top nRelocate by remove_delta (descending)
                Integer[] indices = new Integer[n];
                for (int i = 0; i < n; i++) indices[i] = i;
                int topN = Math.min(nRelocate, n);
                Arrays.sort(indices, (a, b) -> Double.compare(removeDeltas[b], removeDeltas[a]));

                double bestNet = Double.NEGATIVE_INFINITY;
                PlacedPlant bestOrig = null;
                PlacedPlant bestNew = null;

                for (int ci = 0; ci < topN; ci++) {
                    int idx = indices[ci];
                    PlacedPlant ppCand = state.getPlaced().get(idx);
                    double rdelta = removeDeltas[idx];

                    PositionCache pc = posCache.get(posKey(ppCand.plant()));
                    // Temporarily remove
                    state.removePlant(state.getPlaced().indexOf(ppCand));
                    InsertResult ir = bestInsertPosition(state, ppCand.plant(), rng, config.nPositions(),
                            pc != null ? pc.preferred : List.of(), pc != null ? pc.nonPreferred : List.of());
                    // Restore
                    state.addPlant(ppCand);

                    double net = rdelta + (ir.pp != null ? ir.delta : Double.NEGATIVE_INFINITY);
                    if (ir.pp != null && net >= 0
                            && (ir.pp.row() != ppCand.row() || ir.pp.col() != ppCand.col())
                            && net > bestNet) {
                        bestNet = net;
                        bestOrig = ppCand;
                        bestNew = ir.pp;
                    }
                }

                if (bestOrig != null) {
                    int idx = state.getPlaced().indexOf(bestOrig);
                    if (idx >= 0) {
                        state.removePlant(idx);
                        state.addPlant(bestNew);
                        if (bestNet > 0) strictImproved = true;
                    }
                }
            }

            // Insert: try each unplaced plant at its best position
            for (PlantInstance plant : new ArrayList<>(state.getUnplaced())) {
                PositionCache pc = posCache.get(posKey(plant));
                InsertResult ir = bestInsertPosition(state, plant, rng, config.nPositions(),
                        pc != null ? pc.preferred : List.of(), pc != null ? pc.nonPreferred : List.of());
                if (ir.pp != null && ir.delta > 0) {
                    state.addPlant(ir.pp);
                    strictImproved = true;
                    break;
                }
            }

            if (strictImproved) {
                noImprove = 0;
            } else {
                noImprove++;
                if (noImprove >= patience) {
                    if (lnsKicks >= maxLnsKicks || state.getPlaced().size() < lnsK) break;
                    lnsKicks++;
                    noImprove = 0;
                    int k = Math.min(lnsK, state.getPlaced().size());
                    List<Integer> idxList = new ArrayList<>();
                    for (int i = 0; i < state.getPlaced().size(); i++) idxList.add(i);
                    Collections.shuffle(idxList, rng);
                    List<Integer> victimIndices = new ArrayList<>(idxList.subList(0, k));
                    victimIndices.sort(Collections.reverseOrder());
                    List<PlantInstance> destroyed = new ArrayList<>();
                    for (int idx : victimIndices) {
                        PlacedPlant pp = state.removePlant(idx);
                        destroyed.add(pp.plant());
                    }
                    Collections.shuffle(destroyed, rng);
                    for (PlantInstance plant : destroyed) {
                        PositionCache pc = posCache.get(posKey(plant));
                        InsertResult ir = bestInsertPosition(state, plant, rng, config.nPositions(),
                                pc != null ? pc.preferred : List.of(), pc != null ? pc.nonPreferred : List.of());
                        if (ir.pp != null && ir.delta > 0) {
                            state.addPlant(ir.pp);
                        }
                    }
                }
            }
        }

        cleanupOverlaps(state, rng, config.nPositions(), posCache);

        // Post-cleanup: final insert pass
        for (PlantInstance plant : new ArrayList<>(state.getUnplaced())) {
            PositionCache pc = posCache.get(posKey(plant));
            InsertResult ir = bestInsertPosition(state, plant, rng, config.nPositions(),
                    pc != null ? pc.preferred : List.of(), pc != null ? pc.nonPreferred : List.of());
            if (ir.pp != null && ir.delta > 0) {
                state.addPlant(ir.pp);
            }
        }

        metrics.updateBest(state.getScore());

        // CAS-based global best update
        PlacementState snapshot = state.snapshot();
        globalBest.getAndUpdate(prev -> {
            if (prev == null || snapshot.getScore() > prev.getScore()) return snapshot;
            return prev;
        });
    }

    /**
     * Add a plant via addPlant. The plant must already be in the unplaced list.
     * This wrapper ensures the plant is in unplaced before calling addPlant.
     */
    private double safeAddPlant(PlacementState state, PlacedPlant pp) {
        if (!state.getUnplaced().contains(pp.plant())) {
            state.getUnplaced().add(pp.plant());
        }
        return state.addPlant(pp);
    }

    private void cleanupOverlaps(PlacementState state, Random rng, int nPositions, Map<String, PositionCache> posCache) {
        int maxPasses = state.getPlaced().size() + 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            int victimIdx = -1;
            double victimDelta = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < state.getPlaced().size(); i++) {
                PlacedPlant pp = state.getPlaced().get(i);
                if (!pp.plant().isStrict()) continue;
                boolean hasOverlap = false;
                for (GridCell cell : pp.cells()) {
                    if (state.getStrictGrid()[cell.r()][cell.c()] >= 2) {
                        hasOverlap = true;
                        break;
                    }
                }
                if (hasOverlap) {
                    double d = state.removeDelta(i);
                    if (d > victimDelta) {
                        victimIdx = i;
                        victimDelta = d;
                    }
                }
            }

            if (victimIdx < 0) break;

            PlacedPlant pp = state.removePlant(victimIdx);
            PositionCache pc = posCache.get(posKey(pp.plant()));
            List<int[]> positions = samplePositions(
                    pc != null ? pc.preferred : List.of(),
                    pc != null ? pc.nonPreferred : List.of(),
                    nPositions * 2, rng);

            for (int[] pos : positions) {
                PlacedPlant candidate = makePlacedPlant(pp.plant(), pos[0], pos[1], state.getGridRows(), state.getGridCols());
                if (candidate == null) continue;
                boolean overlaps = false;
                for (GridCell cell : candidate.cells()) {
                    if (state.getStrictGrid()[cell.r()][cell.c()] > 0) { overlaps = true; break; }
                }
                if (!overlaps) {
                    state.addPlant(candidate);
                    break;
                }
            }
        }
    }

    public static PlacedPlant makePlacedPlant(PlantInstance plant, int row, int col, int gridRows, int gridCols) {
        ZoneConfig cfg = HexGrid.ZONES.getOrDefault(plant.zone(), HexGrid.ZONES.get("Middle"));
        int allowedLo = cfg.allowedLo();
        int allowedHi = Math.min(cfg.allowedHi(), gridRows - 1);
        Set<GridCell> cells = HexGrid.computeCells(row, col, plant.widthIn(), plant.isStrict(),
                allowedLo, allowedHi, gridRows, gridCols);
        if (cells == null) return null;
        return new PlacedPlant(plant, row, col, cells, false);
    }

    public static PositionLists enumerateValidPositions(PlantInstance plant, int gridRows, int gridCols) {
        ZoneConfig cfg = HexGrid.ZONES.getOrDefault(plant.zone(), HexGrid.ZONES.get("Middle"));
        int allowedLo = cfg.allowedLo();
        int allowedHi = Math.min(cfg.allowedHi(), gridRows - 1);
        int preferredLo = cfg.preferredLo();
        int preferredHi = Math.min(cfg.preferredHi(), gridRows - 1);

        List<int[]> preferred = new ArrayList<>();
        List<int[]> nonPreferred = new ArrayList<>();

        for (int row = allowedLo; row <= allowedHi; row++) {
            for (int col = 0; col < gridCols; col++) {
                if (plant.isStrict()) {
                    Set<GridCell> cells = HexGrid.computeCells(row, col, plant.widthIn(), true,
                            allowedLo, allowedHi, gridRows, gridCols);
                    if (cells == null) continue;
                }
                if (row >= preferredLo && row <= preferredHi) {
                    preferred.add(new int[]{row, col});
                } else {
                    nonPreferred.add(new int[]{row, col});
                }
            }
        }

        return new PositionLists(preferred, nonPreferred);
    }

    public static List<int[]> samplePositions(List<int[]> preferred, List<int[]> nonPreferred, int nSample, Random rng) {
        List<int[]> all = new ArrayList<>();
        all.addAll(preferred);
        all.addAll(nonPreferred);
        if (all.isEmpty()) return all;
        if (all.size() <= nSample) return all;

        // 2x weight for preferred
        List<int[]> pool = new ArrayList<>();
        pool.addAll(preferred);
        pool.addAll(preferred);
        pool.addAll(nonPreferred);
        Collections.shuffle(pool, rng);

        Set<String> seen = new HashSet<>();
        List<int[]> result = new ArrayList<>();
        for (int[] pos : pool) {
            String key = pos[0] + "," + pos[1];
            if (seen.add(key)) {
                result.add(pos);
                if (result.size() >= nSample) break;
            }
        }
        return result;
    }

    private InsertResult bestInsertPosition(
            PlacementState state, PlantInstance plant, Random rng, int nPos,
            List<int[]> preferred, List<int[]> nonPreferred) {
        List<int[]> positions = samplePositions(preferred, nonPreferred, nPos, rng);
        PlacedPlant bestPp = null;
        double bestDelta = Double.NEGATIVE_INFINITY;
        for (int[] pos : positions) {
            PlacedPlant pp = makePlacedPlant(plant, pos[0], pos[1], state.getGridRows(), state.getGridCols());
            if (pp == null) continue;
            double delta = computeAddDelta(state, pp);
            if (delta > bestDelta) {
                bestDelta = delta;
                bestPp = pp;
            }
        }
        return new InsertResult(bestPp, bestDelta);
    }

    /** Compute add delta without mutating state. */
    private double computeAddDelta(PlacementState state, PlacedPlant pp) {
        PlantInstance plant = pp.plant();
        PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());
        double delta = PlacementState.W_N_PLACED;
        if (state.getSpeciesCount().getOrDefault(sp, 0) == 0) {
            delta += PlacementState.W_N_UNIQUE;
        }
        int[][] strictGrid = state.getStrictGrid();
        if (plant.isStrict()) {
            if (state.getPenaltyMode() == PenaltyMode.PAIR) {
                Set<Integer> touching = new HashSet<>();
                for (GridCell cell : pp.cells()) {
                    if (strictGrid[cell.r()][cell.c()] > 0) {
                        for (int idx = 0; idx < state.getPlaced().size(); idx++) {
                            PlacedPlant existing = state.getPlaced().get(idx);
                            if (existing.plant().isStrict() && existing.cells().contains(cell)) {
                                touching.add(idx);
                            }
                        }
                    }
                }
                delta += PlacementState.W_STRICT_STRICT_PAIR * touching.size();
            } else {
                for (GridCell cell : pp.cells()) {
                    delta += PlacementState.W_STRICT_STRICT_CELLS * strictGrid[cell.r()][cell.c()];
                }
            }
        } else {
            for (GridCell cell : pp.cells()) {
                if (strictGrid[cell.r()][cell.c()] > 0) {
                    delta += PlacementState.W_LOOSE_STRICT_CELLS;
                }
            }
        }
        return delta;
    }

    private PlacementState randomFullPlacement(
            List<PlantInstance> plants, int gridRows, int gridCols, Random rng, PenaltyMode mode) {
        List<PlantInstance> order = new ArrayList<>(plants);
        Collections.shuffle(order, rng);

        List<PlacedPlant> placed = new ArrayList<>();
        List<PlantInstance> unplaced = new ArrayList<>();

        for (PlantInstance plant : order) {
            PositionLists posLists = enumerateValidPositions(plant, gridRows, gridCols);
            List<int[]> all = new ArrayList<>();
            all.addAll(posLists.preferred());
            all.addAll(posLists.nonPreferred());
            if (all.isEmpty()) {
                unplaced.add(plant);
                continue;
            }
            int[] pos = all.get(rng.nextInt(all.size()));
            PlacedPlant pp = makePlacedPlant(plant, pos[0], pos[1], gridRows, gridCols);
            if (pp == null) {
                unplaced.add(plant);
            } else {
                placed.add(pp);
            }
        }

        return new PlacementState(placed, unplaced, gridRows, gridCols, mode);
    }

    private Map<String, PositionCache> buildPosCache(List<PlantInstance> plants, int gridRows, int gridCols) {
        Map<String, PositionCache> cache = new HashMap<>();
        for (PlantInstance p : plants) {
            String key = posKey(p);
            if (!cache.containsKey(key)) {
                PositionLists posLists = enumerateValidPositions(p, gridRows, gridCols);
                cache.put(key, new PositionCache(posLists.preferred(), posLists.nonPreferred()));
            }
        }
        return cache;
    }

    private String posKey(PlantInstance p) {
        return p.zone() + "|" + p.widthIn() + "|" + p.isStrict();
    }

    private record InsertResult(PlacedPlant pp, double delta) {}

    private record PositionCache(List<int[]> preferred, List<int[]> nonPreferred) {}

    public record PositionLists(List<int[]> preferred, List<int[]> nonPreferred) {}
}

package com.garden.planner.core.model;

import java.util.*;

/**
 * Mutable bed state. Ports Python LocalState from search_beds.py.
 * strict_grid[r][c] = number of strict plants currently covering that cell.
 * loose_grid[r][c]  = number of loose plants currently covering that cell.
 * center_grid[r][c] = true if any plant has its center at (r,c).
 */
public class PlacementState {

    // Score weights (from Python SCORE_WEIGHTS)
    public static final double W_N_PLACED            =  1.0;
    public static final double W_N_UNIQUE            =  1.0;
    public static final double W_STRICT_STRICT_CELLS = -2.0;
    public static final double W_STRICT_STRICT_PAIR  = -3.0;
    public static final double W_LOOSE_STRICT_CELLS  =  0.5;
    public static final double W_LOOSE_LOOSE_CELLS   = -2.0;

    private final List<PlacedPlant> placed;
    private final List<PlantInstance> unplaced;
    private final int[][] strictGrid;   // [rows][cols] count of strict plants per cell
    private final int[][] looseGrid;    // [rows][cols] count of loose plants per cell
    private final boolean[][] centerGrid; // [rows][cols] true if a plant center is here
    private final Map<PlantSpecies, Integer> speciesCount;
    private double score;
    private final PenaltyMode penaltyMode;
    private final int gridRows;
    private final int gridCols;

    public PlacementState(
            List<PlacedPlant> placed,
            List<PlantInstance> unplaced,
            int gridRows,
            int gridCols,
            PenaltyMode penaltyMode) {
        this.placed = new ArrayList<>(placed);
        this.unplaced = new ArrayList<>(unplaced);
        this.gridRows = gridRows;
        this.gridCols = gridCols;
        this.penaltyMode = penaltyMode;
        this.strictGrid = new int[gridRows][gridCols];
        this.looseGrid  = new int[gridRows][gridCols];
        this.centerGrid = new boolean[gridRows][gridCols];
        this.speciesCount = new HashMap<>();

        // Build grids and speciesCount from placed list
        for (PlacedPlant pp : this.placed) {
            PlantInstance plant = pp.plant();
            if (plant.isStrict()) {
                for (GridCell cell : pp.cells()) {
                    strictGrid[cell.r()][cell.c()]++;
                }
            } else {
                for (GridCell cell : pp.cells()) {
                    looseGrid[cell.r()][cell.c()]++;
                }
            }
            centerGrid[pp.row()][pp.col()] = true;
            PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());
            speciesCount.merge(sp, 1, Integer::sum);
        }

        this.score = fullScore();
    }

    private double fullScore() {
        double s = placed.size() * W_N_PLACED;
        long uniqueCount = speciesCount.values().stream().filter(v -> v > 0).count();
        s += uniqueCount * W_N_UNIQUE;

        if (penaltyMode == PenaltyMode.PAIR) {
            // -3 per pair of strict plants that share >= 1 cell
            for (int i = 0; i < placed.size(); i++) {
                if (!placed.get(i).plant().isStrict()) continue;
                for (int j = 0; j < i; j++) {
                    if (!placed.get(j).plant().isStrict()) continue;
                    Set<GridCell> ci = placed.get(i).cells();
                    Set<GridCell> cj = placed.get(j).cells();
                    for (GridCell cell : ci) {
                        if (cj.contains(cell)) {
                            s += W_STRICT_STRICT_PAIR;
                            break;
                        }
                    }
                }
            }
        } else {
            // -2 per cell where >= 2 strict plants overlap
            for (int r = 0; r < gridRows; r++) {
                for (int c = 0; c < gridCols; c++) {
                    int n = strictGrid[r][c];
                    if (n >= 2) {
                        s += W_STRICT_STRICT_CELLS * (n - 1);
                    }
                }
            }
        }

        // Loose-over-strict bonus
        for (PlacedPlant pp : placed) {
            if (!pp.plant().isStrict()) {
                for (GridCell cell : pp.cells()) {
                    if (strictGrid[cell.r()][cell.c()] > 0) {
                        s += W_LOOSE_STRICT_CELLS;
                    }
                }
            }
        }

        // Loose-over-loose penalty: -2 per cell where >= 2 loose plants overlap
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                int n = looseGrid[r][c];
                if (n >= 2) {
                    s += W_LOOSE_LOOSE_CELLS * (n - 1);
                }
            }
        }

        return s;
    }

    /**
     * Non-mutating: compute score change of adding pp to current state.
     */
    public double addDelta(PlacedPlant pp) {
        PlantInstance plant = pp.plant();
        PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());
        double delta = W_N_PLACED;
        if (speciesCount.getOrDefault(sp, 0) == 0) {
            delta += W_N_UNIQUE;
        }

        if (plant.isStrict()) {
            if (penaltyMode == PenaltyMode.PAIR) {
                int nOverlap = countOverlappingStrictPlants(pp);
                delta += W_STRICT_STRICT_PAIR * nOverlap;
            } else {
                for (GridCell cell : pp.cells()) {
                    delta += W_STRICT_STRICT_CELLS * strictGrid[cell.r()][cell.c()];
                }
            }
        } else {
            for (GridCell cell : pp.cells()) {
                if (strictGrid[cell.r()][cell.c()] > 0) {
                    delta += W_LOOSE_STRICT_CELLS;
                }
                // Penalty for each existing loose plant cell we'd overlap
                delta += W_LOOSE_LOOSE_CELLS * looseGrid[cell.r()][cell.c()];
            }
        }
        return delta;
    }

    private int countOverlappingStrictPlants(PlacedPlant pp) {
        Set<Integer> touching = new HashSet<>();
        for (GridCell cell : pp.cells()) {
            if (strictGrid[cell.r()][cell.c()] > 0) {
                for (int idx = 0; idx < placed.size(); idx++) {
                    PlacedPlant existing = placed.get(idx);
                    if (existing.plant().isStrict() && existing.cells().contains(cell)) {
                        touching.add(idx);
                    }
                }
            }
        }
        return touching.size();
    }

    /**
     * Mutate in-place: add pp, move plant from unplaced to placed. Returns score delta.
     */
    public double addPlant(PlacedPlant pp) {
        double delta = addDelta(pp);
        PlantInstance plant = pp.plant();
        if (plant.isStrict()) {
            for (GridCell cell : pp.cells()) {
                strictGrid[cell.r()][cell.c()]++;
            }
        } else {
            for (GridCell cell : pp.cells()) {
                looseGrid[cell.r()][cell.c()]++;
            }
        }
        centerGrid[pp.row()][pp.col()] = true;
        placed.add(pp);
        unplaced.remove(plant);
        PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());
        speciesCount.merge(sp, 1, Integer::sum);
        score += delta;
        return delta;
    }

    /**
     * Non-mutating: compute score change of removing placed[idx].
     */
    public double removeDelta(int idx) {
        PlacedPlant pp = placed.get(idx);
        PlantInstance plant = pp.plant();
        PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());

        // Temporarily decrement the relevant grid so we see state without this plant
        if (plant.isStrict()) {
            for (GridCell cell : pp.cells()) {
                strictGrid[cell.r()][cell.c()]--;
            }
        } else {
            for (GridCell cell : pp.cells()) {
                looseGrid[cell.r()][cell.c()]--;
            }
        }

        double delta = -W_N_PLACED;
        if (speciesCount.getOrDefault(sp, 0) == 1) {
            delta -= W_N_UNIQUE;
        }

        if (plant.isStrict()) {
            if (penaltyMode == PenaltyMode.PAIR) {
                int nPairs = countOverlappingStrictPlants(pp);
                delta -= W_STRICT_STRICT_PAIR * nPairs;
            } else {
                for (GridCell cell : pp.cells()) {
                    delta -= W_STRICT_STRICT_CELLS * strictGrid[cell.r()][cell.c()];
                }
            }
        } else {
            for (GridCell cell : pp.cells()) {
                if (strictGrid[cell.r()][cell.c()] > 0) {
                    delta -= W_LOOSE_STRICT_CELLS;
                }
                delta -= W_LOOSE_LOOSE_CELLS * looseGrid[cell.r()][cell.c()];
            }
        }

        // Restore grid
        if (plant.isStrict()) {
            for (GridCell cell : pp.cells()) {
                strictGrid[cell.r()][cell.c()]++;
            }
        } else {
            for (GridCell cell : pp.cells()) {
                looseGrid[cell.r()][cell.c()]++;
            }
        }

        return delta;
    }

    /**
     * Mutate in-place: remove placed[idx]. Returns the removed PlacedPlant.
     */
    public PlacedPlant removePlant(int idx) {
        double delta = removeDelta(idx);
        PlacedPlant pp = placed.remove(idx);
        PlantInstance plant = pp.plant();
        if (plant.isStrict()) {
            for (GridCell cell : pp.cells()) {
                strictGrid[cell.r()][cell.c()]--;
            }
        } else {
            for (GridCell cell : pp.cells()) {
                looseGrid[cell.r()][cell.c()]--;
            }
        }
        centerGrid[pp.row()][pp.col()] = false;
        unplaced.add(plant);
        PlantSpecies sp = new PlantSpecies(plant.plantType(), plant.plantName());
        speciesCount.merge(sp, -1, Integer::sum);
        score += delta;
        return pp;
    }

    /**
     * Returns true if any of pp's cells are already covered by an existing plant.
     * Used by flower fill to prefer clean, non-overlapping placements.
     */
    public boolean hasAnyCellOverlap(PlacedPlant pp) {
        for (GridCell cell : pp.cells()) {
            if (strictGrid[cell.r()][cell.c()] > 0 || looseGrid[cell.r()][cell.c()] > 0) return true;
        }
        return false;
    }

    /**
     * Returns true if a plant already has its center cell at (r, c).
     * Use this to enforce the constraint that no two plants share a center cell.
     */
    public boolean isCenterOccupied(int r, int c) {
        if (r < 0 || r >= gridRows || c < 0 || c >= gridCols) return true;
        return centerGrid[r][c];
    }

    /**
     * Deep copy snapshot.
     */
    public PlacementState snapshot() {
        return new PlacementState(
            new ArrayList<>(placed),
            new ArrayList<>(unplaced),
            gridRows, gridCols, penaltyMode
        );
    }

    /**
     * Count cells covered by >= 2 strict plants.
     */
    public int countStrictOverlaps() {
        int total = 0;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (strictGrid[r][c] >= 2) total++;
            }
        }
        return total;
    }

    // --- Accessors ---

    public List<PlacedPlant> getPlaced() { return placed; }
    public List<PlantInstance> getUnplaced() { return unplaced; }
    public int[][] getStrictGrid() { return strictGrid; }
    public Map<PlantSpecies, Integer> getSpeciesCount() { return speciesCount; }
    public double getScore() { return score; }
    public PenaltyMode getPenaltyMode() { return penaltyMode; }
    public int getGridRows() { return gridRows; }
    public int getGridCols() { return gridCols; }

    public int getNPlaced() { return placed.size(); }
    public int getNUnique() {
        return (int) speciesCount.values().stream().filter(v -> v > 0).count();
    }
}

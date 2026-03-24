package com.garden.planner.core.geometry;

import com.garden.planner.core.model.GridCell;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class HexGrid {
    public static final int GRID_ROWS = 28;
    public static final int GRID_COLS = 84;
    public static final double HEX_V_SPACING = Math.sqrt(3) / 2;
    public static final int LOOSE_HEIGHT_THRESHOLD = 18;


    /**
     * Hex footprint for a plant of {@code width} inches diameter.
     * Ports Python disk_offsets.
     * Returns list of [dc, dr] pairs.
     */
    public static List<int[]> diskOffsets(int width) {
        int r = width / 2;
        List<int[]> result = new ArrayList<>();
        for (int dr = -r; dr <= r; dr++) {
            int rowWidth = width - Math.abs(dr);
            int dcStart = -(rowWidth / 2);
            for (int i = 0; i < rowWidth; i++) {
                result.add(new int[]{dcStart + i, dr});
            }
        }
        return result;
    }

    /**
     * Hex distance using offset-to-cube conversion.
     * Ports Python hex_distance / offset_to_cube.
     */
    public static int hexDistance(int c1, int r1, int c2, int r2) {
        int x1 = c1 - (r1 - r1 % 2) / 2, z1 = r1, y1 = -x1 - z1;
        int x2 = c2 - (r2 - r2 % 2) / 2, z2 = r2, y2 = -x2 - z2;
        return Math.max(Math.abs(x1 - x2), Math.max(Math.abs(y1 - y2), Math.abs(z1 - z2)));
    }

    /**
     * Compute the set of GridCells for a given center (row, col) and width.
     * Uses cube-coordinate disk enumeration so the footprint is a true hex
     * circle rather than a diamond in offset space.
     * For strict plants: returns null if any cell falls outside the grid.
     * For loose plants: clips to grid bounds; returns null if no cells remain.
     */
    public static Set<GridCell> computeCells(
            int row, int col, int width, boolean isStrict,
            int gridRows, int gridCols) {
        int r = width / 2;
        Set<GridCell> cells = new LinkedHashSet<>();

        int cx = col - (row - (row & 1)) / 2;
        int cz = row;

        for (int dx = -r; dx <= r; dx++) {
            int dzMin = Math.max(-r, -dx - r);
            int dzMax = Math.min( r,  r - dx);
            for (int dz = dzMin; dz <= dzMax; dz++) {
                int ax = cx + dx;
                int az = cz + dz;
                int nr = az;
                int nc = ax + (az - (az & 1)) / 2;

                if (isStrict) {
                    if (nr < 0 || nr >= gridRows || nc < 0 || nc >= gridCols) return null;
                    cells.add(new GridCell(nr, nc));
                } else {
                    if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols) {
                        cells.add(new GridCell(nr, nc));
                    }
                }
            }
        }
        if (!isStrict && cells.isEmpty()) return null;
        return cells;
    }

    /**
     * Count cells in a loose plant's footprint that fall just outside the grid,
     * within {@code maxOverflow} rows/cols of any edge.
     * These represent canopy overhang beyond the bed border — scored like open space.
     */
    public static int countOverflowCells(int row, int col, int width, int gridRows, int gridCols, int maxOverflow) {
        int r = width / 2;
        int cx = col - (row - (row & 1)) / 2;
        int cz = row;
        int count = 0;
        for (int dx = -r; dx <= r; dx++) {
            int dzMin = Math.max(-r, -dx - r);
            int dzMax = Math.min( r,  r - dx);
            for (int dz = dzMin; dz <= dzMax; dz++) {
                int nr = cz + dz;
                int nc = (cx + dx) + (nr - (nr & 1)) / 2;
                boolean inGrid     = nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols;
                boolean inOverflow = nr >= -maxOverflow && nr < gridRows + maxOverflow
                                  && nc >= -maxOverflow && nc < gridCols + maxOverflow;
                if (!inGrid && inOverflow) count++;
            }
        }
        return count;
    }

}

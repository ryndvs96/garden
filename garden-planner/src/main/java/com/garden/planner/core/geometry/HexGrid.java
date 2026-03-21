package com.garden.planner.core.geometry;

import com.garden.planner.core.model.GridCell;

import java.util.*;

public class HexGrid {
    public static final int GRID_ROWS = 28;
    public static final int GRID_COLS = 84;
    public static final double HEX_V_SPACING = Math.sqrt(3) / 2;
    public static final int LOOSE_HEIGHT_THRESHOLD = 18;

    /** Zone definitions: Back=(0,8,0,18), Middle=(9,18,0,27), Front=(19,27,9,27) */
    public static final Map<String, ZoneConfig> ZONES;

    static {
        Map<String, ZoneConfig> z = new LinkedHashMap<>();
        z.put("Back",   new ZoneConfig(0,  8,  0, 18));
        z.put("Middle", new ZoneConfig(9,  18, 0, 27));
        z.put("Front",  new ZoneConfig(19, 27, 9, 27));
        // "Any" spans the full bed — no zone restriction on placement.
        z.put("Any",    new ZoneConfig(0, GRID_ROWS - 1, 0, GRID_ROWS - 1));
        ZONES = Collections.unmodifiableMap(z);
    }

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
     * For strict plants: all cells must be in bounds and within zone.
     * Returns null if any cell is out of bounds or out of zone (strict).
     */
    public static Set<GridCell> computeCells(
            int row, int col, int width, boolean isStrict,
            int allowedLo, int allowedHi,
            int gridRows, int gridCols) {
        int r = width / 2;
        Set<GridCell> cells = new LinkedHashSet<>();

        // Convert center to cube coordinates (odd-r offset: odd rows shifted right).
        // cube_x = col - (row - (row & 1)) / 2,  cube_z = row
        int cx = col - (row - (row & 1)) / 2;
        int cz = row;

        // Enumerate all hexes within cube distance r.
        // Constraints: |dx| ≤ r, |dz| ≤ r, |dx+dz| ≤ r
        for (int dx = -r; dx <= r; dx++) {
            int dzMin = Math.max(-r, -dx - r);
            int dzMax = Math.min( r,  r - dx);
            for (int dz = dzMin; dz <= dzMax; dz++) {
                int ax = cx + dx;
                int az = cz + dz;
                // Convert cube back to odd-r offset:
                // offset_row = cube_z,  offset_col = cube_x + (cube_z - (cube_z & 1)) / 2
                int nr = az;
                int nc = ax + (az - (az & 1)) / 2;

                if (isStrict) {
                    if (nr < 0 || nr >= gridRows || nc < 0 || nc >= gridCols) return null;
                    if (nr < allowedLo || nr > allowedHi) return null;
                    cells.add(new GridCell(nr, nc));
                } else {
                    if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols
                            && nr >= allowedLo && nr <= allowedHi) {
                        cells.add(new GridCell(nr, nc));
                    }
                }
            }
        }
        if (!isStrict && cells.isEmpty()) return null;
        return cells;
    }

    /** Zone scan order rows for a given zone (preferred first). */
    public static List<Integer> zoneScanRows(String zone, int gridRows) {
        ZoneConfig cfg = ZONES.getOrDefault(zone, ZONES.get("Middle"));
        int allowedLo = cfg.allowedLo();
        int allowedHi = Math.min(cfg.allowedHi(), gridRows - 1);
        // scan_start = preferredLo (start of preferred range)
        int scanStart = Math.min(Math.max(cfg.preferredLo(), allowedLo), allowedHi);

        List<Integer> rows = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (int delta = 0; delta <= gridRows; delta++) {
            int[] candidates = delta == 0 ? new int[]{scanStart} : new int[]{scanStart + delta, scanStart - delta};
            for (int r : candidates) {
                if (!seen.contains(r) && r >= allowedLo && r <= allowedHi) {
                    rows.add(r);
                    seen.add(r);
                }
            }
        }
        return rows;
    }
}

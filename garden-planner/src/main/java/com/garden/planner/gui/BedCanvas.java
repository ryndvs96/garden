package com.garden.planner.gui;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.*;

/**
 * Canvas that draws the hex grid with placed plants.
 *
 * The visual canvas is 2 cells wider/taller on each side than the actual bed
 * (GHOST_PAD = 2). Plant footprints that overflow the bed boundary are rendered
 * in the ghost zone with fading transparency — 1 cell outside is semi-transparent,
 * 2 cells outside is nearly invisible. The bed boundary line is drawn on top.
 */
public class BedCanvas extends Canvas {

    private static final int GHOST_PAD = 2; // extra cells rendered outside the bed on each side

    private double cellSize = 12.0; // pixels per hex cell (mutable for zoom)

    private PlacementState state;
    private int selectedIdx = -1;
    private static final List<Color> PALETTE = List.of(
        Color.TOMATO, Color.CORNFLOWERBLUE, Color.MEDIUMSEAGREEN, Color.GOLDENROD,
        Color.MEDIUMPURPLE, Color.CHOCOLATE, Color.PALEVIOLETRED, Color.STEELBLUE,
        Color.OLIVEDRAB, Color.PERU, Color.MEDIUMORCHID, Color.CADETBLUE,
        Color.INDIANRED, Color.YELLOWGREEN, Color.SLATEBLUE, Color.CRIMSON
    );

    public BedCanvas(double width, double height) {
        super(width, height);
    }

    public void setState(PlacementState state) {
        this.state = state;
        selectedIdx = -1;
        if (state != null) {
            setWidth(computeWidth());
            setHeight(computeHeight());
        }
        redraw();
    }

    public int getSelectedIdx() { return selectedIdx; }
    public void setSelectedIdx(int idx) {
        selectedIdx = idx;
        redraw();
    }

    public void zoom(double factor) {
        cellSize = Math.max(6.0, Math.min(30.0, cellSize * factor));
        if (state != null) {
            setWidth(computeWidth());
            setHeight(computeHeight());
        }
        redraw();
    }

    private double computeWidth() {
        if (state == null) return 900;
        return (state.getGridCols() + 2 * GHOST_PAD + 1.5) * cellSize;
    }

    private double computeHeight() {
        if (state == null) return 300;
        return ((state.getGridRows() + 2 * GHOST_PAD) * HexGrid.HEX_V_SPACING + 1.5) * cellSize;
    }

    public void redraw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        gc.clearRect(0, 0, w, h);
        gc.setFill(Color.web("#f5f0e8"));
        gc.fillRect(0, 0, w, h);

        if (state == null) return;

        int gridRows = state.getGridRows();
        int gridCols = state.getGridCols();
        int[][] strictGrid = state.getStrictGrid();

        // Draw grid background cells inside the actual bed — skip cells covered by a plant
        Set<GridCell> plantCells = new HashSet<>();
        for (PlacedPlant pp : state.getPlaced()) plantCells.addAll(pp.cells());

        gc.setStroke(Color.web("#dedad0"));
        gc.setLineWidth(0.2);
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                if (plantCells.contains(new GridCell(r, c))) continue;
                double[] xy = cellToXY(r, c);
                drawHex(gc, xy[0], xy[1], cellSize * 0.48, false);
            }
        }

        // Draw placed plants in z-order:
        //   Pass 1 — loose, non-selected (semi-transparent, behind everything)
        //   Pass 2 — strict, non-selected (opaque, on top of loose)
        //   Pass 3 — selected plant (halo + fill, always on top)
        // Then ghost overflow, then bed boundary, then labels.
        List<PlacedPlant> placed = state.getPlaced();

        for (int pass = 1; pass <= 3; pass++) {
            for (int i = 0; i < placed.size(); i++) {
                boolean isSelected = (i == selectedIdx);
                if (pass == 1 && (isSelected || placed.get(i).plant().isStrict())) continue;
                if (pass == 2 && (isSelected || !placed.get(i).plant().isStrict())) continue;
                if (pass == 3 && !isSelected) continue;

                PlacedPlant pp = placed.get(i);
                Color color = getSpeciesColor(pp.plant().plantType() + "|" + pp.plant().plantName());

                for (GridCell cell : pp.cells()) {
                    double[] xy = cellToXY(cell.r(), cell.c());

                    if (isSelected) {
                        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 1.0));
                        drawFilledHex(gc, xy[0], xy[1], cellSize * 0.68);
                    }
                    if (pp.plant().isStrict()) {
                        boolean overlap = strictGrid[cell.r()][cell.c()] >= 2;
                        gc.setFill(overlap
                            ? color.deriveColor(0, 1.1, 0.55, 1.0)
                            : color.deriveColor(0, 1.0, 1.0, 1.0));
                    } else {
                        boolean overStrict = strictGrid[cell.r()][cell.c()] > 0;
                        gc.setFill(overStrict
                            ? color.deriveColor(0, 1.0, 0.62, 0.88)
                            : color.deriveColor(0, 1.0, 0.80, 0.72));
                    }
                    drawFilledHex(gc, xy[0], xy[1], cellSize * 0.58);
                }
            }
        }

        // Ghost overflow pass: render cells that extend outside the bed boundary,
        // fading as they go further out (1 cell out → semi-transparent, 2 → nearly gone).
        for (PlacedPlant pp : placed) {
            Color color = getSpeciesColor(pp.plant().plantType() + "|" + pp.plant().plantName());
            for (int[] ghost : ghostCells(pp, gridRows, gridCols)) {
                int nr = ghost[0], nc = ghost[1], dist = ghost[2];
                double alpha = dist == 1 ? 0.45 : 0.18;
                double[] xy = cellToXY(nr, nc);
                gc.setFill(pp.plant().isStrict()
                    ? color.deriveColor(0, 1.0, 1.0, alpha)
                    : color.deriveColor(0, 1.0, 0.80, 0.72 * alpha));
                drawFilledHex(gc, xy[0], xy[1], cellSize * 0.58);
            }
        }

        // Bed boundary drawn after all fills so it always shows over overflow plants
        drawBedBoundary(gc, gridRows, gridCols);

        // Labels (drawn over all fills)
        for (PlacedPlant pp : placed) {
            double[] center = cellToXY(pp.row(), pp.col());
            double availWidth = Math.sqrt(pp.cells().size()) * cellSize;
            double fontSize = Math.max(cellSize * 0.55, Math.min(cellSize * 0.9, availWidth / 5.0));
            int maxChars = (int)(availWidth / (fontSize * 0.58));

            String typeDisplay = pp.plant().plantType();
            int ci = typeDisplay.indexOf(',');
            if (ci >= 0) typeDisplay = typeDisplay.substring(ci + 1).trim();
            String nameDisplay = pp.plant().plantName();

            gc.setFont(Font.font(null, javafx.scene.text.FontWeight.BOLD, fontSize));
            gc.setTextAlign(TextAlignment.CENTER);

            if (maxChars < 3) {
                String init = (typeDisplay.isEmpty() ? "" : String.valueOf(typeDisplay.charAt(0)))
                            + (nameDisplay.isEmpty() ? "" : String.valueOf(nameDisplay.charAt(0)));
                drawLabelText(gc, init, center[0], center[1] + fontSize * 0.35);
            } else {
                String line1 = truncateLabel(typeDisplay, maxChars);
                String line2 = truncateLabel(nameDisplay, maxChars);
                double lineH = fontSize * 1.3;
                double startY = center[1] - lineH * 0.5 + fontSize * 0.35;
                drawLabelText(gc, line1, center[0], startY);
                drawLabelText(gc, line2, center[0], startY + lineH);
            }
        }

        gc.setLineDashes(null);
    }

    /**
     * Returns cells of pp's hex footprint that fall outside the grid but within
     * GHOST_PAD cells of the boundary. Each entry is {row, col, distOutside}.
     */
    private List<int[]> ghostCells(PlacedPlant pp, int gridRows, int gridCols) {
        int width = pp.plant().widthIn();
        int radius = width / 2;
        int row = pp.row(), col = pp.col();

        // Cube-coordinate centre (same formula as HexGrid.computeCells)
        int cx = col - (row - (row & 1)) / 2;
        int cz = row;

        List<int[]> result = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            int dzMin = Math.max(-radius, -dx - radius);
            int dzMax = Math.min( radius,  radius - dx);
            for (int dz = dzMin; dz <= dzMax; dz++) {
                int ax = cx + dx;
                int az = cz + dz;
                int nr = az;
                int nc = ax + (az - (az & 1)) / 2;

                // Skip cells already inside the grid (rendered in the main passes)
                if (nr >= 0 && nr < gridRows && nc >= 0 && nc < gridCols) continue;

                // How many cells outside the grid boundary?
                int rowOut = Math.max(0, Math.max(-nr, nr - (gridRows - 1)));
                int colOut = Math.max(0, Math.max(-nc, nc - (gridCols - 1)));
                int dist   = Math.max(rowOut, colOut);

                if (dist <= GHOST_PAD) {
                    result.add(new int[]{nr, nc, dist});
                }
            }
        }
        return result;
    }

    /** Nearest (row, col) for canvas coordinates (x, y). */
    public int[] xyToCell(double x, double y) {
        int row = (int) Math.round((y - cellSize) / (HexGrid.HEX_V_SPACING * cellSize)) - GHOST_PAD;
        row = Math.max(0, Math.min(state != null ? state.getGridRows() - 1 : 0, row));
        double xAdj = x - (1 + GHOST_PAD) * cellSize - (Math.floorMod(row, 2) == 1 ? cellSize * 0.5 : 0.0);
        int col = (int) Math.round(xAdj / cellSize);
        col = Math.max(0, Math.min(state != null ? state.getGridCols() - 1 : 0, col));
        return new int[]{row, col};
    }

    /** Draw the bed boundary at the actual grid extents (offset inward from the ghost zone). */
    private void drawBedBoundary(GraphicsContext gc, int gridRows, int gridCols) {
        double x0 = (GHOST_PAD + 0.5) * cellSize;
        double y0 = (GHOST_PAD * HexGrid.HEX_V_SPACING + 0.5) * cellSize;
        double x1 = (gridCols + GHOST_PAD + 1.0) * cellSize;
        double y1 = ((gridRows + GHOST_PAD) * HexGrid.HEX_V_SPACING + 1.0) * cellSize;
        gc.setStroke(Color.web("#555544"));
        gc.setLineWidth(2.0);
        gc.setLineDashes(null);
        gc.strokeRect(x0, y0, x1 - x0, y1 - y0);
    }

    /** Convert (row, col) hex offset coords to canvas (x, y), offset by GHOST_PAD. */
    private double[] cellToXY(int row, int col) {
        double x = (col + GHOST_PAD + (Math.floorMod(row, 2) == 1 ? 0.5 : 0.0)) * cellSize + cellSize;
        double y = (row + GHOST_PAD) * HexGrid.HEX_V_SPACING * cellSize + cellSize;
        return new double[]{x, y};
    }

    private void drawFilledHex(GraphicsContext gc, double cx, double cy, double radius) {
        double[] xs = new double[6], ys = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            xs[i] = cx + radius * Math.cos(angle);
            ys[i] = cy + radius * Math.sin(angle);
        }
        gc.fillPolygon(xs, ys, 6);
    }

    private void drawHex(GraphicsContext gc, double cx, double cy, double radius, boolean fill) {
        double[] xs = new double[6], ys = new double[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            xs[i] = cx + radius * Math.cos(angle);
            ys[i] = cy + radius * Math.sin(angle);
        }
        if (fill) gc.fillPolygon(xs, ys, 6);
        gc.strokePolygon(xs, ys, 6);
    }

    private void drawLabelText(GraphicsContext gc, String text, double x, double y) {
        gc.setFill(Color.color(0, 0, 0, 0.45));
        gc.fillText(text, x + 1.2, y + 1.2);
        gc.setFill(Color.WHITE);
        gc.fillText(text, x, y);
    }

    private static String truncateLabel(String s, int maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, Math.max(1, maxChars - 1)) + "\u2026";
    }

    private Color getSpeciesColor(String key) {
        int idx = (key.hashCode() & Integer.MAX_VALUE) % PALETTE.size();
        return PALETTE.get(idx);
    }

    /** Find placed plant index at canvas position (x, y). Returns -1 if none. */
    public int hitTest(double x, double y) {
        if (state == null) return -1;
        List<PlacedPlant> placed = state.getPlaced();
        for (int i = placed.size() - 1; i >= 0; i--) {
            for (GridCell cell : placed.get(i).cells()) {
                double[] xy = cellToXY(cell.r(), cell.c());
                double dist = Math.sqrt(Math.pow(x - xy[0], 2) + Math.pow(y - xy[1], 2));
                if (dist < cellSize * 0.6) return i;
            }
        }
        return -1;
    }

    public double getCellSize() { return cellSize; }

    /** Returns the canvas (x, y) of the center cell of placed plant at idx, or null. */
    public double[] getPlantCenterXY(int idx) {
        if (state == null || idx < 0 || idx >= state.getPlaced().size()) return null;
        PlacedPlant pp = state.getPlaced().get(idx);
        return cellToXY(pp.row(), pp.col());
    }
}

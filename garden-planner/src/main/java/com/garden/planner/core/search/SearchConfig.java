package com.garden.planner.core.search;

import com.garden.planner.core.geometry.HexGrid;
import com.garden.planner.core.model.PenaltyMode;
import com.garden.planner.core.model.PlacedPlant;

import java.util.List;

public final class SearchConfig {

    private final int nStarts;
    private final int nIters;
    private final PenaltyMode penaltyMode;
    private final long timeoutMs;
    private final long baseSeed;
    private final int nPositions;
    private final int gridRows;
    private final int gridCols;
    private final List<PlacedPlant> fixedPlants;
    private final List<PlacedPlant> warmStart;

    private SearchConfig(Builder b) {
        this.nStarts     = b.nStarts;
        this.nIters      = b.nIters;
        this.penaltyMode = b.penaltyMode;
        this.timeoutMs   = b.timeoutMs;
        this.baseSeed    = b.baseSeed;
        this.nPositions  = b.nPositions;
        this.gridRows    = b.gridRows;
        this.gridCols    = b.gridCols;
        this.fixedPlants = List.copyOf(b.fixedPlants);
        this.warmStart   = List.copyOf(b.warmStart);
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public int             nStarts()     { return nStarts; }
    public int             nIters()      { return nIters; }
    public PenaltyMode     penaltyMode() { return penaltyMode; }
    public long            timeoutMs()   { return timeoutMs; }
    public long            baseSeed()    { return baseSeed; }
    public int             nPositions()  { return nPositions; }
    public int             gridRows()    { return gridRows; }
    public int             gridCols()    { return gridCols; }
    public List<PlacedPlant> fixedPlants() { return fixedPlants; }
    public List<PlacedPlant> warmStart()   { return warmStart; }

    // ── Derived copies ─────────────────────────────────────────────────────────

    /** Return a copy with the timeout extended by the given number of milliseconds. */
    public SearchConfig withExtraTimeMs(long extraMs) {
        return toBuilder().timeoutMs(timeoutMs + extraMs).build();
    }

    /** Return a builder pre-populated with this config's values, for making modified copies. */
    public Builder toBuilder() {
        return new Builder()
                .nStarts(nStarts)
                .nIters(nIters)
                .penaltyMode(penaltyMode)
                .timeoutMs(timeoutMs)
                .baseSeed(baseSeed)
                .nPositions(nPositions)
                .gridRows(gridRows)
                .gridCols(gridCols)
                .fixedPlants(fixedPlants)
                .warmStart(warmStart);
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    /** Builder pre-populated with sensible defaults, ready to call .build() or override fields. */
    public static Builder defaults() {
        return new Builder()
                .nStarts(30)
                .nIters(400)
                .penaltyMode(PenaltyMode.CELL)
                .timeoutMs(30_000L)
                .baseSeed(42L)
                .nPositions(150)
                .gridRows(HexGrid.GRID_ROWS)
                .gridCols(HexGrid.GRID_COLS)
                .fixedPlants(List.of())
                .warmStart(List.of());
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {

        private int nStarts;
        private int nIters;
        private PenaltyMode penaltyMode;
        private long timeoutMs;
        private long baseSeed;
        private int nPositions;
        private int gridRows;
        private int gridCols;
        private List<PlacedPlant> fixedPlants = List.of();
        private List<PlacedPlant> warmStart   = List.of();

        private Builder() {}

        public Builder nStarts(int v)               { this.nStarts = v;     return this; }
        public Builder nIters(int v)                { this.nIters = v;      return this; }
        public Builder penaltyMode(PenaltyMode v)   { this.penaltyMode = v; return this; }
        public Builder timeoutMs(long v)            { this.timeoutMs = v;   return this; }
        public Builder baseSeed(long v)             { this.baseSeed = v;    return this; }
        public Builder nPositions(int v)            { this.nPositions = v;  return this; }
        public Builder gridRows(int v)              { this.gridRows = v;    return this; }
        public Builder gridCols(int v)              { this.gridCols = v;    return this; }
        public Builder fixedPlants(List<PlacedPlant> v) { this.fixedPlants = v; return this; }
        public Builder warmStart(List<PlacedPlant> v)   { this.warmStart = v;   return this; }

        public SearchConfig build() {
            return new SearchConfig(this);
        }
    }
}

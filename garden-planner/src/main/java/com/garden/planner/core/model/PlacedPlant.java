package com.garden.planner.core.model;

import java.util.Set;

public final class PlacedPlant {

    private final PlantInstance plant;
    private final int row;
    private final int col;
    private final Set<GridCell> cells;
    private final boolean locked;

    private PlacedPlant(Builder b) {
        this.plant  = b.plant;
        this.row    = b.row;
        this.col    = b.col;
        this.cells  = Set.copyOf(b.cells);
        this.locked = b.locked;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public PlantInstance  plant()  { return plant; }
    public int            row()    { return row; }
    public int            col()    { return col; }
    public Set<GridCell>  cells()  { return cells; }
    public boolean        locked() { return locked; }

    // ── Convenience copy ───────────────────────────────────────────────────────

    public PlacedPlant withLocked(boolean locked) {
        return toBuilder().locked(locked).build();
    }

    public Builder toBuilder() {
        return new Builder()
                .plant(plant)
                .row(row)
                .col(col)
                .cells(cells)
                .locked(locked);
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {

        private PlantInstance plant;
        private int row;
        private int col;
        private Set<GridCell> cells;
        private boolean locked;

        private Builder() {}

        public Builder plant(PlantInstance v)  { this.plant = v;  return this; }
        public Builder row(int v)              { this.row = v;    return this; }
        public Builder col(int v)              { this.col = v;    return this; }
        public Builder cells(Set<GridCell> v)  { this.cells = v;  return this; }
        public Builder locked(boolean v)       { this.locked = v; return this; }

        public PlacedPlant build() {
            return new PlacedPlant(this);
        }
    }
}

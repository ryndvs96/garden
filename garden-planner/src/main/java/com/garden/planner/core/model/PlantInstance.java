package com.garden.planner.core.model;

public final class PlantInstance {

    private final String zone;
    private final String plantType;
    private final String plantName;
    private final int widthIn;
    private final int heightIn;
    private final boolean isStrict;
    private final int instanceIdx;
    private final String code;

    private PlantInstance(Builder b) {
        this.zone        = b.zone;
        this.plantType   = b.plantType;
        this.plantName   = b.plantName;
        this.widthIn     = b.widthIn;
        this.heightIn    = b.heightIn;
        this.isStrict    = b.isStrict;
        this.instanceIdx = b.instanceIdx;
        this.code        = b.code;
    }

    // ── Accessors ──────────────────────────────────────────────────────────────

    public String  zone()        { return zone; }
    public String  plantType()   { return plantType; }
    public String  plantName()   { return plantName; }
    public int     widthIn()     { return widthIn; }
    public int     heightIn()    { return heightIn; }
    public boolean isStrict()    { return isStrict; }
    public int     instanceIdx() { return instanceIdx; }
    public String  code()        { return code; }

    // ── Derived copy ───────────────────────────────────────────────────────────

    public Builder toBuilder() {
        return new Builder()
                .zone(zone)
                .plantType(plantType)
                .plantName(plantName)
                .widthIn(widthIn)
                .heightIn(heightIn)
                .isStrict(isStrict)
                .instanceIdx(instanceIdx)
                .code(code);
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {

        private String zone;
        private String plantType;
        private String plantName;
        private int widthIn;
        private int heightIn;
        private boolean isStrict;
        private int instanceIdx;
        private String code;

        private Builder() {}

        public Builder zone(String v)        { this.zone = v;        return this; }
        public Builder plantType(String v)   { this.plantType = v;   return this; }
        public Builder plantName(String v)   { this.plantName = v;   return this; }
        public Builder widthIn(int v)        { this.widthIn = v;     return this; }
        public Builder heightIn(int v)       { this.heightIn = v;    return this; }
        public Builder isStrict(boolean v)   { this.isStrict = v;    return this; }
        public Builder instanceIdx(int v)    { this.instanceIdx = v; return this; }
        public Builder code(String v)        { this.code = v;        return this; }

        public PlantInstance build() {
            return new PlantInstance(this);
        }
    }
}

package com.garden.planner.core.model;

public record PlantInstance(
    String zone,
    String plantType,
    String plantName,
    int widthIn,
    int heightIn,
    boolean isStrict,
    int instanceIdx,
    String code
) {}

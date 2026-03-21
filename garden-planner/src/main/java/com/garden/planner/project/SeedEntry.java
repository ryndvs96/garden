package com.garden.planner.project;

public record SeedEntry(
        String id,
        String zone,
        String plantType,
        String plantName,
        int widthIn,
        int heightIn,
        boolean isStrict,
        String notes) {}

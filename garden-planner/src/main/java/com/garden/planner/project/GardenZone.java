package com.garden.planner.project;

import java.util.ArrayList;
import java.util.List;

public record GardenZone(String id, String name, List<BedConfig> beds) {

    /** Convenience constructor — starts with an empty mutable bed list. */
    public GardenZone(String id, String name) {
        this(id, name, new ArrayList<>());
    }
}

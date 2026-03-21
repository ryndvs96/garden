package com.garden.planner.core.search;

import com.garden.planner.core.model.PlacementState;

public record SearchResult(PlacementState state, SearchMetrics metrics) {}

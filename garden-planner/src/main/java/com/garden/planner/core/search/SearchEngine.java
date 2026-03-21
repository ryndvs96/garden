package com.garden.planner.core.search;

import com.garden.planner.core.model.PlantInstance;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public interface SearchEngine {
    SearchResult search(
        List<PlantInstance> plants,
        SearchConfig config,
        SearchMetrics metrics,
        AtomicBoolean cancelled
    );
}

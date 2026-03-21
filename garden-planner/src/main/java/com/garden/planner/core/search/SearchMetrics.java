package com.garden.planner.core.search;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SearchMetrics {
    private final AtomicLong statesEvaluated = new AtomicLong();
    private final AtomicReference<Double> bestScore = new AtomicReference<>(Double.NEGATIVE_INFINITY);
    private final long startNanos = System.nanoTime();

    public void recordState() { statesEvaluated.incrementAndGet(); }

    public void updateBest(double score) {
        bestScore.getAndUpdate(prev -> Math.max(prev, score));
    }

    public long getStatesEvaluated() { return statesEvaluated.get(); }

    public double getBestScore() { return bestScore.get(); }

    public double statesPerSecond() {
        double elapsed = (System.nanoTime() - startNanos) / 1e9;
        return elapsed > 0 ? statesEvaluated.get() / elapsed : 0;
    }

    public double elapsedSeconds() {
        return (System.nanoTime() - startNanos) / 1e9;
    }
}

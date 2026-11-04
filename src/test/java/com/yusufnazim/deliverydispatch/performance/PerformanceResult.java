package com.yusufnazim.deliverydispatch.performance;

import java.util.Arrays;

record PerformanceResult(String workload, int concurrency, int requests, double elapsedSeconds,
                         double requestsPerSecond, double meanMs, double p50Ms,
                         double p95Ms, double p99Ms, double maxMs) {

    static PerformanceResult from(String workload, int concurrency, long[] latencyNanos, long elapsedNanos) {
        if (concurrency < 1 || latencyNanos.length == 0 || elapsedNanos <= 0
                || Arrays.stream(latencyNanos).anyMatch(value -> value <= 0)) {
            throw new IllegalArgumentException("Measurements must be positive and nonempty");
        }
        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        double seconds = elapsedNanos / 1_000_000_000.0;
        return new PerformanceResult(workload, concurrency, sorted.length, seconds, sorted.length / seconds,
                Arrays.stream(sorted).average().orElseThrow() / 1_000_000.0,
                percentileMs(sorted, 0.50), percentileMs(sorted, 0.95), percentileMs(sorted, 0.99),
                sorted[sorted.length - 1] / 1_000_000.0);
    }

    private static double percentileMs(long[] sorted, double percentile) {
        return sorted[(int) Math.ceil(percentile * sorted.length) - 1] / 1_000_000.0;
    }
}

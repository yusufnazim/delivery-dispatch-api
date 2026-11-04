package com.yusufnazim.deliverydispatch.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class PerformanceResultTest {

    @Test
    void percentilesUseNearestRankAndThroughputUsesWallTime() {
        long[] samples = LongStream.iterate(100, n -> n - 1).limit(100).map(n -> n * 1_000_000).toArray();
        PerformanceResult result = PerformanceResult.from("read", 4, samples, 2_000_000_000);

        assertThat(result.requests()).isEqualTo(100);
        assertThat(result.requestsPerSecond()).isEqualTo(50);
        assertThat(result.meanMs()).isEqualTo(50.5);
        assertThat(result.p50Ms()).isEqualTo(50);
        assertThat(result.p95Ms()).isEqualTo(95);
        assertThat(result.p99Ms()).isEqualTo(99);
        assertThat(result.maxMs()).isEqualTo(100);
        assertThat(samples[0]).isEqualTo(100_000_000);
    }

    @Test
    void aSingleSampleHasTheSameValueAtEveryPercentile() {
        PerformanceResult result = PerformanceResult.from("dispatch", 1, new long[] {500_000}, 1_000_000);
        assertThat(result.p50Ms()).isEqualTo(0.5);
        assertThat(result.p95Ms()).isEqualTo(0.5);
        assertThat(result.p99Ms()).isEqualTo(0.5);
        assertThat(result.requestsPerSecond()).isEqualTo(1000);
    }

    @Test
    void incompleteOrInvalidMeasurementsCannotProduceAReport() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                PerformanceResult.from("read", 4, new long[0], 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PerformanceResult.from("read", 4, new long[] {1, 0}, 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PerformanceResult.from("read", 4, new long[] {1}, 0));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PerformanceResult.from("read", 0, new long[] {1}, 1));
    }
}

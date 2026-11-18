package com.sfaasproject.stf.measurement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sfaasproject.stf.measurement.service.MeasurementTrendQuery.Interval;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class MeasurementTrendServiceTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 18, 9, 7, 30);

    @Test
    void bucketStartsFromRangeBaseAndGrowsUntilAtMost2000Points() {
        // 24시간 이하에서 원본이 너무 많으면 1분부터.
        assertThat(MeasurementTrendService.bucketSeconds(Duration.ofHours(24).getSeconds())).isEqualTo(60);
        // 7일 이하는 1분이 기본이지만 10080칸이라 10분까지 올린다.
        assertThat(MeasurementTrendService.bucketSeconds(Duration.ofDays(7).getSeconds())).isEqualTo(600);
        assertThat(MeasurementTrendService.bucketSeconds(Duration.ofDays(1).plusHours(1).getSeconds())).isEqualTo(60);
        // 7일 넘게는 1시간이 기본.
        assertThat(MeasurementTrendService.bucketSeconds(Duration.ofDays(30).getSeconds())).isEqualTo(3600);
        assertThat(MeasurementTrendService.bucketSeconds(Duration.ofDays(365).getSeconds())).isEqualTo(21600);
        for (long days : new long[] {1, 2, 7, 8, 30, 90, 365, 1000, 3000}) {
            long range = Duration.ofDays(days).getSeconds();
            assertThat(Math.ceilDiv(range, MeasurementTrendService.bucketSeconds(range))).isLessThanOrEqualTo(2000);
        }
    }

    @Test
    void anchorAlignsBucketsToKstMidnight() {
        assertThat(MeasurementTrendService.anchor(FROM, 60)).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 7));
        assertThat(MeasurementTrendService.anchor(FROM, 600)).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
        assertThat(MeasurementTrendService.anchor(FROM, 3600)).isEqualTo(LocalDateTime.of(2026, 11, 18, 9, 0));
        assertThat(MeasurementTrendService.anchor(FROM, 86400)).isEqualTo(LocalDateTime.of(2026, 11, 18, 0, 0));
    }

    @Test
    void disconnectionsAreClippedMergedAndOngoingStaysOpen() {
        LocalDateTime to = FROM.plusHours(2);
        List<Interval> merged = MeasurementTrendService.merge(List.of(
                new Interval(FROM.minusMinutes(10), FROM.plusMinutes(5)),
                new Interval(FROM.plusMinutes(3), FROM.plusMinutes(8)),
                new Interval(FROM.plusMinutes(30), FROM.plusMinutes(40)),
                new Interval(FROM.plusMinutes(90), null)), FROM, to);
        assertThat(merged).containsExactly(
                new Interval(FROM, FROM.plusMinutes(8)),
                new Interval(FROM.plusMinutes(30), FROM.plusMinutes(40)),
                new Interval(FROM.plusMinutes(90), null));
        assertThat(MeasurementTrendService.merge(List.of(new Interval(FROM, to.plusHours(1))), FROM, to))
                .containsExactly(new Interval(FROM, to));
    }
}

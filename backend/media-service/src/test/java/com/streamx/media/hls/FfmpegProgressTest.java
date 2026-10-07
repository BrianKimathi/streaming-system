package com.streamx.media.hls;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FfmpegProgressTest {

    @Test
    void parsesOutTimeInMicroseconds() {
        assertEquals(OptionalLong.of(12_500_000), FfmpegProgress.outTimeMicros("out_time_us=12500000"));
        assertEquals(OptionalLong.of(12_500_000), FfmpegProgress.outTimeMicros("out_time_ms=12500000"));
        assertEquals(OptionalLong.empty(), FfmpegProgress.outTimeMicros("out_time=00:00:12.500000"));
        assertEquals(OptionalLong.empty(), FfmpegProgress.outTimeMicros("out_time_us=N/A"));
        assertEquals(OptionalLong.empty(), FfmpegProgress.outTimeMicros("out_time_us=-5"));
        assertEquals(OptionalLong.empty(), FfmpegProgress.outTimeMicros("progress=continue"));
        assertEquals(OptionalLong.empty(), FfmpegProgress.outTimeMicros(null));
    }

    @Test
    void percentIsRelativeToProbedDurationAndCappedBelowCompletion() {
        assertEquals(0, FfmpegProgress.percent(0, 100));
        assertEquals(25, FfmpegProgress.percent(25_000_000, 100));
        assertEquals(33, FfmpegProgress.percent(33_999_999, 100));
        assertEquals(99, FfmpegProgress.percent(100_000_000, 100));
        assertEquals(99, FfmpegProgress.percent(500_000_000, 100));
        assertEquals(0, FfmpegProgress.percent(5_000_000, 0));
    }
}

package com.streamx.media.hls;

import java.util.OptionalLong;

/** Parses the key=value stream ffmpeg writes with {@code -progress}. */
final class FfmpegProgress {

    private FfmpegProgress() {
    }

    /** {@code out_time_us} and the misnamed {@code out_time_ms} both carry microseconds. */
    static OptionalLong outTimeMicros(String line) {
        if (line == null) {
            return OptionalLong.empty();
        }
        String value;
        if (line.startsWith("out_time_us=")) {
            value = line.substring("out_time_us=".length());
        } else if (line.startsWith("out_time_ms=")) {
            value = line.substring("out_time_ms=".length());
        } else {
            return OptionalLong.empty();
        }
        try {
            long micros = Long.parseLong(value.trim());
            return micros < 0 ? OptionalLong.empty() : OptionalLong.of(micros);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** 0..99; 100 is reserved for "uploaded and COMPLETED". */
    static int percent(long outTimeMicros, double durationSeconds) {
        if (durationSeconds <= 0) {
            return 0;
        }
        double ratio = outTimeMicros / 1_000_000.0 / durationSeconds;
        return (int) Math.max(0, Math.min(99, Math.floor(ratio * 100)));
    }
}

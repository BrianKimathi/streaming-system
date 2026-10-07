package com.streamx.trending.repository;

import java.util.UUID;

/** Windowed event counts for one title, aggregated from {@code trending_events}. */
public record TitleActivity(UUID titleId, Long views1h, Long views6h, Long completions24h) {
}

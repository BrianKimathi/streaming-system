package com.streamx.trending.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrendingDecayJob {

    private static final Logger log = LoggerFactory.getLogger(TrendingDecayJob.class);

    private final TrendingService trendingService;

    public TrendingDecayJob(TrendingService trendingService) {
        this.trendingService = trendingService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void recompute() {
        try {
            int removed = trendingService.recomputeAll();
            trendingService.refreshCatalogMetadata();
            log.debug("Trending recompute finished; {} inactive item(s) removed", removed);
        } catch (RuntimeException e) {
            log.error("Trending recompute failed", e);
        }
    }
}

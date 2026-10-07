package com.streamx.watchhistory.service;

import com.streamx.watchhistory.repository.WatchProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Gives rows written before titleId/titleType existed a title, so per-title queries see them. */
@Component
public class WatchProgressBackfill {

    private static final Logger log = LoggerFactory.getLogger(WatchProgressBackfill.class);

    private final WatchProgressRepository repository;

    public WatchProgressBackfill(WatchProgressRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillTitles() {
        int series = repository.backfillSeriesTitles();
        int movies = repository.backfillMovieTitles();
        if (series + movies > 0) {
            log.info("Backfilled titleId/titleType on {} watch progress rows ({} series, {} movies)",
                    series + movies, series, movies);
        }
    }
}

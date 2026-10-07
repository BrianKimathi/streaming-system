package com.streamx.catalog.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Hibernate's schema update never widens existing columns, so artwork/trailer URL columns created as varchar(255)
 * are widened to match the entities' length of 2000.
 */
@Component
public class MediaUrlColumnsMigration {

    private static final Logger log = LoggerFactory.getLogger(MediaUrlColumnsMigration.class);
    private static final List<String[]> COLUMNS = List.of(
            new String[]{"movies", "poster_url"},
            new String[]{"movies", "backdrop_url"},
            new String[]{"movies", "trailer_url"},
            new String[]{"tv_shows", "poster_url"},
            new String[]{"tv_shows", "backdrop_url"},
            new String[]{"tv_shows", "trailer_url"},
            new String[]{"seasons", "poster_url"},
            new String[]{"episodes", "thumbnail_url"}
    );

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public MediaUrlColumnsMigration(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void widenUrlColumns() {
        if (!isPostgres()) {
            return;
        }
        for (String[] column : COLUMNS) {
            Integer length = jdbcTemplate.query(
                    "select character_maximum_length from information_schema.columns "
                            + "where table_schema = current_schema() and table_name = ? and column_name = ?",
                    rs -> rs.next() ? (Integer) rs.getObject(1) : null,
                    column[0], column[1]);
            if (length != null && length < 2000) {
                jdbcTemplate.execute("alter table " + column[0] + " alter column " + column[1] + " type varchar(2000)");
                log.info("Widened {}.{} from varchar({}) to varchar(2000)", column[0], column[1], length);
            }
        }
    }

    private boolean isPostgres() {
        try (Connection connection = dataSource.getConnection()) {
            return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            log.warn("Could not determine database type; skipping URL column migration", e);
            return false;
        }
    }
}

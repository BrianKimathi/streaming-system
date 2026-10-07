package com.streamx.catalog.repository;

import com.streamx.catalog.domain.ContentStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.UUID;

/**
 * Filters shared by {@code Movie} and {@code TvShow}; both expose {@code status}, {@code title} and {@code genres}.
 */
public final class CatalogSpecifications {

    private CatalogSpecifications() {
    }

    public static <T> Specification<T> hasStatus(ContentStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static <T> Specification<T> titleContains(String search) {
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
    }

    /** Inner join on a single genre id yields at most one row per title, so no DISTINCT is needed. */
    public static <T> Specification<T> hasGenre(UUID genreId) {
        return (root, query, cb) -> cb.equal(root.join("genres").get("id"), genreId);
    }
}

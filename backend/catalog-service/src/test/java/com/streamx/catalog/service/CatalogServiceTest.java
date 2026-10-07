package com.streamx.catalog.service;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogServiceTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    void testCreateGenreAndMovie() {
        CreateGenreRequest genreReq = new CreateGenreRequest();
        genreReq.setName("Sci-Fi");
        GenreResponse genreRes = catalogService.createGenre(genreReq);

        assertNotNull(genreRes.getId());
        assertEquals("Sci-Fi", genreRes.getName());
        assertEquals("sci-fi", genreRes.getSlug());

        CreateMovieRequest movieReq = new CreateMovieRequest();
        movieReq.setTitle("Interstellar");
        movieReq.setSynopsis("A team of explorers travel through a wormhole in space.");
        movieReq.setReleaseDate(LocalDate.of(2014, 11, 7));
        movieReq.setRuntimeMinutes(169);
        movieReq.setMaturityRating("PG-13");
        movieReq.setStatus(ContentStatus.PUBLISHED);
        movieReq.setGenreIds(Collections.singleton(genreRes.getId()));

        MovieResponse movieRes = catalogService.createMovie(movieReq);

        assertNotNull(movieRes.getId());
        assertEquals("Interstellar", movieRes.getTitle());
        assertEquals(1, movieRes.getGenres().size());

        Page<MovieResponse> movies = catalogService.getMovies(ContentStatus.PUBLISHED, "Interstellar", PageRequest.of(0, 10));
        assertEquals(1, movies.getTotalElements());
    }
}

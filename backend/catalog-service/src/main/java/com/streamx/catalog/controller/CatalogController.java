package com.streamx.catalog.controller;

import com.streamx.catalog.dto.*;
import com.streamx.catalog.service.CatalogService;
import com.streamx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public catalog. Every read only ever exposes PUBLISHED content; admins use {@code /catalog/admin/**}.
 * The non-GET endpoints here are restricted to admins by the gateway.
 */
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // --- Genres ---

    @PostMapping("/genres")
    public ResponseEntity<ApiResponse<GenreResponse>> createGenre(@Valid @RequestBody CreateGenreRequest request) {
        GenreResponse response = catalogService.createGenre(request);
        return ResponseEntity.ok(ApiResponse.success("Genre created successfully", response));
    }

    @GetMapping("/genres")
    public ResponseEntity<ApiResponse<List<GenreResponse>>> getGenres() {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getAllGenres()));
    }

    // --- Movies ---

    @PostMapping("/movies")
    public ResponseEntity<ApiResponse<MovieResponse>> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        MovieResponse response = catalogService.createMovie(request);
        return ResponseEntity.ok(ApiResponse.success("Movie created successfully", response));
    }

    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<Page<MovieResponse>>> getMovies(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "genreId", required = false) String genreId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublishedMovies(search, genreId, pageable)));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublishedMovie(id)));
    }

    // --- TV shows ---

    @GetMapping("/tv-shows")
    public ResponseEntity<ApiResponse<Page<TvShowResponse>>> getTvShows(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "genreId", required = false) String genreId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublishedTvShows(search, genreId, pageable)));
    }

    @GetMapping("/tv-shows/{id}")
    public ResponseEntity<ApiResponse<TvShowDetailResponse>> getTvShow(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getPublishedTvShowDetail(id)));
    }

    // --- Lookup ---

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<CatalogLookupResponse>> lookup(@RequestParam("ids") List<String> ids) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.lookup(ids)));
    }
}

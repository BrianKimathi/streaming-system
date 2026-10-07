package com.streamx.catalog.controller;

import com.streamx.catalog.domain.ContentStatus;
import com.streamx.catalog.dto.*;
import com.streamx.catalog.service.CatalogService;
import com.streamx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        List<GenreResponse> response = catalogService.getAllGenres();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // --- Movies ---
    @PostMapping("/movies")
    public ResponseEntity<ApiResponse<MovieResponse>> createMovie(@Valid @RequestBody CreateMovieRequest request) {
        MovieResponse response = catalogService.createMovie(request);
        return ResponseEntity.ok(ApiResponse.success("Movie created successfully", response));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(@PathVariable("id") String id) {
        MovieResponse response = catalogService.getMovieById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<Page<MovieResponse>>> getMovies(
            @RequestParam(value = "status", required = false) ContentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<MovieResponse> response = catalogService.getMovies(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

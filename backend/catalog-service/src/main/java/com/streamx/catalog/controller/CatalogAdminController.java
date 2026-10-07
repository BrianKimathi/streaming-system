package com.streamx.catalog.controller;

import com.streamx.catalog.dto.*;
import com.streamx.catalog.service.CatalogAdminService;
import com.streamx.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/catalog/admin")
public class CatalogAdminController {

    private final CatalogAdminService catalogAdminService;

    public CatalogAdminController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    @GetMapping("/movies")
    public ResponseEntity<ApiResponse<List<MovieResponse>>> listMovies() {
        return ResponseEntity.ok(ApiResponse.success(catalogAdminService.listAllMovies()));
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<ApiResponse<MovieResponse>> getMovie(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogAdminService.getMovie(id)));
    }

    @PutMapping("/movies/{id}")
    public ResponseEntity<ApiResponse<MovieResponse>> updateMovie(
            @PathVariable("id") String id, @Valid @RequestBody CreateMovieRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Movie updated", catalogAdminService.updateMovie(id, request)));
    }

    @DeleteMapping("/movies/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMovie(@PathVariable("id") String id) {
        catalogAdminService.deleteMovie(id);
        return ResponseEntity.ok(ApiResponse.success("Movie deleted", null));
    }

    @PatchMapping("/movies/{id}/status")
    public ResponseEntity<ApiResponse<MovieResponse>> updateMovieStatus(
            @PathVariable("id") String id, @Valid @RequestBody UpdateContentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Movie status updated",
                catalogAdminService.updateMovieStatus(id, request.status())));
    }

    @GetMapping("/tv-shows")
    public ResponseEntity<ApiResponse<List<TvShowResponse>>> listTvShows() {
        return ResponseEntity.ok(ApiResponse.success(catalogAdminService.listAllTvShows()));
    }

    @GetMapping("/tv-shows/{id}")
    public ResponseEntity<ApiResponse<TvShowDetailResponse>> getTvShow(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogAdminService.getTvShowDetail(id)));
    }

    @PostMapping("/tv-shows")
    public ResponseEntity<ApiResponse<TvShowResponse>> createTvShow(@Valid @RequestBody CreateTvShowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("TV show created", catalogAdminService.createTvShow(request)));
    }

    @PutMapping("/tv-shows/{id}")
    public ResponseEntity<ApiResponse<TvShowResponse>> updateTvShow(
            @PathVariable("id") String id, @Valid @RequestBody CreateTvShowRequest request) {
        return ResponseEntity.ok(ApiResponse.success("TV show updated", catalogAdminService.updateTvShow(id, request)));
    }

    @DeleteMapping("/tv-shows/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTvShow(@PathVariable("id") String id) {
        catalogAdminService.deleteTvShow(id);
        return ResponseEntity.ok(ApiResponse.success("TV show deleted", null));
    }

    @PatchMapping("/tv-shows/{id}/status")
    public ResponseEntity<ApiResponse<TvShowResponse>> updateTvShowStatus(
            @PathVariable("id") String id, @Valid @RequestBody UpdateContentStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("TV show status updated",
                catalogAdminService.updateTvShowStatus(id, request.status())));
    }

    // --- Seasons ---

    @PostMapping("/tv-shows/{showId}/seasons")
    public ResponseEntity<ApiResponse<SeasonResponse>> createSeason(
            @PathVariable("showId") String showId, @Valid @RequestBody SeasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Season created",
                catalogAdminService.createSeason(showId, request)));
    }

    @PutMapping("/seasons/{seasonId}")
    public ResponseEntity<ApiResponse<SeasonResponse>> updateSeason(
            @PathVariable("seasonId") String seasonId, @Valid @RequestBody SeasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Season updated",
                catalogAdminService.updateSeason(seasonId, request)));
    }

    @DeleteMapping("/seasons/{seasonId}")
    public ResponseEntity<ApiResponse<Void>> deleteSeason(@PathVariable("seasonId") String seasonId) {
        catalogAdminService.deleteSeason(seasonId);
        return ResponseEntity.ok(ApiResponse.success("Season deleted", null));
    }

    // --- Episodes ---

    @PostMapping("/seasons/{seasonId}/episodes")
    public ResponseEntity<ApiResponse<EpisodeResponse>> createEpisode(
            @PathVariable("seasonId") String seasonId, @Valid @RequestBody EpisodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Episode created",
                catalogAdminService.createEpisode(seasonId, request)));
    }

    @PutMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<EpisodeResponse>> updateEpisode(
            @PathVariable("episodeId") String episodeId, @Valid @RequestBody EpisodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Episode updated",
                catalogAdminService.updateEpisode(episodeId, request)));
    }

    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<ApiResponse<Void>> deleteEpisode(@PathVariable("episodeId") String episodeId) {
        catalogAdminService.deleteEpisode(episodeId);
        return ResponseEntity.ok(ApiResponse.success("Episode deleted", null));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(catalogAdminService.getStats()));
    }
}

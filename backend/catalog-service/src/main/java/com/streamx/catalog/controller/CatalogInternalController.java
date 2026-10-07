package com.streamx.catalog.controller;

import com.streamx.catalog.dto.InternalTitleResponse;
import com.streamx.catalog.service.CatalogService;
import com.streamx.common.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints; the gateway returns 404 for {@code /catalog/internal/**}.
 */
@RestController
@RequestMapping("/api/v1/catalog/internal")
public class CatalogInternalController {

    private final CatalogService catalogService;

    public CatalogInternalController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/titles/{id}")
    public ResponseEntity<ApiResponse<InternalTitleResponse>> getTitle(@PathVariable("id") String id) {
        return ResponseEntity.ok(ApiResponse.success(catalogService.getInternalTitle(id)));
    }
}

package com.streamx.catalog.service;

import com.streamx.common.exception.BadRequestException;

import java.util.UUID;

final class CatalogIds {

    private CatalogIds() {
    }

    static UUID parse(String id) {
        if (id == null || id.isBlank()) {
            throw new BadRequestException("ID is required");
        }
        try {
            return UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ID: " + id);
        }
    }
}

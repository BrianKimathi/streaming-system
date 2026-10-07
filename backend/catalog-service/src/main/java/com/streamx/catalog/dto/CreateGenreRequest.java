package com.streamx.catalog.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateGenreRequest {
    @NotBlank(message = "Genre name is required")
    private String name;

    public CreateGenreRequest() {
    }

    public CreateGenreRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

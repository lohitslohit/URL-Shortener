package com.example.urlshorten.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateShortUrlRequest(
        @NotBlank(message = "originalUrl is required")
        String originalUrl
) {
}

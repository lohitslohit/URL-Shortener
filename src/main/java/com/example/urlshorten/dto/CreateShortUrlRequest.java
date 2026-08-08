package com.example.urlshorten.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateShortUrlRequest(
        @NotBlank(message = "originalUrl is required")
        @Pattern(regexp = "https?://.+", message = "originalUrl must start with http:// or https://")
        String originalUrl
) {
}

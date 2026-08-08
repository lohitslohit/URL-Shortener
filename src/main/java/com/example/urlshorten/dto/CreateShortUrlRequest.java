package com.example.urlshorten.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank(message = "originalUrl is required")
        @Pattern(regexp = "https?://.+", message = "originalUrl must start with http:// or https://")
        String originalUrl,

        @Size(min = 3, max = 32, message = "alias must be between 3 and 32 characters")
        @Pattern(regexp = "^[0-9a-zA-Z]+$", message = "alias must be base62 (0-9, a-z, A-Z)")
        String alias
) {
    public CreateShortUrlRequest {
        if (alias != null && alias.isBlank()) {
            alias = null;
        }
    }

    public CreateShortUrlRequest(String originalUrl) {
        this(originalUrl, null);
    }
}

package com.example.urlshorten.dto;

import java.time.Instant;

public record ShortUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        boolean reused,
        Instant createdAt
) {
}

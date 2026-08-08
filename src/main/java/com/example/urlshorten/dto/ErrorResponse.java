package com.example.urlshorten.dto;

import java.time.Instant;

public record ErrorResponse(
        String message,
        String path,
        Instant timestamp
) {
}

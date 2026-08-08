package com.example.urlshorten.dto;

public record ShortUrlResponse(
        String shortCode,
        String originalUrl
) {
}

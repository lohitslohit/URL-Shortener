package com.example.urlshorten.service;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.dto.UrlStatsResponse;

public interface UrlService {

    ShortUrlResponse createShortUrl(CreateShortUrlRequest request);

    String resolveOriginalUrl(String shortCode);

    UrlStatsResponse getStats(String shortCode);

    void disableByCode(String shortCode);
}

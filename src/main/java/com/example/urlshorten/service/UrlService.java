package com.example.urlshorten.service;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;

public interface UrlService {

    ShortUrlResponse createShortUrl(CreateShortUrlRequest request);

    String resolveOriginalUrl(String shortCode);

    void disableByCode(String shortCode);
}

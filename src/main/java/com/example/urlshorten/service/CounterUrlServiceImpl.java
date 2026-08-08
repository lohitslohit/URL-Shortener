package com.example.urlshorten.service;

import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "counter")
public class CounterUrlServiceImpl extends UrlServiceImpl {

    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    public CounterUrlServiceImpl(
            UrlRepository repository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        super(repository, baseUrl);
    }

    @Override
    protected ShortUrlResponse createNewMapping(String normalizedUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(normalizedUrl);
        // temporary placeholder satisfies NOT NULL + unique; replaced with base62(id) after DB assigns the ID
        mapping.setShortCode(UUID.randomUUID().toString().replace("-", ""));
        UrlMapping saved = repository.save(mapping);
        saved.setShortCode(base62Encode(saved.getId()));
        return toResponse(repository.save(saved), false);
    }

    // encodes a positive long into base62 using 0-9a-zA-Z alphabet
    static String base62Encode(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Counter value must be positive, got: " + value);
        }
        StringBuilder sb = new StringBuilder();
        long base = ALPHABET.length;
        while (value > 0) {
            sb.append(ALPHABET[(int) (value % base)]);
            value /= base;
        }
        return sb.reverse().toString();
    }
}

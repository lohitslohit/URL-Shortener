package com.example.urlshorten.service;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlServiceImpl implements UrlService {

    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final UrlRepository repository;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public UrlServiceImpl(UrlRepository repository, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public ShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
        String normalizedUrl = request.originalUrl().trim();

        return repository.findByOriginalUrlAndDisabledAtIsNull(normalizedUrl)
                .map(existing -> toResponse(existing, true))
                .orElseGet(() -> createNewMapping(normalizedUrl));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveOriginalUrl(String shortCode) {
        UrlMapping mapping = repository.findByShortCodeAndDisabledAtIsNull(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));
        return mapping.getOriginalUrl();
    }

    @Override
    @Transactional
    public void disableByCode(String shortCode) {
        UrlMapping mapping = repository.findByShortCodeAndDisabledAtIsNull(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));

        mapping.setDisabledAt(Instant.now());
        repository.save(mapping);
    }

    private ShortUrlResponse createNewMapping(String normalizedUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(normalizedUrl);
        mapping.setShortCode(generateUniqueShortCode());

        UrlMapping saved = repository.save(mapping);
        return toResponse(saved, false);
    }

    private String generateUniqueShortCode() {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = randomCode(CODE_LENGTH);
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code");
    }

    private String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    private ShortUrlResponse toResponse(UrlMapping mapping, boolean reused) {
        return new ShortUrlResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                reused,
                mapping.getCreatedAt()
        );
    }
}

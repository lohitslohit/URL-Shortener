package com.example.urlshorten.service;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "random", matchIfMissing = true)
public class UrlServiceImpl implements UrlService {

    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    protected final UrlRepository repository;
    protected final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public UrlServiceImpl(UrlRepository repository, @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.repository = repository;
        this.baseUrl = baseUrl;
    }

    @Override
    @Transactional
    public ShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
        String normalizedUrl = request.originalUrl().trim();
        String lookupUrl = normalizeForLookup(normalizedUrl);

        return repository.findByOriginalUrlAndDisabledAtIsNull(lookupUrl)
                .map(existing -> toResponse(existing, true))
            .orElseGet(() -> createNewMapping(lookupUrl));
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

    protected ShortUrlResponse createNewMapping(String normalizedUrl) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(normalizedUrl);
        mapping.setShortCode(generateShortCode(normalizedUrl));

        UrlMapping saved = repository.save(mapping);
        return toResponse(saved, false);
    }

    protected String generateShortCode(String normalizedUrl) {
        return generateUniqueShortCode();
    }

    protected String generateUniqueShortCode() {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String candidate = randomCode(CODE_LENGTH);
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique short code");
    }

    protected String randomCode(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    protected String normalizeForLookup(String normalizedUrl) {
        return normalizedUrl;
    }

    protected ShortUrlResponse toResponse(UrlMapping mapping, boolean reused) {
        return new ShortUrlResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                reused,
                mapping.getCreatedAt()
        );
    }
}

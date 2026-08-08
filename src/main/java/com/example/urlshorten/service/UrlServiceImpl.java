package com.example.urlshorten.service;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.dto.UrlStatsResponse;
import com.example.urlshorten.exception.ConflictException;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import com.example.urlshorten.util.Base62;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "random", matchIfMissing = true)
public class UrlServiceImpl implements UrlService {

    private static final int CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 20;
    private static final int MAX_CREATE_ATTEMPTS = 3;
    private static final Set<String> RESERVED_ALIASES = Set.of("api", "health", "urls", "shorten", "stats");

    protected final UrlRepository repository;
    protected final String baseUrl;
    private final SecureRandom random = new SecureRandom();
    private final TransactionTemplate transactionTemplate;

    public UrlServiceImpl(
            UrlRepository repository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.baseUrl = baseUrl;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public ShortUrlResponse createShortUrl(CreateShortUrlRequest request) {
        String lookupUrl = normalizeForLookup(request.originalUrl().trim());

        Optional<UrlMapping> existing = repository.findByActiveOriginalUrl(lookupUrl);
        if (existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        String alias = validateAndNormalizeAlias(request.alias());

        DataIntegrityViolationException lastConflict = null;
        for (int attempt = 0; attempt < MAX_CREATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> createNewMapping(lookupUrl, alias));
            } catch (DataIntegrityViolationException ex) {
                lastConflict = ex;
                Optional<UrlMapping> raced = repository.findByActiveOriginalUrl(lookupUrl);
                if (raced.isPresent()) {
                    return toResponse(raced.get(), true);
                }
                if (alias != null) {
                    throw new ConflictException("Alias already taken");
                }
            }
        }

        throw new IllegalStateException("Failed to create short URL due to concurrent conflicts", lastConflict);
    }

    @Override
    @Transactional
    public String resolveOriginalUrl(String shortCode) {
        UrlMapping mapping = repository.findByShortCodeAndDisabledAtIsNull(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));
        mapping.setClickCount(mapping.getClickCount() + 1);
        repository.save(mapping);
        return mapping.getOriginalUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public UrlStatsResponse getStats(String shortCode) {
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));
        return toStatsResponse(mapping);
    }

    @Override
    @Transactional
    public void disableByCode(String shortCode) {
        UrlMapping mapping = repository.findByShortCodeAndDisabledAtIsNull(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL not found"));

        mapping.setDisabledAt(Instant.now());
        mapping.setActiveOriginalUrl(null);
        repository.save(mapping);
    }

    protected ShortUrlResponse createNewMapping(String normalizedUrl, String alias) {
        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(normalizedUrl);
        mapping.setActiveOriginalUrl(normalizedUrl);
        mapping.setShortCode(alias != null ? alias : generateShortCode(normalizedUrl));

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
        char[] alphabet = Base62.ALPHABET.toCharArray();
        for (int i = 0; i < length; i++) {
            code.append(alphabet[random.nextInt(alphabet.length)]);
        }
        return code.toString();
    }

    protected String normalizeForLookup(String normalizedUrl) {
        return canonicalizeUrl(normalizedUrl);
    }

    protected String canonicalizeUrl(String url) {
        try {
            URI uri = new URI(url).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();

            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }

            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            URI canonical = new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), null);
            return canonical.toString();
        } catch (URISyntaxException ex) {
            return url;
        }
    }

    protected String validateAndNormalizeAlias(String alias) {
        if (alias == null) {
            return null;
        }
        if (!Base62.isValidCode(alias)) {
            throw new IllegalArgumentException("alias must be base62 (0-9, a-z, A-Z)");
        }
        if (alias.length() < 3 || alias.length() > 32) {
            throw new IllegalArgumentException("alias must be between 3 and 32 characters");
        }
        if (RESERVED_ALIASES.contains(alias.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("alias is reserved");
        }
        if (repository.existsByShortCode(alias)) {
            throw new ConflictException("Alias already taken");
        }
        return alias;
    }

    protected ShortUrlResponse toResponse(UrlMapping mapping, boolean reused) {
        return new ShortUrlResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                reused,
                mapping.getClickCount(),
                mapping.getCreatedAt()
        );
    }

    protected UrlStatsResponse toStatsResponse(UrlMapping mapping) {
        return new UrlStatsResponse(
                mapping.getShortCode(),
                baseUrl + "/" + mapping.getShortCode(),
                mapping.getOriginalUrl(),
                mapping.getClickCount(),
                mapping.getCreatedAt(),
                mapping.getDisabledAt()
        );
    }
}

package com.example.urlshorten.service;

import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service 
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "hash")
public class HashUrlServiceImpl extends UrlServiceImpl {

    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int DEFAULT_HASH_CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final int hashCodeLength;

    public HashUrlServiceImpl(
            UrlRepository repository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.short-code.hash-length:8}") int hashCodeLength
    ) {
        super(repository, baseUrl);
        this.hashCodeLength = hashCodeLength <= 0 ? DEFAULT_HASH_CODE_LENGTH : hashCodeLength;
    }

    @Override
    protected String normalizeForLookup(String normalizedUrl) {
        return canonicalizeUrl(normalizedUrl);
    }

    @Override
    protected String generateShortCode(String normalizedUrl) {
        return generateHashBasedShortCode(normalizedUrl);
    }

    private String generateHashBasedShortCode(String canonicalUrl) {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String material = i == 0 ? canonicalUrl : canonicalUrl + "#" + i;
            String candidate = base62Encode(hashBytes(material)).substring(0, hashCodeLength);
            Optional<UrlMapping> existing = repository.findByShortCodeAndDisabledAtIsNull(candidate);
            if (existing.isEmpty() || canonicalUrl.equals(existing.get().getOriginalUrl())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique hash-based short code");
    }

    private byte[] hashBytes(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm not available: " + HASH_ALGORITHM, e);
        }
    }

    private String base62Encode(byte[] input) {
        BigInteger value = new BigInteger(1, input);
        if (value.equals(BigInteger.ZERO)) {
            return "0".repeat(Math.max(hashCodeLength, 1));
        }

        StringBuilder encoded = new StringBuilder();
        BigInteger base = BigInteger.valueOf(ALPHABET.length);
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            encoded.append(ALPHABET[divRem[1].intValue()]);
            value = divRem[0];
        }

        while (encoded.length() < hashCodeLength) {
            encoded.append('0');
        }
        return encoded.reverse().toString();
    }

    private String canonicalizeUrl(String url) {
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
            // Request validation should prevent this; fallback keeps service resilient.
            return url;
        }
    }
}

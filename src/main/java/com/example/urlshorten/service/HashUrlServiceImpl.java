package com.example.urlshorten.service;

import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import com.example.urlshorten.util.Base62;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "hash")
public class HashUrlServiceImpl extends UrlServiceImpl {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int DEFAULT_HASH_CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final int hashCodeLength;

    public HashUrlServiceImpl(
            UrlRepository repository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            PlatformTransactionManager transactionManager,
            @Value("${app.short-code.hash-length:8}") int hashCodeLength
    ) {
        super(repository, baseUrl, transactionManager);
        this.hashCodeLength = hashCodeLength <= 0 ? DEFAULT_HASH_CODE_LENGTH : hashCodeLength;
    }

    @Override
    protected String generateShortCode(String normalizedUrl) {
        return generateHashBasedShortCode(normalizedUrl);
    }

    private String generateHashBasedShortCode(String canonicalUrl) {
        for (int i = 0; i < MAX_GENERATION_ATTEMPTS; i++) {
            String material = i == 0 ? canonicalUrl : canonicalUrl + "#" + i;
            String candidate = encodeAndTrim(hashBytes(material));
            Optional<UrlMapping> existing = repository.findByShortCodeAndDisabledAtIsNull(candidate);
            if (existing.isEmpty() || canonicalUrl.equals(existing.get().getOriginalUrl())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate unique hash-based short code");
    }

    private String encodeAndTrim(byte[] digest) {
        String encoded = Base62.encode(digest);
        if (encoded.length() < hashCodeLength) {
            encoded = "0".repeat(hashCodeLength - encoded.length()) + encoded;
        }
        return encoded.substring(0, hashCodeLength);
    }

    private byte[] hashBytes(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm not available: " + HASH_ALGORITHM, e);
        }
    }
}

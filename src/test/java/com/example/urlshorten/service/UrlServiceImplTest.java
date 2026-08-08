package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.exception.ConflictException;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    private static final String CANONICAL_EXAMPLE = "https://example.com/";
    private static final String CANONICAL_ORG = "https://example.org/";

    @Mock
    private UrlRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private UrlServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new UrlServiceImpl(repository, "http://localhost:8080", transactionManager);
    }

    @Test
    void createShortUrl_reusesExistingMapping() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("abc123");
        existing.setOriginalUrl(CANONICAL_EXAMPLE);

        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo("abc123");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void createShortUrl_ignoresAliasWhenReusingExistingMapping() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("abc123");
        existing.setOriginalUrl(CANONICAL_EXAMPLE);

        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(
                new CreateShortUrlRequest("https://example.com", "newAlias")
        );

        assertThat(response.shortCode()).isEqualTo("abc123");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
        verify(repository, never()).existsByShortCode(any());
    }

    @Test
    void createShortUrl_reusesExistingMappingAfterConcurrentConflict() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("abc123");
        existing.setOriginalUrl(CANONICAL_EXAMPLE);

        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(repository.existsByShortCode(any())).thenReturn(false);
        when(repository.save(any(UrlMapping.class)))
                .thenThrow(new DataIntegrityViolationException("active_original_url unique"));

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo("abc123");
        assertThat(response.reused()).isTrue();
    }

    @Test
    void createShortUrl_createsNewWhenMissing() {
        when(repository.findByActiveOriginalUrl(CANONICAL_ORG))
                .thenReturn(Optional.empty());
        when(repository.existsByShortCode(any())).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            mapping.setDisabledAt(null);
            return mapping;
        });

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.org"));

        assertThat(response.reused()).isFalse();
        assertThat(response.shortCode()).isNotBlank();
        assertThat(response.shortUrl()).contains(response.shortCode());
        assertThat(response.originalUrl()).isEqualTo(CANONICAL_ORG);
    }

    @Test
    void createShortUrl_usesCustomAlias() {
        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty());
        when(repository.existsByShortCode("myAlias")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.createShortUrl(
                new CreateShortUrlRequest("https://example.com", "myAlias")
        );

        assertThat(response.shortCode()).isEqualTo("myAlias");
        assertThat(response.reused()).isFalse();
    }

    @Test
    void createShortUrl_rejectsReservedAlias() {
        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.createShortUrl(new CreateShortUrlRequest("https://example.com", "health")));
    }

    @Test
    void createShortUrl_rejectsTakenAlias() {
        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty());
        when(repository.existsByShortCode("taken1")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createShortUrl(new CreateShortUrlRequest("https://example.com", "taken1")));
    }

    @Test
    void resolveOriginalUrl_returnsValueAndIncrementsClickCount() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc123");
        mapping.setOriginalUrl(CANONICAL_EXAMPLE);
        mapping.setClickCount(2);

        when(repository.findByShortCodeAndDisabledAtIsNull("abc123"))
                .thenReturn(Optional.of(mapping));
        when(repository.save(mapping)).thenReturn(mapping);

        String originalUrl = service.resolveOriginalUrl("abc123");

        assertThat(originalUrl).isEqualTo(CANONICAL_EXAMPLE);
        assertThat(mapping.getClickCount()).isEqualTo(3);
        verify(repository).save(mapping);
    }

    @Test
    void resolveOriginalUrl_throwsWhenMissing() {
        when(repository.findByShortCodeAndDisabledAtIsNull("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.resolveOriginalUrl("missing"));
    }

    @Test
    void getStats_returnsClickAnalytics() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc123");
        mapping.setOriginalUrl(CANONICAL_EXAMPLE);
        mapping.setClickCount(5);

        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(mapping));

        var stats = service.getStats("abc123");

        assertThat(stats.shortCode()).isEqualTo("abc123");
        assertThat(stats.clickCount()).isEqualTo(5);
        assertThat(stats.originalUrl()).isEqualTo(CANONICAL_EXAMPLE);
    }

    @Test
    void getStats_throwsWhenMissing() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getStats("missing"));
    }

    @Test
    void createShortUrl_rejectsReservedShortenAlias() {
        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.createShortUrl(new CreateShortUrlRequest("https://example.com", "shorten")));
    }

    @Test
    void disableByCode_setsDisabledAt() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc123");
        mapping.setOriginalUrl(CANONICAL_EXAMPLE);
        mapping.setActiveOriginalUrl(CANONICAL_EXAMPLE);

        when(repository.findByShortCodeAndDisabledAtIsNull("abc123"))
                .thenReturn(Optional.of(mapping));

        service.disableByCode("abc123");

        assertThat(mapping.getDisabledAt()).isNotNull();
        assertThat(mapping.getActiveOriginalUrl()).isNull();
        verify(repository).save(mapping);
    }

    @Test
    void disableByCode_throwsWhenMissing() {
        when(repository.findByShortCodeAndDisabledAtIsNull("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.disableByCode("missing"));
    }
}

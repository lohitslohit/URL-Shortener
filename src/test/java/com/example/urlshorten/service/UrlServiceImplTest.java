package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlServiceImplTest {

    @Mock
    private UrlRepository repository;

    private UrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UrlServiceImpl(repository, "http://localhost:8080");
    }

    @Test
    void createShortUrl_reusesExistingMapping() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("abc123");
        existing.setOriginalUrl("https://example.com");

        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://example.com"))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo("abc123");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void createShortUrl_createsNewWhenMissing() {
        UrlMapping saved = new UrlMapping();
        saved.setShortCode("def789X");
        saved.setOriginalUrl("https://example.org");
        saved.setDisabledAt(null);

        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://example.org"))
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
    }

    @Test
    void resolveOriginalUrl_returnsValue() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc123");
        mapping.setOriginalUrl("https://example.com");

        when(repository.findByShortCodeAndDisabledAtIsNull("abc123"))
                .thenReturn(Optional.of(mapping));

        String originalUrl = service.resolveOriginalUrl("abc123");

        assertThat(originalUrl).isEqualTo("https://example.com");
    }

    @Test
    void resolveOriginalUrl_throwsWhenMissing() {
        when(repository.findByShortCodeAndDisabledAtIsNull("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.resolveOriginalUrl("missing"));
    }

    @Test
    void disableByCode_setsDisabledAt() {
        UrlMapping mapping = new UrlMapping();
        mapping.setShortCode("abc123");
        mapping.setOriginalUrl("https://example.com");

        when(repository.findByShortCodeAndDisabledAtIsNull("abc123"))
                .thenReturn(Optional.of(mapping));

        service.disableByCode("abc123");

        assertThat(mapping.getDisabledAt()).isNotNull();
        verify(repository).save(mapping);
    }

    @Test
    void disableByCode_throwsWhenMissing() {
        when(repository.findByShortCodeAndDisabledAtIsNull("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.disableByCode("missing"));
    }
}

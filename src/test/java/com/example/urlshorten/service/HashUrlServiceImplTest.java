package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HashUrlServiceImplTest {

    @Mock
    private UrlRepository repository;

    private HashUrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HashUrlServiceImpl(repository, "http://localhost:8080", 8);
    }

    @Test
    void createShortUrl_canonicalizesAndReusesExisting() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("abcd1234");
        existing.setOriginalUrl("https://www.example.com/some/very/long/url");

        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://www.example.com/some/very/long/url"))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(
                new CreateShortUrlRequest("https://www.EXAMPLE.com:443/some/very/long/url/")
        );

        assertThat(response.shortCode()).isEqualTo("abcd1234");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void createShortUrl_createsDeterministicCodeForSameCanonicalUrl() {
        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://www.example.com/some/very/long/url"))
                .thenReturn(Optional.empty());
        when(repository.findByShortCodeAndDisabledAtIsNull(any()))
                .thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse first = service.createShortUrl(
                new CreateShortUrlRequest("https://www.EXAMPLE.com:443/some/very/long/url/")
        );
        ShortUrlResponse second = service.createShortUrl(
                new CreateShortUrlRequest("https://www.example.com/some/very/long/url")
        );

        assertThat(first.shortCode()).hasSize(8);
        assertThat(second.shortCode()).isEqualTo(first.shortCode());
    }
}

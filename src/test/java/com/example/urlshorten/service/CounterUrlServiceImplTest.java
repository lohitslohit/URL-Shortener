package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
class CounterUrlServiceImplTest {

    @Mock
    private UrlRepository repository;

    private CounterUrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CounterUrlServiceImpl(repository, "http://localhost:8080");
    }

    @Test
    void base62Encode_knownValues() {
        assertThat(CounterUrlServiceImpl.base62Encode(1)).isEqualTo("1");
        assertThat(CounterUrlServiceImpl.base62Encode(62)).isEqualTo("10");
        assertThat(CounterUrlServiceImpl.base62Encode(1_000_000_000L)).isEqualTo("15ftgG");
    }

    @Test
    void base62Encode_rejectsNonPositive() {
        assertThatThrownBy(() -> CounterUrlServiceImpl.base62Encode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CounterUrlServiceImpl.base62Encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createShortUrl_usesDbIdAsCounter() {
        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://example.com"))
                .thenReturn(Optional.empty());
        // first save: DB assigns ID 42
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            if (m.getId() == null) {
                setId(m, 42L);
            }
            return m;
        });

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo(CounterUrlServiceImpl.base62Encode(42L));
        assertThat(response.reused()).isFalse();
        // two saves: one to get the ID, one to persist the real short code
        verify(repository, times(2)).save(any(UrlMapping.class));
    }

    @Test
    void createShortUrl_reusesExistingMapping() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("15ftgG");
        existing.setOriginalUrl("https://example.com");

        when(repository.findByOriginalUrlAndDisabledAtIsNull("https://example.com"))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo("15ftgG");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
    }

    // UrlMapping.id has no public setter; set via reflection for test purposes
    private static void setId(UrlMapping mapping, Long id) {
        try {
            var field = UrlMapping.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(mapping, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

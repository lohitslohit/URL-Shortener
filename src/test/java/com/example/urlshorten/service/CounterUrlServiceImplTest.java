package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.urlshorten.dto.CreateShortUrlRequest;
import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import com.example.urlshorten.util.Base62;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class CounterUrlServiceImplTest {

    private static final String CANONICAL_EXAMPLE = "https://example.com/";

    @Mock
    private UrlRepository repository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private CounterUrlServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new CounterUrlServiceImpl(repository, "http://localhost:8080", transactionManager);
    }

    @Test
    void base62Encode_knownValues() {
        assertThat(Base62.encode(1)).isEqualTo("1");
        assertThat(Base62.encode(62)).isEqualTo("10");
        assertThat(Base62.encode(1_000_000_000L)).isEqualTo("15FTGg");
    }

    @Test
    void base62Encode_rejectsNonPositive() {
        assertThatThrownBy(() -> Base62.encode(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createShortUrl_usesDbIdAsCounter() {
        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.empty());
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> {
            UrlMapping m = inv.getArgument(0);
            if (m.getId() == null) {
                setId(m, 42L);
            }
            return m;
        });

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo(Base62.encode(42L));
        assertThat(response.reused()).isFalse();
        verify(repository, times(2)).save(any(UrlMapping.class));
    }

    @Test
    void createShortUrl_reusesExistingMapping() {
        UrlMapping existing = new UrlMapping();
        existing.setShortCode("15FTGg");
        existing.setOriginalUrl(CANONICAL_EXAMPLE);

        when(repository.findByActiveOriginalUrl(CANONICAL_EXAMPLE))
                .thenReturn(Optional.of(existing));

        ShortUrlResponse response = service.createShortUrl(new CreateShortUrlRequest("https://example.com"));

        assertThat(response.shortCode()).isEqualTo("15FTGg");
        assertThat(response.reused()).isTrue();
        verify(repository, never()).save(any());
    }

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

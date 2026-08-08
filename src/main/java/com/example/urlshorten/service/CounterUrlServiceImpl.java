package com.example.urlshorten.service;

import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.model.UrlMapping;
import com.example.urlshorten.repository.UrlRepository;
import com.example.urlshorten.util.Base62;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
@ConditionalOnProperty(name = "app.short-code.strategy", havingValue = "counter")
public class CounterUrlServiceImpl extends UrlServiceImpl {

    public CounterUrlServiceImpl(
            UrlRepository repository,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            PlatformTransactionManager transactionManager
    ) {
        super(repository, baseUrl, transactionManager);
    }

    @Override
    protected ShortUrlResponse createNewMapping(String normalizedUrl, String alias) {
        if (alias != null) {
            return super.createNewMapping(normalizedUrl, alias);
        }

        UrlMapping mapping = new UrlMapping();
        mapping.setOriginalUrl(normalizedUrl);
        mapping.setActiveOriginalUrl(normalizedUrl);
        // temporary placeholder satisfies NOT NULL + unique; replaced with base62(id) after DB assigns the ID
        mapping.setShortCode(UUID.randomUUID().toString().replace("-", ""));
        UrlMapping saved = repository.save(mapping);
        saved.setShortCode(Base62.encode(saved.getId()));
        return toResponse(repository.save(saved), false);
    }
}

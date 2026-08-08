package com.example.urlshorten.repository;

import com.example.urlshorten.model.UrlMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {

	Optional<UrlMapping> findByShortCodeAndDisabledAtIsNull(String shortCode);

	Optional<UrlMapping> findByShortCode(String shortCode);

	Optional<UrlMapping> findByActiveOriginalUrl(String activeOriginalUrl);

	boolean existsByShortCode(String shortCode);
}

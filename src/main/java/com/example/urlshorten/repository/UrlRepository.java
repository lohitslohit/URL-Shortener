package com.example.urlshorten.repository;

import com.example.urlshorten.model.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlRepository extends JpaRepository<UrlMapping, Long> {
}

package com.example.urlshorten.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class UrlServiceImplTest {

    private UrlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UrlServiceImpl();
    }

    @Test
    void health_returnsOk() {
        assertThat(service.health()).isEqualTo("ok");
    }
}

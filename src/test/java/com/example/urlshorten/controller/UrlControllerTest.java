package com.example.urlshorten.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshorten.dto.ShortUrlResponse;
import com.example.urlshorten.dto.UrlStatsResponse;
import com.example.urlshorten.exception.ConflictException;
import com.example.urlshorten.exception.ResourceNotFoundException;
import com.example.urlshorten.service.UrlService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    void createShortUrl_returnsCreated() throws Exception {
        ShortUrlResponse response = new ShortUrlResponse(
                "abc123",
                "http://localhost:8080/abc123",
                "https://example.com/",
                false,
                0L,
                Instant.now()
        );

        when(urlService.createShortUrl(any())).thenReturn(response);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("abc123"));
    }

    @Test
    void createShortUrl_returnsOkWhenReused() throws Exception {
        ShortUrlResponse response = new ShortUrlResponse(
                "abc123",
                "http://localhost:8080/abc123",
                "https://example.com/",
                true,
                3L,
                Instant.now()
        );

        when(urlService.createShortUrl(any())).thenReturn(response);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reused").value(true));
    }

    @Test
    void createShortUrl_returnsBadRequestForInvalidUrl() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("originalUrl must start with http:// or https://"));
    }

    @Test
    void createShortUrl_returnsBadRequestForInvalidAlias() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"alias\":\"bad alias\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("alias must be base62 (0-9, a-z, A-Z)"));
    }

    @Test
    void createShortUrl_returnsBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void createShortUrl_returnsConflictWhenAliasTaken() throws Exception {
        when(urlService.createShortUrl(any())).thenThrow(new ConflictException("Alias already taken"));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalUrl\":\"https://example.com\",\"alias\":\"taken1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Alias already taken"));
    }

    @Test
    void redirect_returnsMovedPermanentlyLocationHeader() throws Exception {
        when(urlService.resolveOriginalUrl("abc123")).thenReturn("https://example.com");

        mockMvc.perform(get("/abc123"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://example.com"));
    }

    @Test
    void stats_returnsClickAnalytics() throws Exception {
        when(urlService.getStats("abc123")).thenReturn(new UrlStatsResponse(
                "abc123",
                "http://localhost:8080/abc123",
                "https://example.com/",
                7L,
                Instant.parse("2026-01-01T00:00:00Z"),
                null
        ));

        mockMvc.perform(get("/stats/abc123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value("abc123"))
                .andExpect(jsonPath("$.clickCount").value(7));
    }

    @Test
    void disableShortUrl_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/urls/abc123"))
                .andExpect(status().isNoContent());
    }

    @Test
    void redirect_returnsNotFoundWhenMissing() throws Exception {
        when(urlService.resolveOriginalUrl("missing"))
                .thenThrow(new ResourceNotFoundException("Short URL not found"));

        mockMvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short URL not found"));
    }

    @Test
    void disableShortUrl_returnsNotFoundWhenMissing() throws Exception {
        doThrow(new ResourceNotFoundException("Short URL not found"))
                .when(urlService).disableByCode("missing");

        mockMvc.perform(delete("/api/urls/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Short URL not found"));
    }

    @Test
    void health_returnsOkPayload() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module").value("controller"))
                .andExpect(jsonPath("$.service").value("ok"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}

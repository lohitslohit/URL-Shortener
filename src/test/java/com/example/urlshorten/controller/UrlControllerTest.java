package com.example.urlshorten.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.urlshorten.service.UrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UrlController.class)
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UrlService urlService;

    @Test
    void health_returnsOkPayload() throws Exception {
        when(urlService.health()).thenReturn("ok");

        mockMvc.perform(get("/api/urls/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.module").value("controller"))
                .andExpect(jsonPath("$.service").value("ok"))
                .andExpect(jsonPath("$.status").value("ok"));
    }
}

package com.example.urlshorten.service;

import org.springframework.stereotype.Service;

@Service
public class UrlServiceImpl implements UrlService {

    @Override
    public String health() {
        return "ok";
    }
}

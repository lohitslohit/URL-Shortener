package com.example.urlshorten;

import com.example.urlshorten.config.PostgresDatabaseCreator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UrlShortenApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(UrlShortenApplication.class);
        app.addListeners(new PostgresDatabaseCreator());
        app.run(args);
    }
}

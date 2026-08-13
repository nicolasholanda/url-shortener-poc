package com.nicolasholanda.urlshortener.controller;

import com.nicolasholanda.urlshortener.service.UrlShortenerService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final UrlShortenerService service;

    public RedirectController(UrlShortenerService service) {
        this.service = service;
    }

    @GetMapping("/{shortKey:[0-9A-Za-z]{1,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortKey) {
        String longUrl = service.resolve(shortKey);

        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, longUrl)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .build();
    }
}

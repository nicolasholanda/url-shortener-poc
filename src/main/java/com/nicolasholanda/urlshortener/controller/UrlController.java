package com.nicolasholanda.urlshortener.controller;

import com.nicolasholanda.urlshortener.domain.UrlMapping;
import com.nicolasholanda.urlshortener.dto.CreateShortUrlRequest;
import com.nicolasholanda.urlshortener.dto.ShortUrlResponse;
import com.nicolasholanda.urlshortener.service.UrlShortenerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlShortenerService service;

    public UrlController(UrlShortenerService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShortUrlResponse> shorten(@Valid @RequestBody CreateShortUrlRequest request) {
        UrlMapping mapping = service.shorten(request.url(), request.ttl());
        String shortUrl = service.shortUrlFor(mapping);

        ShortUrlResponse body = new ShortUrlResponse(
                mapping.getShortKey(),
                shortUrl,
                mapping.getLongUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                null);

        return ResponseEntity.created(URI.create(shortUrl)).body(body);
    }

    @GetMapping("/{shortKey}")
    public ShortUrlResponse get(@PathVariable String shortKey) {
        UrlMapping mapping = service.findByShortKey(shortKey);

        return new ShortUrlResponse(
                mapping.getShortKey(),
                service.shortUrlFor(mapping),
                mapping.getLongUrl(),
                mapping.getCreatedAt(),
                mapping.getExpiresAt(),
                service.clickCount(shortKey));
    }
}

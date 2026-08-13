package com.nicolasholanda.urlshortener.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShortUrlResponse(
        String shortKey,
        String shortUrl,
        String longUrl,
        Instant createdAt,
        Instant expiresAt,
        Long clicks) {
}

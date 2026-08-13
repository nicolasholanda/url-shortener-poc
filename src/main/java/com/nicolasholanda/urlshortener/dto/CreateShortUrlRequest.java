package com.nicolasholanda.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Duration;

public record CreateShortUrlRequest(
        @NotBlank(message = "url is required")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        String url,

        Duration ttl) {
}

package com.nicolasholanda.urlshortener.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "shortener")
public record AppProperties(
        @NotBlank String baseUrl,
        @Min(0) @Max(1023) long nodeId,
        @NotNull Duration cacheTtl,
        @NotNull RateLimit rateLimit) {

    public record RateLimit(@Min(1) long capacity, @Min(1) long refillPerSecond) {
    }

    public String shortUrlFor(String shortKey) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalized + "/" + shortKey;
    }
}

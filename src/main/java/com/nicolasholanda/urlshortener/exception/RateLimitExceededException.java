package com.nicolasholanda.urlshortener.exception;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

    private final String clientKey;
    private final Duration retryAfter;

    public RateLimitExceededException(String clientKey, Duration retryAfter) {
        super("rate limit exceeded for client: " + clientKey);
        this.clientKey = clientKey;
        this.retryAfter = retryAfter;
    }

    public String getClientKey() {
        return clientKey;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}

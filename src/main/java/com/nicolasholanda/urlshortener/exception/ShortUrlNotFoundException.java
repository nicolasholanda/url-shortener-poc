package com.nicolasholanda.urlshortener.exception;

public class ShortUrlNotFoundException extends RuntimeException {

    private final String shortKey;

    public ShortUrlNotFoundException(String shortKey) {
        super("no active mapping for short key: " + shortKey);
        this.shortKey = shortKey;
    }

    public String getShortKey() {
        return shortKey;
    }
}

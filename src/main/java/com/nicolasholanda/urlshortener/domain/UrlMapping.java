package com.nicolasholanda.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "url_mapping")
public class UrlMapping {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "short_key", nullable = false, unique = true, length = 16)
    private String shortKey;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "long_url_hash", nullable = false, length = 64)
    private String longUrlHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected UrlMapping() {
    }

    public UrlMapping(Long id, String shortKey, String longUrl, String longUrlHash, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.shortKey = shortKey;
        this.longUrl = longUrl;
        this.longUrlHash = longUrlHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getShortKey() {
        return shortKey;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getLongUrlHash() {
        return longUrlHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UrlMapping that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "UrlMapping{id=" + id + ", shortKey='" + shortKey + "'}";
    }
}

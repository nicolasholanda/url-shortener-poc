package com.nicolasholanda.urlshortener.service;

import com.nicolasholanda.urlshortener.config.AppProperties;
import com.nicolasholanda.urlshortener.domain.UrlMapping;
import com.nicolasholanda.urlshortener.exception.InvalidUrlException;
import com.nicolasholanda.urlshortener.exception.ShortUrlNotFoundException;
import com.nicolasholanda.urlshortener.id.SnowflakeIdGenerator;
import com.nicolasholanda.urlshortener.repository.UrlMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UrlShortenerServiceTest {

    private static final String LONG_URL = "https://example.com/a/very/long/path?with=query";

    @Mock
    private UrlMappingRepository repository;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UrlShortenerService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                "http://localhost:8080",
                1L,
                Duration.ofHours(1),
                new AppProperties.RateLimit(20L, 5L));

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(repository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new UrlShortenerService(repository, new SnowflakeIdGenerator(1L), redis, properties);
    }

    @Test
    @DisplayName("creates a mapping and warms the cache")
    void shortensNewUrl() {
        when(repository.findByLongUrl(anyString(), eq(LONG_URL))).thenReturn(Optional.empty());

        UrlMapping mapping = service.shorten(LONG_URL, null);

        assertThat(mapping.getShortKey()).isNotBlank();
        assertThat(mapping.getLongUrl()).isEqualTo(LONG_URL);
        assertThat(mapping.getExpiresAt()).isNull();
        verify(valueOperations).set(eq("url:" + mapping.getShortKey()), eq(LONG_URL), any(Duration.class));
    }

    @Test
    @DisplayName("is idempotent for a url that was already shortened")
    void reusesExistingMapping() {
        UrlMapping existing = new UrlMapping(42L, "abc", LONG_URL, "hash", Instant.now(), null);
        when(repository.findByLongUrl(anyString(), eq(LONG_URL))).thenReturn(Optional.of(existing));

        UrlMapping mapping = service.shorten(LONG_URL, null);

        assertThat(mapping).isSameAs(existing);
        verify(repository, never()).save(any(UrlMapping.class));
    }

    @Test
    @DisplayName("applies the requested ttl")
    void appliesTtl() {
        when(repository.findByLongUrl(anyString(), eq(LONG_URL))).thenReturn(Optional.empty());

        UrlMapping mapping = service.shorten(LONG_URL, Duration.ofMinutes(30));

        assertThat(mapping.getExpiresAt()).isNotNull();
        assertThat(mapping.getExpiresAt()).isAfter(Instant.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-a-url", "ftp://example.com/file", "/relative/path", "https://"})
    @DisplayName("rejects urls that are blank, relative or use an unsupported scheme")
    void rejectsInvalidUrls(String candidate) {
        assertThatThrownBy(() -> service.shorten(candidate, null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    @DisplayName("serves a cache hit without touching the database")
    void resolvesFromCache() {
        when(valueOperations.get("url:abc")).thenReturn(LONG_URL);

        assertThat(service.resolve("abc")).isEqualTo(LONG_URL);

        verify(repository, never()).findByShortKey(anyString());
        verify(valueOperations).increment("clicks:abc");
    }

    @Test
    @DisplayName("falls back to the database on a cache miss and repopulates the cache")
    void resolvesFromDatabaseOnMiss() {
        UrlMapping mapping = new UrlMapping(42L, "abc", LONG_URL, "hash", Instant.now(), null);
        when(valueOperations.get("url:abc")).thenReturn(null);
        when(repository.findByShortKey("abc")).thenReturn(Optional.of(mapping));

        assertThat(service.resolve("abc")).isEqualTo(LONG_URL);

        verify(valueOperations).set(eq("url:abc"), eq(LONG_URL), any(Duration.class));
    }

    @Test
    @DisplayName("still resolves when redis is unavailable")
    void degradesGracefullyWhenRedisIsDown() {
        UrlMapping mapping = new UrlMapping(42L, "abc", LONG_URL, "hash", Instant.now(), null);
        when(valueOperations.get("url:abc")).thenThrow(new RedisConnectionFailureException("down"));
        when(repository.findByShortKey("abc")).thenReturn(Optional.of(mapping));

        assertThat(service.resolve("abc")).isEqualTo(LONG_URL);
    }

    @Test
    @DisplayName("treats the negative cache sentinel as a miss without hitting the database")
    void honoursNegativeCache() {
        when(valueOperations.get("url:abc")).thenReturn("__MISS__");

        assertThatThrownBy(() -> service.resolve("abc"))
                .isInstanceOf(ShortUrlNotFoundException.class);

        verify(repository, never()).findByShortKey(anyString());
    }

    @Test
    @DisplayName("rejects an expired mapping")
    void rejectsExpiredMapping() {
        UrlMapping expired = new UrlMapping(42L, "abc", LONG_URL, "hash",
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        when(valueOperations.get("url:abc")).thenReturn(null);
        when(repository.findByShortKey("abc")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.resolve("abc"))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }

    @Test
    @DisplayName("rejects a short key that cannot be a base62 value")
    void rejectsMalformedShortKey() {
        assertThatThrownBy(() -> service.resolve("not a key"))
                .isInstanceOf(ShortUrlNotFoundException.class);

        verify(repository, never()).findByShortKey(anyString());
    }

    @Test
    @DisplayName("builds the public short url from the configured base url")
    void buildsShortUrl() {
        UrlMapping mapping = new UrlMapping(42L, "abc", LONG_URL, "hash", Instant.now(), null);

        assertThat(service.shortUrlFor(mapping)).isEqualTo("http://localhost:8080/abc");
    }
}

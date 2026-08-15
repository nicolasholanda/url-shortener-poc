# url-shortener-poc

A distributed URL shortener, built as a working POC for three designs from
*System Design Interview* (Alex Xu): unique ID generation (Ch. 7), rate limiting (Ch. 4),
and the read-heavy URL shortener itself (Ch. 8).

The point isn't the shortener — it's having a real system where you can measure the
things the book only estimates: cache hit ratio, p99 latency, what a token bucket
actually does under load, and how the service behaves when Redis disappears.

## How it works

**Write path** — `POST /api/v1/urls` generates a Snowflake ID (41-bit timestamp,
10-bit node, 12-bit sequence), Base62-encodes it into a short key, persists the mapping
and warms the cache. Shortening the same URL twice returns the same key.

**Read path** — `GET /{shortKey}` is cache-aside: Redis first, Postgres on a miss,
then the cache is repopulated. Misses are negatively cached for 30s so a scan of random
keys can't hammer the database. Clicks are counted with a Redis `INCR` off the critical path.

**Rate limiting** — a token bucket implemented as a single atomic Lua script in Redis,
keyed by `X-Api-Key` or client IP. State lives in Redis, so it holds across instances.
Responses carry `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` on 429.

Redis is treated as an accelerator, not a dependency: if it's unreachable, reads fall
through to Postgres and the limiter fails open.

## Stack

Java 21 · Spring Boot 3.5 · PostgreSQL 16 + Flyway · Redis 7 · Maven · JUnit 5 + Mockito · k6 · Docker Compose

## API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/urls` | Create a short URL. Body: `{"url": "...", "ttl": "PT24H"}` (`ttl` optional) |
| `GET` | `/api/v1/urls/{key}` | Mapping metadata + click count |
| `GET` | `/{key}` | 301 redirect to the original URL |
| `GET` | `/actuator/health` | Health, including Postgres and Redis |

Errors come back as a consistent JSON payload — 400 for an invalid or non-http(s) URL,
404 for an unknown or expired key, 429 when the bucket is empty.

## Running it

```bash
docker compose up -d
./mvnw spring-boot:run
```

Then:

```bash
curl -X POST localhost:8080/api/v1/urls \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/some/very/long/path"}'

curl -i localhost:8080/<shortKey>
```

Tests: `./mvnw test`

Configuration lives in `application.yml` and is overridable by environment variable —
see `.env.example`. `SHORTENER_NODE_ID` must be unique per instance (0–1023), which is
what keeps Snowflake IDs collision-free when you scale out.

## Load testing

```bash
k6 run loadtest/redirect.js   # read-heavy, p95 < 50ms
k6 run loadtest/shorten.js    # write path, trips the rate limiter on purpose
```

See `loadtest/README.md`. The interesting run is `redirect.js` with Redis stopped —
that's the cache-hit ratio showing up in the p99.

## Capacity estimation

Working from the book's assumptions: 100M writes/day.

- Writes: ~1,160/s average, call it 3,500/s at peak
- Reads at 10:1: ~11,600/s average
- 10 years of data: ~365B records at ~500 bytes → ~182 TB
- Key space: 62⁷ ≈ 3.5 trillion, so 7 Base62 characters cover it with room to spare

Snowflake gives 4,096 IDs per millisecond per node — roughly 4M/s per node, so ID
generation is nowhere near the bottleneck. Storage is, which is why the real design
shards by short key.

## Things worth trying

- Run two instances on different `SHORTENER_NODE_ID`s behind a proxy and confirm both
  the limiter and ID generation still behave.
- `docker compose stop redis` mid-load-test and watch the fallback.
- Drop `SHORTENER_RATELIMIT_CAPACITY` to 5 and watch 429s appear in `shorten.js`.

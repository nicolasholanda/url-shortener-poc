# Load tests

Two k6 scenarios, one per side of the read/write split.

## redirect.js — the read-heavy path

Seeds a few hundred short URLs, then ramps to 500 req/s of pure redirects.
This is the scenario that shows whether the Redis cache-aside layer is doing its job.

```bash
k6 run loadtest/redirect.js
k6 run -e BASE_URL=http://localhost:8080 -e SEED_KEYS=500 loadtest/redirect.js
```

Thresholds: p95 < 50ms, p99 < 120ms, error rate < 1%.

To see the cache actually earn its keep, run it once with Redis stopped
(`docker compose stop redis`) and compare `redirect_duration` — every hit falls
through to Postgres and the p99 climbs sharply.

## shorten.js — the write path and the rate limiter

Constant 30 req/s of `POST /api/v1/urls` with a per-VU API key. With the default
bucket (capacity 20, refill 5/s) a share of requests is expected to come back
429 — the `rate_limited` counter shows how many.

```bash
k6 run loadtest/shorten.js
```

Raise the ceiling to compare:

```bash
SHORTENER_RATELIMIT_CAPACITY=200 SHORTENER_RATELIMIT_REFILL_PER_SECOND=100 ./mvnw spring-boot:run
```

## Notes

- Start the infra first: `docker compose up -d`.
- Both scripts read `BASE_URL`, so they work against a container or a remote host.
- Run two app instances behind a proxy to confirm the limiter is shared state in
  Redis rather than per-process.

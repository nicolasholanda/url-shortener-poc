import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const rateLimited = new Counter('rate_limited');

export const options = {
  scenarios: {
    steady_writes: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 20,
      maxVUs: 60,
    },
  },
  thresholds: {
    'http_req_duration{expected_response:true}': ['p(95)<300', 'p(99)<600'],
    'checks': ['rate>0.99'],
  },
};

export default function () {
  const payload = JSON.stringify({
    url: `https://example.com/article/${__VU}/${__ITER}?ref=k6`,
  });

  const res = http.post(`${BASE_URL}/api/v1/urls`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'X-Api-Key': `loadtest-${__VU}`,
    },
  });

  if (res.status === 429) {
    rateLimited.add(1);
  }

  check(res, {
    'created or rate limited': (r) => r.status === 201 || r.status === 429,
    'sends rate limit headers': (r) => r.headers['X-Ratelimit-Limit'] !== undefined,
    'returns a short url when created': (r) => r.status !== 201 || r.json('shortUrl') !== '',
  });
}

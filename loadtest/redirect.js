import http from 'k6/http';
import { check, fail } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEED_KEYS = Number(__ENV.SEED_KEYS || 200);

const redirectDuration = new Trend('redirect_duration', true);

export const options = {
  scenarios: {
    read_heavy: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { target: 500, duration: '30s' },
        { target: 500, duration: '60s' },
        { target: 0, duration: '15s' },
      ],
    },
  },
  thresholds: {
    redirect_duration: ['p(95)<50', 'p(99)<120'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  const keys = [];

  for (let i = 0; i < SEED_KEYS; i++) {
    const res = http.post(
      `${BASE_URL}/api/v1/urls`,
      JSON.stringify({ url: `https://example.com/seed/${i}` }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Api-Key': `seed-${i % 10}`,
        },
      },
    );

    if (res.status === 201) {
      keys.push(res.json('shortKey'));
    }
  }

  if (keys.length === 0) {
    fail('setup could not seed any short urls, is the app running?');
  }

  return { keys };
}

export default function (data) {
  const key = data.keys[Math.floor(Math.random() * data.keys.length)];

  const res = http.get(`${BASE_URL}/${key}`, { redirects: 0 });
  redirectDuration.add(res.timings.duration);

  check(res, {
    'redirects permanently': (r) => r.status === 301,
    'sets a location header': (r) => !!r.headers['Location'],
  });
}

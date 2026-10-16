import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const vus = Number(__ENV.VUS || 500);
const duration = __ENV.DURATION || '3m';
const thinkTime = Number(__ENV.THINK_TIME || 1);

const warmupVUs = Number(__ENV.WARMUP_VUS || Math.max(1, Math.min(vus, 50)));
const warmupDuration = __ENV.WARMUP_DURATION || '30s';

const maxErrorRate = Number(__ENV.MAX_ERROR_RATE || 0.01);
const minCheckRate = Number(__ENV.MIN_CHECK_RATE || 0.99);

const thresholds = {
    'http_req_failed{phase:measurement}': [`rate<${maxErrorRate}`],
    'checks{phase:measurement}': [`rate>${minCheckRate}`],
};

const latencyThresholds = [];
if (__ENV.P95_MS) {
    latencyThresholds.push(`p(95)<${Number(__ENV.P95_MS)}`);
}
if (__ENV.P99_MS) {
    latencyThresholds.push(`p(99)<${Number(__ENV.P99_MS)}`);
}
if (latencyThresholds.length > 0) {
    thresholds['http_req_duration{phase:measurement}'] = latencyThresholds;
}

export const options = {
    discardResponseBodies: true,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds,
    scenarios: {
        warmup: {
            executor: 'constant-vus',
            exec: 'getDailyProblems',
            vus: warmupVUs,
            duration: warmupDuration,
            gracefulStop: '0s',
            tags: { phase: 'warmup' },
        },
        dailyProblems: {
            executor: 'constant-vus',
            exec: 'getDailyProblems',
            startTime: warmupDuration,
            vus,
            duration,
            gracefulStop: '10s',
            tags: { phase: 'measurement' },
        },
    },
};

export function getDailyProblems() {
    const response = http.get(`${baseUrl}/daily-problems`, {
        timeout: __ENV.HTTP_TIMEOUT || '10s',
        tags: { name: 'GET /daily-problems' },
    });

    check(response, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(thinkTime);
}

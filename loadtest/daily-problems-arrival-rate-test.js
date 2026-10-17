import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const rate = Number(__ENV.RATE || 100);
const duration = __ENV.DURATION || '5m';
const preAllocatedVUs = Number(__ENV.PREALLOCATED_VUS || 100);
const maxVUs = Number(__ENV.MAX_VUS || 500);

const warmupRate = Number(__ENV.WARMUP_RATE || Math.min(rate, 10));
const warmupDuration = __ENV.WARMUP_DURATION || '30s';
const warmupPreAllocatedVUs = Number(
    __ENV.WARMUP_PREALLOCATED_VUS || Math.min(preAllocatedVUs, 20),
);
const warmupMaxVUs = Number(
    __ENV.WARMUP_MAX_VUS || Math.max(warmupPreAllocatedVUs, Math.min(maxVUs, 50)),
);

const maxErrorRate = Number(__ENV.MAX_ERROR_RATE || 0.01);
const minCheckRate = Number(__ENV.MIN_CHECK_RATE || 0.99);
const maxDroppedIterations = Number(__ENV.MAX_DROPPED_ITERATIONS || 0);

const thresholds = {
    'http_req_failed{phase:measurement}': [`rate<${maxErrorRate}`],
    'checks{phase:measurement}': [`rate>${minCheckRate}`],
    'dropped_iterations{scenario:dailyProblems}': [`count<=${maxDroppedIterations}`],
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
            executor: 'constant-arrival-rate',
            exec: 'getDailyProblems',
            rate: warmupRate,
            timeUnit: '1s',
            duration: warmupDuration,
            preAllocatedVUs: warmupPreAllocatedVUs,
            maxVUs: warmupMaxVUs,
            gracefulStop: '0s',
            tags: { phase: 'warmup' },
        },
        dailyProblems: {
            executor: 'constant-arrival-rate',
            exec: 'getDailyProblems',
            startTime: warmupDuration,
            rate,
            timeUnit: '1s',
            duration,
            preAllocatedVUs,
            maxVUs,
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
}

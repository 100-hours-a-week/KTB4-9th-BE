import http from 'k6/http';
import { check } from 'k6';

export const options = {
    scenarios: {
        load_test: {
            executor: 'ramping-arrival-rate',

            startRate: 70,
            timeUnit: '1s',

            preAllocatedVUs: 700,
            maxVUs: 2000,

            stages: [
                // 0 ~ 3분: 70 RPS
                { target: 70, duration: '3m' },

                // 110 RPS
                { target: 1000, duration: '1s' },
                { target: 1000, duration: '2m59s' },


            ],

            gracefulStop: '30s',
        },
    },
};

export default function () {
    const response = http.get(
        'http://localhost:8080/daily-problems',
        {
            timeout: '30s',
        }
    );

    check(response, {
        'status is 200': (r) => r.status === 200,
    });
}
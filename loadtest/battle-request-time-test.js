import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

// 1. 구간별 지연 시간 (ms)
const tomcatToFilter = new Trend('tomcat_to_filter_ms');
const filterToController = new Trend('filter_to_controller_ms');
const tomcatToController = new Trend('tomcat_to_controller_ms');

// 2. 테스트 설정
// export const options = {
//     scenarios: {
//         baseline: {
//             executor: 'shared-iterations',
//             vus: 1,
//             iterations: 100,
//             maxDuration: '2m',
//         },
//     },
//     thresholds: {
//         http_req_failed: ['rate<0.01'],
//     },
// };


// export const options = {
//     scenarios: {
//         concurrent: {
//             executor: 'constant-vus',
//             vus: 10,
//             duration: '1m',
//         },
//     },
//     thresholds: {
//         http_req_failed: ['rate<0.01'],
//     },
// };

export const options = {
    scenarios: {
        spike: {
            executor: 'per-vu-iterations',
            vus: 500,
            iterations: 1,
            maxDuration: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
    },
};

// 3. API 요청
export default function () {
    const response = http.get(
        'http://localhost:8080/daily-battles/test/request-time'
    );

    // 4. 응답 검증
    const success = check(response, {
        'status is 200': (r) => r.status === 200,
    });

    if (success) {
        const data = response.json();

        // μs → ms 변환
        tomcatToFilter.add(data.tomcatToFilterUs / 1000);
        filterToController.add(data.filterToControllerUs / 1000);
        tomcatToController.add(data.tomcatToControllerUs / 1000);
    }

}

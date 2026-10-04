import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {

    // 1. 동시에 요청을 보내는 가상 사용자 수
    vus: 500,

    // 2. 테스트 지속 시간
    duration: '30s',
};

export default function () {

    // 3. 데일리 문제 API 호출
    const response = http.get('http://localhost:8080/daily-problems');

    // 4. 정상 응답인지 확인
    check(response, {
        'status is 200': (r) => r.status === 200,
    });

    // 5. 한 사용자가 요청 후 잠깐 기다리는 상황
    sleep(1);
}
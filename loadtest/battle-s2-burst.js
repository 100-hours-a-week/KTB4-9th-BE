import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Rate, Trend } from 'k6/metrics';

const USER_COUNT = 100;
const KST_OFFSET = '+09:00';
const BASE_URL = requiredEnv('BASE_URL').replace(/\/+$/, '');
const TOKEN_FILE = __ENV.TOKEN_FILE || './data/battle-users.json';
const BATTLE_OPEN_TIME = __ENV.BATTLE_OPEN_TIME || '12:50:00';
const BATTLE_BURST_SUBMISSION_TIME = __ENV.BATTLE_BURST_SUBMISSION_TIME
    || __ENV.BATTLE_LAST_SUBMISSION_TIME
    || '12:59:50';
const BATTLE_SUBMISSION_DEADLINE = __ENV.BATTLE_SUBMISSION_DEADLINE || '12:59:59';
const BATTLE_RESULT_TIME = __ENV.BATTLE_RESULT_TIME || '13:00:00';
const PARTICIPATION_START_GRACE_MS = Number(
    __ENV.BATTLE_PARTICIPATION_START_GRACE_MS || '500',
);
const PARTICIPATION_SPREAD_MS = Number(
    __ENV.BATTLE_PARTICIPATION_SPREAD_MS || '30000',
);

validateBurstSchedule();

const firstPollReady = new Rate('battle_first_poll_ready');
const firstPollCompleted = new Rate('battle_first_poll_grading_completed');
const submissionScheduleMissed = new Rate('battle_submission_schedule_missed');
const submissionLateness = new Trend('battle_submission_lateness_ms', true);

const users = new SharedArray('battle S2 burst users', function () {
    const parsed = JSON.parse(open(TOKEN_FILE));

    if (!Array.isArray(parsed) || parsed.length !== USER_COUNT) {
        throw new Error(`TOKEN_FILE must contain exactly ${USER_COUNT} users`);
    }

    const userIds = new Set();
    for (const user of parsed) {
        if (!user.user_id || !user.access_token) {
            throw new Error('each test user requires user_id and access_token');
        }
        if (userIds.has(String(user.user_id))) {
            throw new Error(`duplicate user_id in TOKEN_FILE: ${user.user_id}`);
        }
        userIds.add(String(user.user_id));
    }

    return parsed;
});

export const options = {
    discardResponseBodies: false,
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        burstSubmission: {
            executor: 'per-vu-iterations',
            exec: 'burstSubmission',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '12m',
        },
    },
    thresholds: {
        'checks{operation:current}': ['rate==1'],
        'checks{operation:participate}': ['rate==1'],
        'checks{operation:submit}': ['rate==1'],
        'checks{operation:first_result}': ['rate==1'],
        battle_first_poll_ready: ['rate==1'],
        battle_first_poll_grading_completed: ['rate==1'],
        battle_submission_schedule_missed: ['rate==0'],
    },
};

export function burstSubmission() {
    const userIndex = exec.vu.idInTest - 1;
    const user = users[userIndex];
    const requestParams = params(user.access_token);

    const beforeCurrent = Date.now();
    const currentResponse = http.get(
        `${BASE_URL}/daily-battles/current`,
        tagged(requestParams, 'current'),
    );
    const afterCurrent = Date.now();

    const currentOk = check(currentResponse, {
        'current battle request is 200': (response) => response.status === 200,
    }, { operation: 'current' });
    if (!currentOk) {
        exec.test.abort(`current battle request failed with ${currentResponse.status}`);
    }

    const current = json(currentResponse);
    const serverTimeText = current?.data?.server_time ?? current?.data?.serverTime;
    const currentBattleId = Number(
        __ENV.BATTLE_ID
        || current?.data?.current_battle_id
        || current?.data?.currentBattleId,
    );
    const serverTime = parseKst(serverTimeText);

    if (!Number.isFinite(currentBattleId) || Number.isNaN(serverTime.getTime())) {
        exec.test.abort('current battle response is missing battle id or server time');
    }

    // 부하 발생기와 개발 서버의 시각 차이를 왕복 시간의 중간값으로 보정합니다.
    const clockOffsetMs = serverTime.getTime() - ((beforeCurrent + afterCurrent) / 2);
    const battleDate = serverTimeText.slice(0, 10);
    const opensAt = kstTime(battleDate, BATTLE_OPEN_TIME);
    const burstSubmissionAt = kstTime(battleDate, BATTLE_BURST_SUBMISSION_TIME);
    const resultAt = kstTime(battleDate, BATTLE_RESULT_TIME);

    // 참여 API가 S2의 동시 제출 결과를 오염시키지 않도록 배틀 초반 30초에 분산합니다.
    const participationOffsetMs = USER_COUNT === 1
        ? 0
        : (PARTICIPATION_SPREAD_MS * userIndex / (USER_COUNT - 1));
    const scheduledParticipationAt = opensAt
        + PARTICIPATION_START_GRACE_MS
        + participationOffsetMs;

    sleepUntilServerTime(scheduledParticipationAt, clockOffsetMs);

    const participationResponse = http.post(
        `${BASE_URL}/daily-battles/${currentBattleId}/participations`,
        null,
        tagged(requestParams, 'participate'),
    );
    const participationOk = check(participationResponse, {
        'participation is 200': (response) => response.status === 200,
    }, { operation: 'participate' });
    if (!participationOk) {
        return;
    }

    // 모든 VU가 같은 서버 시각에 제출하여 100명 동시 제출 burst를 만듭니다.
    sleepUntilServerTime(burstSubmissionAt, clockOffsetMs);
    const latenessMs = (Date.now() + clockOffsetMs) - burstSubmissionAt;
    submissionLateness.add(latenessMs);
    const missedSchedule = latenessMs > 2_000;
    submissionScheduleMissed.add(missedSchedule);
    if (missedSchedule) {
        return;
    }

    const submissionResponse = http.post(
        `${BASE_URL}/daily-battles/${currentBattleId}/submissions`,
        JSON.stringify({
            battleId: currentBattleId,
            userAnswers: [
                { battle_case_num: 1, submitted_output: 's2-answer-1' },
                { battle_case_num: 2, submitted_output: 's2-answer-2' },
                { battle_case_num: 3, submitted_output: 's2-answer-3' },
            ],
        }),
        tagged(requestParams, 'submit'),
    );
    check(submissionResponse, {
        'submission is accepted': (response) => response.status === 202,
    }, { operation: 'submit' });

    sleepUntilServerTime(resultAt, clockOffsetMs);
    const resultResponse = http.get(
        `${BASE_URL}/daily-battles/${currentBattleId}/results`,
        tagged(requestParams, 'first_result'),
    );
    const result = json(resultResponse);
    const participationStatus = result?.data?.my_result?.participation_status;

    const ready = resultResponse.status === 200;
    const completed = ready && participationStatus === 'GRADING_COMPLETED';
    firstPollReady.add(ready);
    firstPollCompleted.add(completed);

    check(resultResponse, {
        'first result poll is 200': () => ready,
        'own grading is completed on first poll': () => completed,
    }, { operation: 'first_result' });
}

function requiredEnv(name) {
    const value = __ENV[name];
    if (!value) {
        throw new Error(`${name} is required`);
    }
    return value;
}

function validateBurstSchedule() {
    const date = '2000-01-01';
    const openAt = kstTime(date, BATTLE_OPEN_TIME);
    const burstSubmissionAt = kstTime(date, BATTLE_BURST_SUBMISSION_TIME);
    const deadlineAt = kstTime(date, BATTLE_SUBMISSION_DEADLINE);
    const resultAt = kstTime(date, BATTLE_RESULT_TIME);

    if ([openAt, burstSubmissionAt, deadlineAt, resultAt].some(Number.isNaN)) {
        throw new Error('battle times must use HH:mm:ss format');
    }
    if (!Number.isFinite(PARTICIPATION_START_GRACE_MS)
            || PARTICIPATION_START_GRACE_MS < 0
            || !Number.isFinite(PARTICIPATION_SPREAD_MS)
            || PARTICIPATION_SPREAD_MS < 0) {
        throw new Error('participation timing values must be zero or positive numbers');
    }
    if (resultAt - openAt !== 10 * 60 * 1000
            || burstSubmissionAt - openAt !== 9 * 60 * 1000 + 50 * 1000
            || resultAt - deadlineAt !== 1000) {
        throw new Error(
            'S2 keeps a 10-minute battle: 100-user burst at +09:50, deadline at +09:59, result at +10:00',
        );
    }
    if (opensAfterParticipationWindow(openAt, burstSubmissionAt)) {
        throw new Error('participation window must finish before the 100-user submission burst');
    }
}

function opensAfterParticipationWindow(openAt, burstSubmissionAt) {
    return openAt + PARTICIPATION_START_GRACE_MS + PARTICIPATION_SPREAD_MS
        >= burstSubmissionAt;
}

function params(accessToken) {
    return {
        headers: {
            'Content-Type': 'application/json',
            Cookie: `accessToken=${accessToken}`,
        },
        timeout: __ENV.HTTP_TIMEOUT || '10s',
    };
}

function tagged(requestParams, operation) {
    return {
        ...requestParams,
        tags: { operation },
    };
}

function json(response) {
    try {
        return response.json();
    } catch (_) {
        return null;
    }
}

function parseKst(value) {
    if (!value) {
        return new Date(Number.NaN);
    }
    const text = String(value).replace(/(\.\d{3})\d+/, '$1');
    const hasZone = /(?:Z|[+-]\d{2}:\d{2})$/i.test(text);
    return new Date(hasZone ? text : `${text}${KST_OFFSET}`);
}

function kstTime(date, time) {
    return new Date(`${date}T${time}${KST_OFFSET}`).getTime();
}

function sleepUntilServerTime(targetTimeMs, clockOffsetMs) {
    const delayMs = targetTimeMs - (Date.now() + clockOffsetMs);
    if (delayMs > 0) {
        sleep(delayMs / 1000);
    }
}

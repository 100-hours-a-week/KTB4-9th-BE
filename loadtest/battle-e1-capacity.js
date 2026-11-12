import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const ALLOWED_USER_COUNTS = new Set([100, 300, 600, 1000]);
const USER_COUNT = numberEnv('USER_COUNT');
const TARGET_SUBMISSION_RPS = numberEnv(
    'TARGET_SUBMISSION_RPS',
    USER_COUNT / 60,
);
const KST_OFFSET = '+09:00';
const BASE_URL = requiredEnv('BASE_URL').replace(/\/+$/, '');
const TOKEN_FILE = __ENV.TOKEN_FILE || './data/battle-users.json';
const BATTLE_OPEN_TIME = requiredEnv('BATTLE_OPEN_TIME');
const BATTLE_SUBMISSION_DEADLINE = requiredEnv('BATTLE_SUBMISSION_DEADLINE');
const BATTLE_RESULT_TIME = requiredEnv('BATTLE_RESULT_TIME');
const FIRST_SUBMISSION_OFFSET_MS = numberEnv(
    'FIRST_SUBMISSION_OFFSET_SECONDS',
    300,
) * 1000;
const LAST_SUBMISSION_LEAD_MS = numberEnv(
    'LAST_SUBMISSION_LEAD_SECONDS',
    239,
) * 1000;
const PARTICIPATION_START_OFFSET_MS = numberEnv(
    'PARTICIPATION_START_OFFSET_SECONDS',
    10,
) * 1000;
const PARTICIPATION_END_OFFSET_MS = numberEnv(
    'PARTICIPATION_END_OFFSET_SECONDS',
    270,
) * 1000;
const SCHEDULE_MISS_TOLERANCE_MS = numberEnv(
    'SCHEDULE_MISS_TOLERANCE_MS',
    2000,
);

validateConfiguration();

const participationScheduleMissed = new Rate('battle_participation_schedule_missed');
const submissionScheduleMissed = new Rate('battle_submission_schedule_missed');
const participationLateness = new Trend('battle_participation_lateness_ms', true);
const submissionLateness = new Trend('battle_submission_lateness_ms', true);
const submissionAttempts = new Counter('battle_submission_attempts');
const submissionAccepted = new Counter('battle_submission_accepted');

const users = new SharedArray('battle E1 capacity users', function () {
    const parsed = JSON.parse(open(TOKEN_FILE));

    if (!Array.isArray(parsed) || parsed.length < USER_COUNT) {
        throw new Error(`TOKEN_FILE must contain at least ${USER_COUNT} users`);
    }

    const selectedUsers = parsed.slice(0, USER_COUNT);
    const userIds = new Set();
    for (const user of selectedUsers) {
        if (!user.user_id || !user.access_token) {
            throw new Error('each test user requires user_id and access_token');
        }
        if (userIds.has(String(user.user_id))) {
            throw new Error(`duplicate user_id in TOKEN_FILE: ${user.user_id}`);
        }
        userIds.add(String(user.user_id));
    }

    return selectedUsers;
});

export const options = {
    discardResponseBodies: false,
    tags: {
        scenario_id: 'e1-capacity',
        user_count: String(USER_COUNT),
        target_submission_rps: String(TARGET_SUBMISSION_RPS),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        capacityBaseline: {
            executor: 'per-vu-iterations',
            exec: 'capacitySubmission',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: '12m',
        },
        controlApiProbe: {
            executor: 'constant-arrival-rate',
            exec: 'controlApiProbe',
            rate: 1,
            timeUnit: '1s',
            duration: '11m',
            preAllocatedVUs: 2,
            maxVUs: 10,
        },
    },
    thresholds: {
        'checks{operation:current}': ['rate==1'],
        'checks{operation:participate}': ['rate==1'],
        'checks{operation:submit}': ['rate==1'],
        'checks{operation:control_api}': ['rate==1'],
        battle_participation_schedule_missed: ['rate==0'],
        battle_submission_schedule_missed: ['rate==0'],
    },
};

// 배틀 부하가 다른 API 응답 시간에 미치는 영향을 비교하기 위한 고정 1 RPS 요청입니다.
export function controlApiProbe() {
    const response = http.get(
        `${BASE_URL}/daily-problems`,
        {
            tags: { operation: 'control_api' },
            timeout: __ENV.HTTP_TIMEOUT || '10s',
        },
    );

    check(response, {
        'control API request is 200': (result) => result.status === 200,
    }, { operation: 'control_api' });
}

// 모든 VU가 current API를 동시에 호출해 실험 부하를 오염시키지 않도록 한 번만 조회합니다.
export function setup() {
    const beforeCurrent = Date.now();
    const currentResponse = http.get(
        `${BASE_URL}/daily-battles/current`,
        tagged(params(users[0].access_token), 'current'),
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

    return {
        battleId: currentBattleId,
        battleDate: serverTimeText.slice(0, 10),
        clockOffsetMs: serverTime.getTime() - ((beforeCurrent + afterCurrent) / 2),
    };
}

export function capacitySubmission(testContext) {
    // controlApiProbe와 VU 번호를 공유하지 않도록 이 시나리오 내부의 고유 반복 번호를 사용합니다.
    const userIndex = exec.scenario.iterationInTest;
    const user = users[userIndex];
    if (!user) {
        exec.test.abort(`capacity user index is out of range: ${userIndex}/${USER_COUNT}`);
    }
    const requestParams = params(user.access_token);
    const opensAt = kstTime(testContext.battleDate, BATTLE_OPEN_TIME);
    const firstSubmissionAt = opensAt + FIRST_SUBMISSION_OFFSET_MS;

    // 목표 RPS에 맞춰 각 사용자의 제출 시각을 일정한 간격으로 배치합니다.
    const scheduledSubmissionAt = firstSubmissionAt
        + (userIndex * 1000 / TARGET_SUBMISSION_RPS);
    // 제출 부하와 참여 INSERT가 겹치지 않도록 참여는 제출 시작 30초 전까지 미리 완료합니다.
    const scheduledParticipationAt = USER_COUNT === 1
        ? opensAt + PARTICIPATION_START_OFFSET_MS
        : opensAt
            + PARTICIPATION_START_OFFSET_MS
            + ((PARTICIPATION_END_OFFSET_MS - PARTICIPATION_START_OFFSET_MS)
                * userIndex / (USER_COUNT - 1));

    sleepUntilServerTime(scheduledParticipationAt, testContext.clockOffsetMs);
    const participationLatenessMs = serverNow(testContext.clockOffsetMs)
        - scheduledParticipationAt;
    participationLateness.add(participationLatenessMs);
    const missedParticipation = participationLatenessMs > SCHEDULE_MISS_TOLERANCE_MS;
    participationScheduleMissed.add(missedParticipation);
    if (missedParticipation) {
        return;
    }

    const participationResponse = http.post(
        `${BASE_URL}/daily-battles/${testContext.battleId}/participations`,
        null,
        tagged(requestParams, 'participate'),
    );
    const participationOk = check(participationResponse, {
        'participation is 200': (response) => response.status === 200,
    }, { operation: 'participate' });
    if (!participationOk) {
        return;
    }

    sleepUntilServerTime(scheduledSubmissionAt, testContext.clockOffsetMs);
    const submissionLatenessMs = serverNow(testContext.clockOffsetMs) - scheduledSubmissionAt;
    submissionLateness.add(submissionLatenessMs);
    const missedSubmission = submissionLatenessMs > SCHEDULE_MISS_TOLERANCE_MS;
    submissionScheduleMissed.add(missedSubmission);
    if (missedSubmission) {
        return;
    }

    submissionAttempts.add(1);
    const submissionResponse = http.post(
        `${BASE_URL}/daily-battles/${testContext.battleId}/submissions`,
        JSON.stringify({
            battleId: testContext.battleId,
            userAnswers: [
                { battle_case_num: 1, submitted_output: 'e1-answer-1' },
                { battle_case_num: 2, submitted_output: 'e1-answer-2' },
                { battle_case_num: 3, submitted_output: 'e1-answer-3' },
            ],
        }),
        tagged(requestParams, 'submit'),
    );
    const submissionOk = check(submissionResponse, {
        'submission is accepted': (response) => response.status === 202,
    }, { operation: 'submit' });
    if (submissionOk) {
        submissionAccepted.add(1);
    }
}

function validateConfiguration() {
    if (!ALLOWED_USER_COUNTS.has(USER_COUNT)) {
        throw new Error('USER_COUNT must be one of 100, 300, 600, 1000');
    }

    const date = '2000-01-01';
    const openAt = kstTime(date, BATTLE_OPEN_TIME);
    const deadlineAt = kstTime(date, BATTLE_SUBMISSION_DEADLINE);
    const resultAt = kstTime(date, BATTLE_RESULT_TIME);

    if ([openAt, deadlineAt, resultAt].some(Number.isNaN)) {
        throw new Error('battle times must use HH:mm:ss format');
    }
    if (resultAt - openAt !== 10 * 60 * 1000 || resultAt - deadlineAt !== 1000) {
        throw new Error('E1 requires a 10-minute battle with deadline one second before result time');
    }
    if (PARTICIPATION_START_OFFSET_MS < 0
        || PARTICIPATION_END_OFFSET_MS <= PARTICIPATION_START_OFFSET_MS) {
        throw new Error('participation preparation window must be positive');
    }
    if (PARTICIPATION_END_OFFSET_MS >= FIRST_SUBMISSION_OFFSET_MS) {
        throw new Error('participation preparation must finish before submissions start');
    }
    if (TARGET_SUBMISSION_RPS <= 0) {
        throw new Error('TARGET_SUBMISSION_RPS must be greater than zero');
    }

    const lastScheduledSubmissionAt = openAt
        + FIRST_SUBMISSION_OFFSET_MS
        + ((USER_COUNT - 1) * 1000 / TARGET_SUBMISSION_RPS);
    if (lastScheduledSubmissionAt > deadlineAt - LAST_SUBMISSION_LEAD_MS) {
        throw new Error('target RPS is too low: all submissions must finish before the deadline safety margin');
    }
    if (SCHEDULE_MISS_TOLERANCE_MS < 0) {
        throw new Error('SCHEDULE_MISS_TOLERANCE_MS must be zero or positive');
    }
}

function requiredEnv(name) {
    const value = __ENV[name];
    if (!value) {
        throw new Error(`${name} is required`);
    }
    return value;
}

function numberEnv(name, defaultValue) {
    const rawValue = __ENV[name];
    if ((rawValue === undefined || rawValue === '') && defaultValue !== undefined) {
        return defaultValue;
    }

    const value = Number(rawValue);
    if (!Number.isFinite(value)) {
        throw new Error(`${name} must be a number`);
    }
    return value;
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

function serverNow(clockOffsetMs) {
    return Date.now() + clockOffsetMs;
}

function sleepUntilServerTime(targetTimeMs, clockOffsetMs) {
    const delayMs = targetTimeMs - serverNow(clockOffsetMs);
    if (delayMs > 0) {
        sleep(delayMs / 1000);
    }
}

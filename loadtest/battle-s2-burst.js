import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const ALLOWED_USER_COUNTS = new Set([100, 200, 300, 500, 1000]);
const USER_COUNT = numberEnv('USER_COUNT');
const KST_OFFSET = '+09:00';
const BASE_URL = requiredEnv('BASE_URL').replace(/\/+$/, '');
const TOKEN_FILE = __ENV.TOKEN_FILE || './data/battle-users.json';
const BATTLE_OPEN_TIME = __ENV.BATTLE_OPEN_TIME || '12:50:00';
const BATTLE_BURST_SUBMISSION_TIME = requiredEnv('BATTLE_BURST_SUBMISSION_TIME');
const BATTLE_SUBMISSION_DEADLINE = __ENV.BATTLE_SUBMISSION_DEADLINE || '12:59:59';
const BATTLE_RESULT_TIME = __ENV.BATTLE_RESULT_TIME || '13:00:00';
const SUBMISSION_ONLY = (__ENV.SUBMISSION_ONLY || 'false').toLowerCase() === 'true';
const PARTICIPATION_START_GRACE_MS = Number(
    __ENV.BATTLE_PARTICIPATION_START_GRACE_MS || '10000',
);
const PARTICIPATION_SPREAD_MS = Number(
    __ENV.BATTLE_PARTICIPATION_SPREAD_MS || '260000',
);

validateBurstSchedule();

const thresholds = {
    'checks{operation:current}': ['rate==1'],
    'checks{operation:participate}': ['rate==1'],
    'checks{operation:submit}': ['rate==1'],
    'checks{operation:control_api}': ['rate==1'],
    battle_submission_schedule_missed: ['rate==0'],
};

if (!SUBMISSION_ONLY) {
    thresholds['checks{operation:first_result}'] = ['rate==1'];
    thresholds.battle_first_poll_ready = ['rate==1'];
    thresholds.battle_first_poll_grading_completed = ['rate==1'];
}

const firstPollReady = new Rate('battle_first_poll_ready');
const firstPollCompleted = new Rate('battle_first_poll_grading_completed');
const submissionScheduleMissed = new Rate('battle_submission_schedule_missed');
const submissionLateness = new Trend('battle_submission_lateness_ms', true);
const submissionAttempts = new Counter('battle_submission_attempts');
const submissionAccepted = new Counter('battle_submission_accepted');
const submissionConnectionAcquire = new Trend('battle_submission_connection_acquire_ms', true);
const submissionConnectionHold = new Trend('battle_submission_connection_hold_ms', true);
const submissionTransaction = new Trend('battle_submission_transaction_ms', true);
const submissionSqlCount = new Trend('battle_submission_sql_count');

const users = new SharedArray('battle S2 burst users', function () {
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
        scenario_id: 's2-burst',
        user_count: String(USER_COUNT),
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    scenarios: {
        burstSubmission: {
            executor: 'per-vu-iterations',
            exec: 'burstSubmission',
            vus: USER_COUNT,
            iterations: 1,
            maxDuration: SUBMISSION_ONLY ? '7m' : '12m',
        },
        controlApiProbe: {
            executor: 'constant-arrival-rate',
            exec: 'controlApiProbe',
            rate: 1,
            timeUnit: '1s',
            duration: SUBMISSION_ONLY ? '6m30s' : '11m',
            preAllocatedVUs: 2,
            maxVUs: 10,
        },
    },
    thresholds,
};

// 동시 제출 부하가 일반 조회 API에 미치는 영향을 비교하기 위해 고정 1 RPS로 호출합니다.
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

// 모든 VU가 current API를 동시에 호출하지 않도록 배틀 정보와 서버 시각은 한 번만 조회합니다.
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

export function burstSubmission(testContext) {
    const userIndex = exec.scenario.iterationInTest;
    const user = users[userIndex];
    if (!user) {
        exec.test.abort(`burst user index is out of range: ${userIndex}/${USER_COUNT}`);
    }
    const requestParams = params(user.access_token);

    const currentBattleId = testContext.battleId;
    const clockOffsetMs = testContext.clockOffsetMs;
    const opensAt = kstTime(testContext.battleDate, BATTLE_OPEN_TIME);
    const burstSubmissionAt = kstTime(
        testContext.battleDate,
        BATTLE_BURST_SUBMISSION_TIME,
    );
    const resultAt = kstTime(testContext.battleDate, BATTLE_RESULT_TIME);

    // 참여 API가 동시 제출 결과를 오염시키지 않도록 제출 30초 전까지 분산 완료합니다.
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

    // 모든 VU가 같은 서버 시각에 제출하여 설정한 사용자 수만큼의 burst를 만듭니다.
    sleepUntilServerTime(burstSubmissionAt, clockOffsetMs);
    const latenessMs = (Date.now() + clockOffsetMs) - burstSubmissionAt;
    submissionLateness.add(latenessMs);
    const missedSchedule = latenessMs > 2_000;
    submissionScheduleMissed.add(missedSchedule);
    if (missedSchedule) {
        return;
    }

    submissionAttempts.add(1);
    const submissionRequestId = `${__ENV.TEST_ID || 's2'}-${user.user_id}`;
    const submissionParams = tagged(requestParams, 'submit');
    submissionParams.headers = {
        ...submissionParams.headers,
        'X-Load-Test-Id': __ENV.TEST_ID || 's2',
        'X-Load-Test-Request-Id': submissionRequestId,
    };

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
        submissionParams,
    );
    const submissionOk = check(submissionResponse, {
        'submission is accepted': (response) => response.status === 202,
    }, { operation: 'submit' });
    if (submissionOk) {
        submissionAccepted.add(1);
        addTimingHeader(
            submissionConnectionAcquire,
            submissionResponse,
            'X-Cosmos-Db-Connection-Acquire-Ms',
        );
        addTimingHeader(
            submissionConnectionHold,
            submissionResponse,
            'X-Cosmos-Db-Connection-Hold-Ms',
        );
        addTimingHeader(
            submissionTransaction,
            submissionResponse,
            'X-Cosmos-Submission-Transaction-Ms',
        );
        addTimingHeader(
            submissionSqlCount,
            submissionResponse,
            'X-Cosmos-Submission-Sql-Count',
        );
    }

    // 채점 워커 경합을 제거한 제출 단독 실험에서는 결과 공개를 기다리지 않습니다.
    if (SUBMISSION_ONLY) {
        return;
    }

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

function numberEnv(name) {
    const value = Number(__ENV[name]);
    if (!Number.isFinite(value)) {
        throw new Error(`${name} must be a number`);
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
    if (!ALLOWED_USER_COUNTS.has(USER_COUNT)) {
        throw new Error('USER_COUNT must be one of 100, 200, 300, 500, 1000');
    }
    if (!Number.isFinite(PARTICIPATION_START_GRACE_MS)
            || PARTICIPATION_START_GRACE_MS < 0
            || !Number.isFinite(PARTICIPATION_SPREAD_MS)
            || PARTICIPATION_SPREAD_MS < 0) {
        throw new Error('participation timing values must be zero or positive numbers');
    }
    if (resultAt - openAt !== 10 * 60 * 1000
            || resultAt - deadlineAt !== 1000) {
        throw new Error(
            'S2 keeps a 10-minute battle: deadline at +09:59 and result at +10:00',
        );
    }
    if (participationWindowOverlapsBurst(openAt, burstSubmissionAt)) {
        throw new Error('participation window must finish before the submission burst');
    }
    if (burstSubmissionAt >= deadlineAt) {
        throw new Error('submission burst must run before the submission deadline');
    }
}

function participationWindowOverlapsBurst(openAt, burstSubmissionAt) {
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

function addTimingHeader(metric, response, headerName) {
    const matchingHeader = Object.keys(response.headers)
        .find((key) => key.toLowerCase() === headerName.toLowerCase());
    const value = Number(matchingHeader ? response.headers[matchingHeader] : Number.NaN);
    if (Number.isFinite(value)) {
        metric.add(value, { operation: 'submit', outcome: 'accepted' });
    }
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

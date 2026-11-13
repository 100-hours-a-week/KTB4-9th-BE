#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

LOCK_DIR="${TMPDIR:-/tmp}/cosmos-battle-e1-run-local.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo '다른 E1 실행 준비 작업이 이미 진행 중입니다.' >&2
  exit 1
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

export COMPOSE_FILE='compose.yml:docker-compose.loadtest.yml:docker-compose.observability.yml'
export TZ='Asia/Seoul'
export USER_COUNT='1000'
export TARGET_SUBMISSION_RPS="${1:-50}"

if [[ ! "$USER_COUNT" =~ ^(100|300|600|1000)$ ]]; then
  echo 'USER_COUNT는 100, 300, 600, 1000 중 하나여야 합니다.' >&2
  exit 1
fi

if [[ ! "$TARGET_SUBMISSION_RPS" =~ ^[0-9]+([.][0-9]+)?$ ]] \
  || ! awk -v rps="$TARGET_SUBMISSION_RPS" 'BEGIN { exit !(rps > 0) }'; then
  echo 'TARGET_SUBMISSION_RPS는 0보다 큰 숫자여야 합니다.' >&2
  exit 1
fi

if pgrep -f '[k]6 run.*battle-e1-capacity.js' >/dev/null \
  || pgrep -f '[z]sh -c.*battle-e1-capacity.js' >/dev/null \
  || pgrep -f '[k]6 run.*battle-s2-burst.js' >/dev/null \
  || pgrep -f '[z]sh -c.*battle-s2-burst.js' >/dev/null; then
  echo '이전 E1 또는 S2 k6 실행·예약 프로세스가 남아 있습니다.' >&2
  echo '기존 테스트가 끝난 뒤 다시 실행하세요.' >&2
  exit 1
fi

if [[ -z "${LOADTEST_FIXTURES_SECRET:-}" ]]; then
  export LOADTEST_FIXTURES_SECRET="$(
    docker inspect cosmos-backend-loadtest \
      --format '{{range .Config.Env}}{{println .}}{{end}}' 2>/dev/null |
      sed -n 's/^LOADTEST_FIXTURES_SECRET=//p' |
      head -n 1
  )"
fi

if [[ -z "${LOADTEST_FIXTURES_SECRET:-}" ]]; then
  echo '실행 중인 로컬 백엔드에서 LOADTEST_FIXTURES_SECRET를 찾지 못했습니다.' >&2
  exit 1
fi

NOW_EPOCH="$(date +%s)"
export OPEN_EPOCH="$(( ((NOW_EPOCH + 3 * 60 + 59) / 60) * 60 ))"
export BATTLE_OPEN_TIME="$(date -r "$OPEN_EPOCH" '+%H:%M:%S')"
export BATTLE_SUBMISSION_DEADLINE="$(date -r "$((OPEN_EPOCH + 599))" '+%H:%M:%S')"
export BATTLE_RESULT_TIME="$(date -r "$((OPEN_EPOCH + 600))" '+%H:%M:%S')"
export FIRST_SUBMISSION_OFFSET_SECONDS='300'
export LAST_SUBMISSION_LEAD_SECONDS='239'
export PARTICIPATION_START_OFFSET_SECONDS='10'
export PARTICIPATION_END_OFFSET_SECONDS='270'
SUBMISSION_DURATION_SECONDS="$(
  awk -v users="$USER_COUNT" -v rps="$TARGET_SUBMISSION_RPS" \
    'BEGIN { seconds = (users - 1) / rps; print int(seconds) + (seconds > int(seconds)) }'
)"
SUBMISSION_WINDOW_START="$(date -r "$((OPEN_EPOCH + FIRST_SUBMISSION_OFFSET_SECONDS))" '+%H:%M:%S')"
SUBMISSION_WINDOW_END="$(date -r "$((OPEN_EPOCH + FIRST_SUBMISSION_OFFSET_SECONDS + SUBMISSION_DURATION_SECONDS))" '+%H:%M:%S')"
PARTICIPATION_WINDOW_START="$(date -r "$((OPEN_EPOCH + PARTICIPATION_START_OFFSET_SECONDS))" '+%H:%M:%S')"
PARTICIPATION_WINDOW_END="$(date -r "$((OPEN_EPOCH + PARTICIPATION_END_OFFSET_SECONDS))" '+%H:%M:%S')"

printf '%s\n' \
  "배틀 시작=$BATTLE_OPEN_TIME" \
  "참여 준비 시작=$PARTICIPATION_WINDOW_START" \
  "참여 준비 종료=$PARTICIPATION_WINDOW_END" \
  "목표 제출 RPS=$TARGET_SUBMISSION_RPS" \
  "분산 제출 시작=$SUBMISSION_WINDOW_START" \
  "분산 제출 종료=$SUBMISSION_WINDOW_END" \
  "제출 마감=$BATTLE_SUBMISSION_DEADLINE" \
  "결과 공개=$BATTLE_RESULT_TIME"

docker rm -f cosmos-backend-loadtest >/dev/null 2>&1 || true

docker compose --env-file .env.local \
  up -d --force-recreate --no-deps backend-loadtest

until curl --fail --silent 'http://localhost:8080/actuator/health' >/dev/null; do
  echo '백엔드 시작 대기 중...'
  sleep 2
done

curl --fail --silent --show-error \
  -X POST \
  "http://localhost:8080/internal/load-test/battle/users?count=${USER_COUNT}" \
  -H "X-Load-Test-Secret: ${LOADTEST_FIXTURES_SECRET}" \
  -o loadtest/data/battle-users.json

jq -e --argjson count "$USER_COUNT" \
  'length == $count' \
  loadtest/data/battle-users.json >/dev/null

FIRST_TOKEN="$(jq -r '.[0].access_token' loadtest/data/battle-users.json)"
export BATTLE_ID="$(
  curl --fail --silent --show-error \
    'http://localhost:8080/daily-battles/current' \
    -H "Cookie: accessToken=${FIRST_TOKEN}" |
  jq -r '.data.current_battle_id'
)"

if [[ ! "$BATTLE_ID" =~ ^[0-9]+$ ]]; then
  echo "배틀 ID 확인 실패: ${BATTLE_ID}" >&2
  exit 1
fi

./loadtest/battle-reset-local.sh

export BASE_URL='http://localhost:8080'
export TOKEN_FILE='./data/battle-users.json'
TEST_RPS_ID="${TARGET_SUBMISSION_RPS//./p}"
export TEST_ID="e1-${USER_COUNT}-rps${TEST_RPS_ID}-$(date +%Y%m%d-%H%M%S)"
export K6_PROMETHEUS_RW_SERVER_URL='http://localhost:9090/api/v1/write'
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),min,max,avg'
unset K6_PROMETHEUS_RW_USERNAME
unset K6_PROMETHEUS_RW_PASSWORD

K6_ENV_ARGS=(
  -e "USER_COUNT=$USER_COUNT"
  -e "TARGET_SUBMISSION_RPS=$TARGET_SUBMISSION_RPS"
  -e "BASE_URL=$BASE_URL"
  -e "TOKEN_FILE=$TOKEN_FILE"
  -e "BATTLE_ID=$BATTLE_ID"
  -e "BATTLE_OPEN_TIME=$BATTLE_OPEN_TIME"
  -e "BATTLE_SUBMISSION_DEADLINE=$BATTLE_SUBMISSION_DEADLINE"
  -e "BATTLE_RESULT_TIME=$BATTLE_RESULT_TIME"
  -e "FIRST_SUBMISSION_OFFSET_SECONDS=$FIRST_SUBMISSION_OFFSET_SECONDS"
  -e "LAST_SUBMISSION_LEAD_SECONDS=$LAST_SUBMISSION_LEAD_SECONDS"
  -e "PARTICIPATION_START_OFFSET_SECONDS=$PARTICIPATION_START_OFFSET_SECONDS"
  -e "PARTICIPATION_END_OFFSET_SECONDS=$PARTICIPATION_END_OFFSET_SECONDS"
)

k6 inspect "${K6_ENV_ARGS[@]}" loadtest/battle-e1-capacity.js >/dev/null

K6_START_EPOCH="$((OPEN_EPOCH - 30))"
WAIT_SECONDS="$((K6_START_EPOCH - $(date +%s)))"

printf '%s\n' \
  "TEST_ID=$TEST_ID" \
  "USER_COUNT=$USER_COUNT" \
  "TARGET_SUBMISSION_RPS=$TARGET_SUBMISSION_RPS" \
  "BATTLE_ID=$BATTLE_ID" \
  "k6 자동 시작=$(date -r "$K6_START_EPOCH")" \
  "남은 시간=${WAIT_SECONDS}초" \
  'Grafana=http://localhost:3000/d/cosmos-battle-e1-capacity'

if (( WAIT_SECONDS <= 0 )); then
  echo '자동 시작 시각을 놓쳤습니다. 스크립트를 다시 실행해 주세요.' >&2
  exit 1
fi

sleep "$WAIT_SECONDS"

K6_COMMAND=(
  k6 run
  "${K6_ENV_ARGS[@]}"
  --tag "testid=$TEST_ID"
  -o experimental-prometheus-rw
  loadtest/battle-e1-capacity.js
)

if command -v caffeinate >/dev/null 2>&1; then
  rmdir "$LOCK_DIR" 2>/dev/null || true
  exec caffeinate -i "${K6_COMMAND[@]}"
fi

rmdir "$LOCK_DIR" 2>/dev/null || true
exec "${K6_COMMAND[@]}"

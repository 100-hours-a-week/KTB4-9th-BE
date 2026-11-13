#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

LOCK_DIR="${TMPDIR:-/tmp}/cosmos-battle-s2-run-local.lock"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
  echo '다른 S2 실행 준비 작업이 이미 진행 중입니다.' >&2
  exit 1
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

export COMPOSE_FILE='compose.yml:docker-compose.loadtest.yml:docker-compose.observability.yml'
export TZ='Asia/Seoul'
export USER_COUNT="${1:-100}"
export S2_MODE="${2:-with-worker}"

if [[ ! "$USER_COUNT" =~ ^(100|200|300|500|1000)$ ]]; then
  echo '동시 제출 인원은 100, 200, 300, 500, 1000 중 하나여야 합니다.' >&2
  exit 1
fi

case "$S2_MODE" in
  with-worker)
    export BATTLE_GRADING_WORKER_ENABLED='true'
    export SUBMISSION_ONLY='false'
    TEST_ID_PREFIX="s2-${USER_COUNT}"
    ;;
  no-worker)
    export BATTLE_GRADING_WORKER_ENABLED='false'
    export SUBMISSION_ONLY='true'
    TEST_ID_PREFIX="s2-noworker-${USER_COUNT}"
    ;;
  *)
    echo '실행 모드는 with-worker 또는 no-worker여야 합니다.' >&2
    exit 1
    ;;
esac

if pgrep -f '[k]6 run.*battle-s2-burst.js' >/dev/null \
  || pgrep -f '[z]sh -c.*battle-s2-burst.js' >/dev/null \
  || pgrep -f '[k]6 run.*battle-e1-capacity.js' >/dev/null \
  || pgrep -f '[z]sh -c.*battle-e1-capacity.js' >/dev/null; then
  echo '이전 E1 또는 S2 k6 실행·예약 프로세스가 남아 있습니다.' >&2
  echo "기존 테스트가 끝난 뒤 다시 실행하세요." >&2
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
  # 로컬 전용 테스트 API를 호출하기 위한 실행별 임시 값
  export LOADTEST_FIXTURES_SECRET="local-s2-$(date +%s)"
fi

NOW_EPOCH="$(date +%s)"
export OPEN_EPOCH="$(( ((NOW_EPOCH + 3 * 60 + 59) / 60) * 60 ))"
export BATTLE_OPEN_TIME="$(date -r "$OPEN_EPOCH" '+%H:%M:%S')"
export BATTLE_BURST_SUBMISSION_TIME="$(date -r "$((OPEN_EPOCH + 300))" '+%H:%M:%S')"
export BATTLE_SUBMISSION_DEADLINE="$(date -r "$((OPEN_EPOCH + 599))" '+%H:%M:%S')"
export BATTLE_RESULT_TIME="$(date -r "$((OPEN_EPOCH + 600))" '+%H:%M:%S')"
export BATTLE_PARTICIPATION_START_GRACE_MS='10000'
export BATTLE_PARTICIPATION_SPREAD_MS='260000'

printf '%s\n' \
  "동시 제출 인원=$USER_COUNT" \
  "실행 모드=$S2_MODE" \
  "채점 워커 활성화=$BATTLE_GRADING_WORKER_ENABLED" \
  "배틀 시작=$BATTLE_OPEN_TIME" \
  "참여 준비 구간=$(date -r "$((OPEN_EPOCH + 10))" '+%H:%M:%S') ~ $(date -r "$((OPEN_EPOCH + 270))" '+%H:%M:%S')" \
  "동시 제출=$BATTLE_BURST_SUBMISSION_TIME" \
  "제출 마감=$BATTLE_SUBMISSION_DEADLINE" \
  "결과 공개=$BATTLE_RESULT_TIME"

docker compose --env-file .env.local up -d rabbitmq prometheus grafana

until [[ "$(docker inspect --format '{{.State.Health.Status}}' cosmos-rabbitmq-loadtest 2>/dev/null || true)" == 'healthy' ]]; do
  if [[ "$(docker inspect --format '{{.State.Running}}' cosmos-rabbitmq-loadtest 2>/dev/null || true)" != 'true' ]]; then
    echo 'RabbitMQ 컨테이너가 시작 중 종료됐습니다. 마지막 로그를 출력합니다.' >&2
    docker logs --tail 120 cosmos-rabbitmq-loadtest >&2 || true
    exit 1
  fi

  echo 'RabbitMQ 시작 대기 중...'
  sleep 2
done

until curl --fail --silent 'http://localhost:9090/-/ready' >/dev/null; do
  if [[ "$(docker inspect --format '{{.State.Running}}' ktb4-9th-be-prometheus-1 2>/dev/null || true)" != 'true' ]]; then
    echo 'Prometheus 컨테이너가 시작 중 종료됐습니다. 마지막 로그를 출력합니다.' >&2
    docker logs --tail 120 ktb4-9th-be-prometheus-1 >&2 || true
    exit 1
  fi

  echo 'Prometheus 시작 대기 중...'
  sleep 2
done

docker rm -f cosmos-backend-loadtest cosmos-grading-worker-loadtest >/dev/null 2>&1 || true

docker compose --env-file .env.local \
  up -d --force-recreate --no-deps backend-loadtest

until curl --fail --silent 'http://localhost:8080/actuator/health' >/dev/null; do
  if [[ "$(docker inspect --format '{{.State.Running}}' cosmos-backend-loadtest 2>/dev/null || true)" != 'true' ]]; then
    echo '백엔드 컨테이너가 시작 중 종료됐습니다. 마지막 로그를 출력합니다.' >&2
    docker logs --tail 120 cosmos-backend-loadtest >&2 || true
    exit 1
  fi

  echo '백엔드 시작 대기 중...'
  sleep 2
done

# Consumer를 시작하기 전에 이전 실행의 테스트 메시지를 제거합니다.
for queue_name in \
  battle.grading.queue \
  battle.grading.retry.queue \
  battle.grading.failure.queue; do
  docker exec cosmos-rabbitmq-loadtest \
    rabbitmqctl -q -p / purge_queue "$queue_name" >/dev/null 2>&1 || true
done

if [[ "$S2_MODE" == 'with-worker' ]]; then
  docker compose --env-file .env.local \
    up -d --force-recreate --no-deps grading-worker-loadtest

  until docker logs cosmos-grading-worker-loadtest 2>&1 | grep -q 'Started CosmosBackendApplication'; do
    if [[ "$(docker inspect --format '{{.State.Running}}' cosmos-grading-worker-loadtest 2>/dev/null || true)" != 'true' ]]; then
      echo '채점 Worker 컨테이너가 시작 중 종료됐습니다. 마지막 로그를 출력합니다.' >&2
      docker logs --tail 120 cosmos-grading-worker-loadtest >&2 || true
      exit 1
    fi

    echo '채점 Worker 시작 대기 중...'
    sleep 2
  done

  echo "RabbitMQ 채점 Worker가 준비됐습니다. prefetch=${RABBITMQ_CONSUMER_PREFETCH:-1}, concurrency=${RABBITMQ_CONSUMER_CONCURRENCY:-1}"
fi

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
export TEST_ID="${TEST_ID_PREFIX}-$(date +%Y%m%d-%H%M%S)"
export K6_PROMETHEUS_RW_SERVER_URL='http://localhost:9090/api/v1/write'
export K6_PROMETHEUS_RW_TREND_STATS='p(95),p(99),min,max,avg'
unset K6_PROMETHEUS_RW_USERNAME
unset K6_PROMETHEUS_RW_PASSWORD

K6_ENV_ARGS=(
  -e "USER_COUNT=$USER_COUNT"
  -e "BASE_URL=$BASE_URL"
  -e "TOKEN_FILE=$TOKEN_FILE"
  -e "BATTLE_ID=$BATTLE_ID"
  -e "BATTLE_OPEN_TIME=$BATTLE_OPEN_TIME"
  -e "BATTLE_BURST_SUBMISSION_TIME=$BATTLE_BURST_SUBMISSION_TIME"
  -e "BATTLE_SUBMISSION_DEADLINE=$BATTLE_SUBMISSION_DEADLINE"
  -e "BATTLE_RESULT_TIME=$BATTLE_RESULT_TIME"
  -e "BATTLE_PARTICIPATION_START_GRACE_MS=$BATTLE_PARTICIPATION_START_GRACE_MS"
  -e "BATTLE_PARTICIPATION_SPREAD_MS=$BATTLE_PARTICIPATION_SPREAD_MS"
  -e "SUBMISSION_ONLY=$SUBMISSION_ONLY"
)

k6 inspect "${K6_ENV_ARGS[@]}" loadtest/battle-s2-burst.js >/dev/null

K6_START_EPOCH="$((OPEN_EPOCH - 30))"
WAIT_SECONDS="$((K6_START_EPOCH - $(date +%s)))"

printf '%s\n' \
  "TEST_ID=$TEST_ID" \
  "BATTLE_ID=$BATTLE_ID" \
  "k6 자동 시작=$(date -r "$K6_START_EPOCH")" \
  "남은 시간=${WAIT_SECONDS}초" \
  'Grafana=http://localhost:3000/d/cosmos-battle-s2' \
  'RabbitMQ=http://localhost:15673/#/queues'

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
  loadtest/battle-s2-burst.js
)

if command -v caffeinate >/dev/null 2>&1; then
  caffeinate -i "${K6_COMMAND[@]}"
  exit $?
fi

"${K6_COMMAND[@]}"

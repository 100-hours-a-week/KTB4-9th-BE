#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

required_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "필수 환경변수가 없습니다: ${name}" >&2
    exit 1
  fi
}

for name in \
  TEST_ID BATTLE_ID BASE_URL TOKEN_FILE \
  BATTLE_OPEN_TIME BATTLE_BURST_SUBMISSION_TIME \
  BATTLE_SUBMISSION_DEADLINE \
  K6_PROMETHEUS_RW_SERVER_URL; do
  required_env "$name"
done

if [[ ! "$TEST_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo 'TEST_ID에는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.' >&2
  exit 1
fi
if [[ ! "$BATTLE_ID" =~ ^[0-9]+$ ]]; then
  echo 'BATTLE_ID는 숫자여야 합니다.' >&2
  exit 1
fi
if [[ ! -f "$TOKEN_FILE" && ! -f "$SCRIPT_DIR/${TOKEN_FILE#./}" ]]; then
  echo "사용자 토큰 파일이 없습니다: ${TOKEN_FILE}" >&2
  exit 1
fi

for command in docker k6 tar; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "필요한 명령어가 설치되어 있지 않습니다: ${command}" >&2
    exit 1
  fi
done

BACKEND_CONTAINER_NAME="${BACKEND_CONTAINER_NAME:-cosmos-backend-loadtest}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-cosmos-mysql}"
for container in "$BACKEND_CONTAINER_NAME" "$MYSQL_CONTAINER"; do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)" != 'true' ]]; then
    echo "실행 중인 컨테이너를 찾을 수 없습니다: ${container}" >&2
    exit 1
  fi
done

submission_id_extra="$(
  docker exec -i "$MYSQL_CONTAINER" sh -lc \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot "$MYSQL_DATABASE"' \
    <<< "
      SELECT EXTRA
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'battle_submissions'
        AND COLUMN_NAME = 'battle_submission_id';
    "
)"
if [[ "$submission_id_extra" != *auto_increment* ]]; then
  echo 'battle_submissions.battle_submission_id에 AUTO_INCREMENT가 없습니다.' >&2
  echo '먼저 ./loadtest/battle-schema-fix-local.sh 를 실행하세요.' >&2
  exit 1
fi

FAULT_SCRIPT="$SCRIPT_DIR/battle-r1-fault-injection-remote.sh"
RESULT_ROOT="${R1_RESULT_ROOT:-$SCRIPT_DIR/results}"
RESULT_DIR="$RESULT_ROOT/$TEST_ID"
FAULT_ARCHIVE="$RESULT_DIR/fault-results.tar.gz"
FAULT_LOG="$RESULT_DIR/fault-injection.log"
K6_LOG="$RESULT_DIR/k6.log"
RUN_SUMMARY="$RESULT_DIR/run-summary.tsv"

if [[ -e "$RESULT_DIR" ]]; then
  echo "동일한 TEST_ID 결과 디렉터리가 이미 존재합니다: ${RESULT_DIR}" >&2
  exit 1
fi
mkdir -p "$RESULT_DIR"

printf 'key\tvalue\n' > "$RUN_SUMMARY"
printf 'test_id\t%s\nbattle_id\t%s\nstarted_at\t%s\nexecution_mode\tlocal\n' \
  "$TEST_ID" "$BATTLE_ID" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" >> "$RUN_SUMMARY"

echo "[R1] 로컬 장애 감시를 시작합니다. 결과: ${RESULT_DIR}"
TEST_ID="$TEST_ID" \
BATTLE_ID="$BATTLE_ID" \
BACKEND_CONTAINER_NAME="$BACKEND_CONTAINER_NAME" \
MYSQL_CONTAINER="$MYSQL_CONTAINER" \
EXPECTED_USERS="${EXPECTED_USERS:-100}" \
EXPECTED_ANSWERS_PER_USER="${EXPECTED_ANSWERS_PER_USER:-3}" \
DOWNTIME_SECONDS="${DOWNTIME_SECONDS:-5}" \
TRIGGER_TIMEOUT_SECONDS="${TRIGGER_TIMEOUT_SECONDS:-300}" \
RECOVERY_TIMEOUT_SECONDS="${RECOVERY_TIMEOUT_SECONDS:-300}" \
bash "$FAULT_SCRIPT" > "$FAULT_ARCHIVE" 2> "$FAULT_LOG" &
FAULT_PID=$!

cleanup() {
  local exit_code=$?
  if kill -0 "$FAULT_PID" >/dev/null 2>&1; then
    kill "$FAULT_PID" >/dev/null 2>&1 || true
    wait "$FAULT_PID" >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}
trap cleanup INT TERM

set +e
k6 run \
  --quiet \
  --tag "testid=$TEST_ID" \
  -o experimental-prometheus-rw \
  "$SCRIPT_DIR/battle-r1-recovery.js" \
  2>&1 | tee "$K6_LOG"
K6_EXIT_CODE=${PIPESTATUS[0]}
set -e

if (( K6_EXIT_CODE != 0 )) && kill -0 "$FAULT_PID" >/dev/null 2>&1; then
  kill "$FAULT_PID" >/dev/null 2>&1 || true
fi

set +e
wait "$FAULT_PID"
FAULT_EXIT_CODE=$?
set -e

printf 'k6_exit_code\t%s\nfault_injection_exit_code\t%s\nfinished_at\t%s\n' \
  "$K6_EXIT_CODE" "$FAULT_EXIT_CODE" "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" >> "$RUN_SUMMARY"

if (( FAULT_EXIT_CODE == 0 )); then
  tar -xzf "$FAULT_ARCHIVE" -C "$RESULT_DIR"
  rm "$FAULT_ARCHIVE"
else
  echo "[R1] 로컬 장애 주입 작업이 실패했습니다. 로그를 확인하세요: ${FAULT_LOG}" >&2
fi

if (( K6_EXIT_CODE != 0 || FAULT_EXIT_CODE != 0 )); then
  echo "[R1] 테스트 실패: k6=${K6_EXIT_CODE}, fault-injection=${FAULT_EXIT_CODE}" >&2
  exit 1
fi

echo "[R1] 테스트 완료: ${RESULT_DIR}"

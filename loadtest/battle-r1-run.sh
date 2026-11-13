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
  K6_PROMETHEUS_RW_SERVER_URL K6_PROMETHEUS_RW_USERNAME \
  K6_PROMETHEUS_RW_PASSWORD SSH_HOST SSH_KEY; do
  required_env "$name"
done

if [[ ! "$TEST_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo 'TEST_ID에는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.' >&2
  exit 1
fi
if [[ ! -f "$TOKEN_FILE" && ! -f "$SCRIPT_DIR/${TOKEN_FILE#./}" ]]; then
  echo "사용자 토큰 파일이 없습니다: ${TOKEN_FILE}" >&2
  exit 1
fi
if [[ ! -f "$SSH_KEY" ]]; then
  echo "SSH 키 파일이 없습니다: ${SSH_KEY}" >&2
  exit 1
fi

for command in ssh k6 tar; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "필요한 명령어가 설치되어 있지 않습니다: ${command}" >&2
    exit 1
  fi
done

REMOTE_SCRIPT="$SCRIPT_DIR/battle-r1-fault-injection-remote.sh"
RESULT_ROOT="${R1_RESULT_ROOT:-$SCRIPT_DIR/results}"
RESULT_DIR="$RESULT_ROOT/$TEST_ID"
REMOTE_ARCHIVE="$RESULT_DIR/remote-results.tar.gz"
REMOTE_LOG="$RESULT_DIR/fault-injection.log"
K6_LOG="$RESULT_DIR/k6.log"
RUN_SUMMARY="$RESULT_DIR/run-summary.tsv"

if [[ -e "$RESULT_DIR" ]]; then
  echo "동일한 TEST_ID 결과 디렉터리가 이미 존재합니다: ${RESULT_DIR}" >&2
  exit 1
fi
mkdir -p "$RESULT_DIR"

printf 'key\tvalue\n' > "$RUN_SUMMARY"
printf 'test_id\t%s\nbattle_id\t%s\nstarted_at\t%s\n' \
  "$TEST_ID" "$BATTLE_ID" "$(date -u '+%Y-%m-%dT%H:%M:%S.%3NZ')" >> "$RUN_SUMMARY"

remote_command="TEST_ID=$(printf '%q' "$TEST_ID")"
remote_command+=" BATTLE_ID=$(printf '%q' "$BATTLE_ID")"
remote_command+=" EXPECTED_USERS=$(printf '%q' "${EXPECTED_USERS:-100}")"
remote_command+=" EXPECTED_ANSWERS_PER_USER=$(printf '%q' "${EXPECTED_ANSWERS_PER_USER:-3}")"
remote_command+=" DOWNTIME_SECONDS=$(printf '%q' "${DOWNTIME_SECONDS:-5}")"
remote_command+=" TRIGGER_TIMEOUT_SECONDS=$(printf '%q' "${TRIGGER_TIMEOUT_SECONDS:-300}")"
remote_command+=" RECOVERY_TIMEOUT_SECONDS=$(printf '%q' "${RECOVERY_TIMEOUT_SECONDS:-300}")"
remote_command+=" bash -s"

echo "[R1] 원격 장애 감시를 시작합니다. 결과: ${RESULT_DIR}"
ssh \
  -o BatchMode=yes \
  -o ServerAliveInterval=15 \
  -o ServerAliveCountMax=4 \
  -i "$SSH_KEY" \
  "$SSH_HOST" \
  "$remote_command" \
  < "$REMOTE_SCRIPT" \
  > "$REMOTE_ARCHIVE" \
  2> "$REMOTE_LOG" &
FAULT_PID=$!

cleanup() {
  local exit_code=$?
  if kill -0 "$FAULT_PID" >/dev/null 2>&1; then
    echo '[R1] 로컬 실행이 중단되어 원격 감시 작업의 종료를 기다립니다.' >&2
    wait "$FAULT_PID" || true
  fi
  exit "$exit_code"
}
trap cleanup INT TERM

set +e
k6 run \
  --tag "testid=$TEST_ID" \
  -o experimental-prometheus-rw \
  "$SCRIPT_DIR/battle-r1-recovery.js" \
  2>&1 | tee "$K6_LOG"
K6_EXIT_CODE=${PIPESTATUS[0]}
set -e

set +e
wait "$FAULT_PID"
FAULT_EXIT_CODE=$?
set -e

printf 'k6_exit_code\t%s\nfault_injection_exit_code\t%s\nfinished_at\t%s\n' \
  "$K6_EXIT_CODE" "$FAULT_EXIT_CODE" "$(date -u '+%Y-%m-%dT%H:%M:%S.%3NZ')" >> "$RUN_SUMMARY"

if (( FAULT_EXIT_CODE == 0 )); then
  tar -xzf "$REMOTE_ARCHIVE" -C "$RESULT_DIR"
  rm "$REMOTE_ARCHIVE"
else
  echo "[R1] 장애 주입 작업이 실패했습니다. 로그를 확인하세요: ${REMOTE_LOG}" >&2
fi

if (( K6_EXIT_CODE != 0 || FAULT_EXIT_CODE != 0 )); then
  echo "[R1] 테스트 실패: k6=${K6_EXIT_CODE}, fault-injection=${FAULT_EXIT_CODE}" >&2
  exit 1
fi

echo "[R1] 테스트 완료: ${RESULT_DIR}"

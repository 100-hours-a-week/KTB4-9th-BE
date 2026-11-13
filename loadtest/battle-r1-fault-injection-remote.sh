#!/usr/bin/env bash

set -Eeuo pipefail

required_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "필수 환경변수가 없습니다: ${name}" >&2
    exit 1
  fi
}

required_env TEST_ID
required_env BATTLE_ID

if [[ ! "$TEST_ID" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo 'TEST_ID에는 영문, 숫자, 점, 밑줄, 하이픈만 사용할 수 있습니다.' >&2
  exit 1
fi
if [[ ! "$BATTLE_ID" =~ ^[0-9]+$ ]]; then
  echo 'BATTLE_ID는 숫자여야 합니다.' >&2
  exit 1
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-cosmos-mysql}"
EXPECTED_USERS="${EXPECTED_USERS:-100}"
EXPECTED_ANSWERS_PER_USER="${EXPECTED_ANSWERS_PER_USER:-3}"
DOWNTIME_SECONDS="${DOWNTIME_SECONDS:-5}"
TRIGGER_TIMEOUT_SECONDS="${TRIGGER_TIMEOUT_SECONDS:-300}"
RECOVERY_TIMEOUT_SECONDS="${RECOVERY_TIMEOUT_SECONDS:-300}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-0.05}"
FREEZE_ARM_ACCEPTED_COUNT="${FREEZE_ARM_ACCEPTED_COUNT:-90}"
FREEZE_PULSE_SECONDS="${FREEZE_PULSE_SECONDS:-0.01}"
REMOTE_RESULT_DIR="$(mktemp -d "/tmp/cosmos-r1-${TEST_ID}.XXXXXX")"
TIMELINE_FILE="$REMOTE_RESULT_DIR/timeline.tsv"
QUEUE_FILE="$REMOTE_RESULT_DIR/queue-snapshots.tsv"
BACKEND_CONTAINER=''
SERVER_KILLED_MS=0
SERVER_RESTART_REQUESTED_MS=0
HEALTH_READY_MS=0
WORKERS_ACTIVE_MS=0
FIRST_RECLAIM_MS=0
FIRST_COMPLETED_MS=0
ALL_COMPLETED_MS=0

log() {
  printf '[R1][%s] %s\n' "$(now_iso)" "$*" >&2
}

now_ms() {
  if command -v perl >/dev/null 2>&1; then
    perl -MTime::HiRes=time -e 'printf "%.0f\n", time() * 1000'
    return
  fi
  printf '%s000\n' "$(date '+%s')"
}

now_iso() {
  if command -v perl >/dev/null 2>&1; then
    perl -MPOSIX=strftime -MTime::HiRes=time -e '
      $time = time();
      printf "%s.%03dZ\n",
        strftime("%Y-%m-%dT%H:%M:%S", gmtime($time)),
        int(($time - int($time)) * 1000);
    '
    return
  fi
  date -u '+%Y-%m-%dT%H:%M:%SZ'
}

mysql_query() {
  docker exec -i "$MYSQL_CONTAINER" sh -lc \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw -uroot "$MYSQL_DATABASE"' <<< "$1"
}

mysql_scalar() {
  docker exec -i "$MYSQL_CONTAINER" sh -lc \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot "$MYSQL_DATABASE"' <<< "$1"
}

append_event() {
  local event="$1"
  local epoch_ms="$2"
  local detail="${3:-}"
  printf '%s\t%s\t%s\t%s\n' "$event" "$epoch_ms" "$(now_iso)" "$detail" >> "$TIMELINE_FILE"
}

queue_counts() {
  mysql_scalar "
    SELECT CONCAT_WS(
      '\t',
      COALESCE(SUM(participation_status IN ('SUBMITTED', 'GRADING', 'GRADING_COMPLETED', 'GRADING_FAILED')), 0),
      COALESCE(SUM(participation_status = 'SUBMITTED'), 0),
      COALESCE(SUM(participation_status = 'GRADING'), 0),
      COALESCE(SUM(participation_status = 'GRADING_COMPLETED'), 0),
      COALESCE(SUM(participation_status = 'GRADING_FAILED'), 0),
      COALESCE(SUM(participation_status = 'IN_PROGRESS'), 0),
      COALESCE(SUM(participation_status = 'TIME_OVER'), 0)
    )
    FROM battle_participations
    WHERE daily_battle_id = ${BATTLE_ID};
  "
}

snapshot_queue() {
  local phase="$1"
  local counts
  counts="$(queue_counts)"
  printf '%s\t%s\t%s\n' "$phase" "$(now_iso)" "$counts" >> "$QUEUE_FILE"
}

snapshot_participations() {
  local phase="$1"
  mysql_query "
    SELECT
      participation_id,
      user_id,
      participation_status,
      submitted_at,
      lease_expires_at,
      HEX(lease_token) AS lease_token_hex,
      retry_count,
      lease_retry_count,
      all_correct
    FROM battle_participations
    WHERE daily_battle_id = ${BATTLE_ID}
    ORDER BY participation_id;
  " > "$REMOTE_RESULT_DIR/participations-${phase}.tsv"
}

snapshot_grading_leases() {
  local phase="$1"
  mysql_query "
    SELECT
      participation_id,
      user_id,
      participation_status,
      submitted_at,
      lease_expires_at,
      HEX(lease_token) AS lease_token_hex,
      retry_count,
      lease_retry_count
    FROM battle_participations
    WHERE daily_battle_id = ${BATTLE_ID}
      AND (participation_status = 'GRADING' OR lease_retry_count > 0)
    ORDER BY participation_id;
  " > "$REMOTE_RESULT_DIR/leases-${phase}.tsv"
}

ensure_backend_running() {
  if [[ -n "$BACKEND_CONTAINER" ]] \
      && docker inspect "$BACKEND_CONTAINER" >/dev/null 2>&1 \
      && [[ "$(docker inspect -f '{{.State.Paused}}' "$BACKEND_CONTAINER")" == 'true' ]]; then
    log "안전장치: ${BACKEND_CONTAINER} 컨테이너의 동결을 해제합니다."
    docker unpause "$BACKEND_CONTAINER" >/dev/null
  fi
  if [[ -n "$BACKEND_CONTAINER" ]] \
      && docker inspect "$BACKEND_CONTAINER" >/dev/null 2>&1 \
      && [[ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER")" != 'true' ]]; then
    log "안전장치: ${BACKEND_CONTAINER} 컨테이너를 다시 시작합니다."
    docker start "$BACKEND_CONTAINER" >/dev/null
  fi
}

cleanup() {
  local exit_code=$?
  ensure_backend_running || true
  if (( exit_code != 0 )); then
    log "장애 주입 스크립트가 실패했습니다. 임시 결과: ${REMOTE_RESULT_DIR}"
  fi
}
trap cleanup EXIT

printf 'event\tepoch_ms\tutc_time\tdetail\n' > "$TIMELINE_FILE"
printf 'phase\tutc_time\taccepted_total\tsubmitted\tgrading\tcompleted\tfailed\tin_progress\ttime_over\n' > "$QUEUE_FILE"
printf 'key\tvalue\n' > "$REMOTE_RESULT_DIR/test-metadata.tsv"
printf 'test_id\t%s\nbattle_id\t%s\nexpected_users\t%s\nexpected_answers_per_user\t%s\ndowntime_seconds\t%s\n' \
  "$TEST_ID" "$BATTLE_ID" "$EXPECTED_USERS" "$EXPECTED_ANSWERS_PER_USER" "$DOWNTIME_SECONDS" \
  >> "$REMOTE_RESULT_DIR/test-metadata.tsv"

backend_containers=()
if [[ -n "${BACKEND_CONTAINER_NAME:-}" ]]; then
  if [[ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER_NAME" 2>/dev/null || true)" == 'true' ]]; then
    backend_containers+=("$BACKEND_CONTAINER_NAME")
  fi
else
  while IFS= read -r container_name; do
    [[ -n "$container_name" ]] && backend_containers+=("$container_name")
  done < <(docker ps --filter 'name=cosmos-backend-' --format '{{.Names}}' | sort)
fi
if (( ${#backend_containers[@]} != 1 )); then
  echo "실행 중인 백엔드 컨테이너가 정확히 1개여야 합니다. 현재: ${#backend_containers[@]}개" >&2
  printf '%s\n' "${backend_containers[@]:-}" >&2
  exit 1
fi
BACKEND_CONTAINER="${backend_containers[0]}"
BACKEND_IMAGE="$(docker inspect -f '{{.Config.Image}}' "$BACKEND_CONTAINER")"
BACKEND_PORT="$(docker port "$BACKEND_CONTAINER" 8080/tcp | awk -F: 'NR == 1 { print $NF }')"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:${BACKEND_PORT}/actuator/health}"
METRICS_URL="${METRICS_URL:-http://127.0.0.1:${BACKEND_PORT}/actuator/prometheus}"
printf 'backend_container\t%s\nbackend_image\t%s\nhealth_url\t%s\n' \
  "$BACKEND_CONTAINER" "$BACKEND_IMAGE" "$HEALTH_URL" >> "$REMOTE_RESULT_DIR/test-metadata.tsv"
printf 'metrics_url\t%s\n' "$METRICS_URL" >> "$REMOTE_RESULT_DIR/test-metadata.tsv"

append_event watcher_started "$(now_ms)" "container=${BACKEND_CONTAINER}"
log "100건 접수 + GRADING 1건 이상 + SUBMITTED 1건 이상 조건을 기다립니다."

trigger_deadline=$(( $(date '+%s') + TRIGGER_TIMEOUT_SECONDS ))
BEFORE_COMPLETED=0
while true; do
  IFS=$'\t' read -r accepted submitted grading completed failed in_progress time_over <<< "$(queue_counts)"
  accepted="${accepted:-0}"
  submitted="${submitted:-0}"
  grading="${grading:-0}"
  completed="${completed:-0}"

  if (( accepted >= FREEZE_ARM_ACCEPTED_COUNT )); then
    # 로컬 채점이 너무 빨라 100건이 모두 접수된 시점에 GRADING이 사라지는 경쟁 조건을 막습니다.
    # 마지막 접수 구간에서 JVM을 짧게 동결·해제하여 100건 접수와 실제 Lease 선점이 함께 관측된 순간을 잡습니다.
    docker pause "$BACKEND_CONTAINER" >/dev/null

    IFS=$'\t' read -r accepted submitted grading completed failed in_progress time_over <<< "$(queue_counts)"
    if (( accepted == EXPECTED_USERS && grading >= 1 && submitted >= 1 )); then
      BEFORE_COMPLETED="$completed"
      break
    fi

    docker unpause "$BACKEND_CONTAINER" >/dev/null
    sleep "$FREEZE_PULSE_SECONDS"
  fi
  if (( $(date '+%s') >= trigger_deadline )); then
    snapshot_queue trigger_timeout
    echo "장애 주입 조건을 ${TRIGGER_TIMEOUT_SECONDS}초 안에 충족하지 못했습니다." >&2
    exit 1
  fi
  sleep "$POLL_INTERVAL_SECONDS"
done

append_event failure_condition_met "$(now_ms)" \
  "accepted=${accepted},submitted=${submitted},grading=${grading},completed=${completed}"

# pause 상태에서 SIGKILL을 먼저 대기시킨 뒤 unpause하여 워커가 결과를 저장할 틈 없이 종료합니다.
log "장애 조건 충족 및 워커 동결 완료: ${BACKEND_CONTAINER}에 SIGKILL을 보냅니다."
docker kill --signal=KILL "$BACKEND_CONTAINER" >/dev/null &
DOCKER_KILL_PID=$!
sleep 0.1
docker unpause "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
wait "$DOCKER_KILL_PID"
SERVER_KILLED_MS="$(now_ms)"
append_event server_killed "$SERVER_KILLED_MS" "container=${BACKEND_CONTAINER}"
snapshot_queue during_outage
snapshot_participations after-kill
snapshot_grading_leases after-kill

sleep "$DOWNTIME_SECONDS"
SERVER_RESTART_REQUESTED_MS="$(now_ms)"
append_event server_restart_requested "$SERVER_RESTART_REQUESTED_MS" "container=${BACKEND_CONTAINER}"
docker start "$BACKEND_CONTAINER" >/dev/null

health_deadline=$(( $(date '+%s') + RECOVERY_TIMEOUT_SECONDS ))
until curl --fail --silent --show-error "$HEALTH_URL" >/dev/null 2>&1; do
  if (( $(date '+%s') >= health_deadline )); then
    echo "백엔드 헬스 체크가 ${RECOVERY_TIMEOUT_SECONDS}초 안에 복구되지 않았습니다: ${HEALTH_URL}" >&2
    exit 1
  fi
  sleep 0.2
done
HEALTH_READY_MS="$(now_ms)"
append_event health_check_ready "$HEALTH_READY_MS" "$HEALTH_URL"
snapshot_queue after_health_ready

worker_deadline=$(( $(date '+%s') + RECOVERY_TIMEOUT_SECONDS ))
while true; do
  active_workers="$(
    curl --fail --silent "$METRICS_URL" 2>/dev/null \
      | awk '$1 ~ /^battle_worker_active(\{|$)/ { print $2; exit }' \
      || true
  )"
  if awk -v value="${active_workers:-0}" 'BEGIN { exit !(value >= 2) }'; then
    WORKERS_ACTIVE_MS="$(now_ms)"
    append_event workers_active "$WORKERS_ACTIVE_MS" "active_workers=${active_workers}"
    break
  fi
  if (( $(date '+%s') >= worker_deadline )); then
    echo "복구 워커 2개가 ${RECOVERY_TIMEOUT_SECONDS}초 안에 활성화되지 않았습니다." >&2
    exit 1
  fi
  sleep 0.1
done

recovery_deadline=$(( $(date '+%s') + RECOVERY_TIMEOUT_SECONDS ))
reclaim_recorded=false
completion_recorded=false
while true; do
  IFS=$'\t' read -r accepted submitted grading completed failed in_progress time_over <<< "$(queue_counts)"
  reclaimed_count="$(mysql_scalar "
    SELECT COUNT(*)
    FROM battle_participations
    WHERE daily_battle_id = ${BATTLE_ID}
      AND lease_retry_count > 0;
  ")"

  if [[ "$reclaim_recorded" == false ]] && (( reclaimed_count > 0 )); then
    FIRST_RECLAIM_MS="$(now_ms)"
    append_event first_lease_reclaimed "$FIRST_RECLAIM_MS" "reclaimed_count=${reclaimed_count}"
    snapshot_grading_leases after-first-reclaim
    reclaim_recorded=true
  fi
  if [[ "$completion_recorded" == false ]] && (( completed > BEFORE_COMPLETED )); then
    FIRST_COMPLETED_MS="$(now_ms)"
    append_event first_post_restart_completion "$FIRST_COMPLETED_MS" "completed=${completed}"
    completion_recorded=true
  fi
  if (( failed > 0 )); then
    snapshot_queue grading_failed
    echo "복구 후 채점 실패 작업이 발생했습니다: ${failed}건" >&2
    exit 1
  fi
  if (( submitted == 0 && grading == 0 && completed == EXPECTED_USERS )); then
    ALL_COMPLETED_MS="$(now_ms)"
    append_event all_tasks_completed "$ALL_COMPLETED_MS" "completed=${completed},failed=${failed}"
    break
  fi
  if (( $(date '+%s') >= recovery_deadline )); then
    snapshot_queue recovery_timeout
    echo "전체 작업이 ${RECOVERY_TIMEOUT_SECONDS}초 안에 종료 상태가 되지 않았습니다." >&2
    exit 1
  fi
  sleep "$POLL_INTERVAL_SECONDS"
done

snapshot_queue after-recovery
snapshot_participations after-recovery
snapshot_grading_leases after-recovery

mysql_query "
  SELECT
    bs.battle_submission_id,
    bs.participation_id,
    bs.battle_case_id,
    bs.is_correct
  FROM battle_submissions bs
  JOIN battle_participations bp
    ON bp.participation_id = bs.participation_id
  WHERE bp.daily_battle_id = ${BATTLE_ID}
  ORDER BY bs.participation_id, bs.battle_case_id;
" > "$REMOTE_RESULT_DIR/submissions-final.tsv"

mysql_query "
  SELECT
    COUNT(*) AS participation_count,
    SUM(submitted_at IS NOT NULL) AS accepted_participation_count,
    SUM(participation_status = 'GRADING_COMPLETED') AS completed_count,
    SUM(participation_status = 'GRADING_FAILED') AS failed_count,
    SUM(participation_status = 'SUBMITTED') AS remaining_submitted,
    SUM(participation_status = 'GRADING') AS remaining_grading,
    SUM(lease_retry_count > 0) AS reclaimed_participation_count,
    COALESCE(SUM(lease_retry_count), 0) AS total_lease_retry_count
  FROM battle_participations
  WHERE daily_battle_id = ${BATTLE_ID};
" > "$REMOTE_RESULT_DIR/final-status.tsv"

mysql_query "
  SELECT
    (
      SELECT COUNT(*)
      FROM battle_submissions bs
      JOIN battle_participations bp
        ON bp.participation_id = bs.participation_id
      WHERE bp.daily_battle_id = ${BATTLE_ID}
    ) AS submission_row_count,
    (
      SELECT COUNT(DISTINCT bs.participation_id)
      FROM battle_submissions bs
      JOIN battle_participations bp
        ON bp.participation_id = bs.participation_id
      WHERE bp.daily_battle_id = ${BATTLE_ID}
    ) AS participants_with_answers,
    (
      SELECT COALESCE(SUM(grouped.row_count - 1), 0)
      FROM (
        SELECT bs.participation_id, bs.battle_case_id, COUNT(*) AS row_count
        FROM battle_submissions bs
        JOIN battle_participations bp
          ON bp.participation_id = bs.participation_id
        WHERE bp.daily_battle_id = ${BATTLE_ID}
        GROUP BY bs.participation_id, bs.battle_case_id
        HAVING COUNT(*) > 1
      ) grouped
    ) AS duplicate_answer_rows;
" > "$REMOTE_RESULT_DIR/final-submission-integrity.tsv"

if [[ "$reclaim_recorded" != true ]]; then
  echo '만료된 Lease 작업의 재선점을 관측하지 못했습니다.' >&2
  exit 1
fi

expected_submission_rows=$((EXPECTED_USERS * EXPECTED_ANSWERS_PER_USER))
IFS=$'\t' read -r submission_row_count participants_with_answers duplicate_answer_rows <<< "$(mysql_scalar "
  SELECT CONCAT_WS(
    '\t',
    (
      SELECT COUNT(*)
      FROM battle_submissions bs
      JOIN battle_participations bp
        ON bp.participation_id = bs.participation_id
      WHERE bp.daily_battle_id = ${BATTLE_ID}
    ),
    (
      SELECT COUNT(DISTINCT bs.participation_id)
      FROM battle_submissions bs
      JOIN battle_participations bp
        ON bp.participation_id = bs.participation_id
      WHERE bp.daily_battle_id = ${BATTLE_ID}
    ),
    (
      SELECT COALESCE(SUM(grouped.row_count - 1), 0)
      FROM (
        SELECT bs.participation_id, bs.battle_case_id, COUNT(*) AS row_count
        FROM battle_submissions bs
        JOIN battle_participations bp
          ON bp.participation_id = bs.participation_id
        WHERE bp.daily_battle_id = ${BATTLE_ID}
        GROUP BY bs.participation_id, bs.battle_case_id
        HAVING COUNT(*) > 1
      ) grouped
    )
  );
")"

if (( submission_row_count != expected_submission_rows
      || participants_with_answers != EXPECTED_USERS
      || duplicate_answer_rows != 0 )); then
  echo "답안 정합성 검증 실패: rows=${submission_row_count}/${expected_submission_rows}, participants=${participants_with_answers}/${EXPECTED_USERS}, duplicates=${duplicate_answer_rows}" >&2
  exit 1
fi

cat > "$REMOTE_RESULT_DIR/recovery-durations.tsv" <<EOF
metric\tmilliseconds
server_downtime_to_health\t$((HEALTH_READY_MS - SERVER_KILLED_MS))
server_startup_to_health\t$((HEALTH_READY_MS - SERVER_RESTART_REQUESTED_MS))
health_to_workers_active\t$((WORKERS_ACTIVE_MS - HEALTH_READY_MS))
health_to_first_reclaim\t$((FIRST_RECLAIM_MS - HEALTH_READY_MS))
failure_to_first_reclaim\t$((FIRST_RECLAIM_MS - SERVER_KILLED_MS))
failure_to_first_completion\t$((FIRST_COMPLETED_MS - SERVER_KILLED_MS))
failure_to_all_terminal\t$((ALL_COMPLETED_MS - SERVER_KILLED_MS))
EOF

append_event recovery_validation_completed "$(now_ms)" \
  "completed=${completed},reclaimed=${reclaimed_count},submission_rows=${submission_row_count}"
log "R1 장애 주입 및 복구 검증 자료 저장 완료"

tar -C "$REMOTE_RESULT_DIR" -czf - .
rm -rf "$REMOTE_RESULT_DIR"
trap - EXIT

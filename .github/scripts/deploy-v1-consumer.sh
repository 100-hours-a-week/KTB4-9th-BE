#!/usr/bin/env bash
set -Eeuo pipefail

readonly DEPLOY_DIR="${CONSUMER_DEPLOY_DIR:-/opt/cosmos/backend-consumer}"
readonly COMPOSE_FILE="${DEPLOY_DIR}/compose.yml"
readonly ENV_FILE="${DEPLOY_DIR}/.env"
readonly CONTAINER_NAME="cosmos-backend-consumer"
readonly VERIFY_ATTEMPTS="${CONSUMER_VERIFY_ATTEMPTS:-30}"
readonly VERIFY_INTERVAL="${CONSUMER_VERIFY_INTERVAL:-2}"
readonly STABILIZATION_SECONDS="${CONSUMER_STABILIZATION_SECONDS:-10}"
readonly TARGET_IMAGE="${1:-}"

backup_file=""
had_container=0
deployment_mutated=0
target_started=0
deploy_succeeded=0

log() {
  printf '[consumer-deploy] %s\n' "$*"
}

fail() {
  log "ERROR: $*" >&2
  exit 1
}

file_mode() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"
}

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    --file "${COMPOSE_FILE}" \
    "$@"
}

set_backend_image() {
  local image="$1"
  local temporary_file

  temporary_file="$(mktemp "${DEPLOY_DIR}/.env.tmp.XXXXXX")"
  awk '
    {
      key = $0
      sub(/^[[:space:]]*/, "", key)
      sub(/[[:space:]]*[:=].*$/, "", key)
    }
    key != "BACKEND_IMAGE" &&
    key != "BATTLE_OPEN_TIME" &&
    key != "BATTLE_SUBMISSION_DEADLINE" &&
    key != "BATTLE_RESULT_TIME"
  ' "${ENV_FILE}" >"${temporary_file}"
  printf 'BACKEND_IMAGE=%s\n' "${image}" >>"${temporary_file}"
  chmod 600 "${temporary_file}"
  mv "${temporary_file}" "${ENV_FILE}"
}

container_exists() {
  docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1
}

container_is_running() {
  [[ "$(docker inspect --format '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null || true)" == "true" ]]
}

wait_for_startup() {
  local attempt
  local started_after="$1"

  for attempt in $(seq 1 "${VERIFY_ATTEMPTS}"); do
    if container_is_running &&
      docker logs --since "${started_after}" "${CONTAINER_NAME}" 2>&1 |
        grep -Eq 'Started [A-Za-z0-9_.]+Application'; then
      return 0
    fi
    sleep "${VERIFY_INTERVAL}"
  done

  return 1
}

verify_stability() {
  local initial_restart_count
  local final_restart_count

  initial_restart_count="$(docker inspect --format '{{.RestartCount}}' "${CONTAINER_NAME}")" || return 1
  sleep "${STABILIZATION_SECONDS}"
  container_is_running || return 1
  final_restart_count="$(docker inspect --format '{{.RestartCount}}' "${CONTAINER_NAME}")" || return 1
  [[ "${final_restart_count}" == "${initial_restart_count}" ]]
}

rollback() {
  log "배포 실패 상태를 이전 상태로 복구합니다."
  cp "${backup_file}" "${ENV_FILE}"
  chmod 600 "${ENV_FILE}"

  if [[ "${had_container}" -eq 1 ]]; then
    if [[ "${target_started}" -eq 1 ]]; then
      compose config --quiet
      compose pull backend-consumer
      compose up -d backend-consumer
      container_is_running || log "ERROR: 이전 consumer 복구 후 running 상태를 확인하지 못했습니다."
    fi
  elif [[ "${target_started}" -eq 1 ]]; then
    compose rm -sf backend-consumer
  fi
}

on_exit() {
  local status=$?

  if [[ "${status}" -ne 0 && "${deployment_mutated}" -eq 1 && -n "${backup_file}" ]]; then
    rollback || log "ERROR: 자동 rollback 중 오류가 발생했습니다."
  fi

  if [[ -n "${backup_file}" ]]; then
    rm -f "${backup_file}"
  fi

  if [[ "${deploy_succeeded}" -eq 1 ]]; then
    log "consumer 배포가 완료됐습니다."
  fi

  exit "${status}"
}

[[ "${TARGET_IMAGE}" =~ ^[a-z0-9][a-z0-9._/-]*:[0-9a-f]{40}$ ]] ||
  fail "image must use an immutable 40-character commit SHA tag"
[[ "${VERIFY_ATTEMPTS}" =~ ^[1-9][0-9]*$ ]] || fail "CONSUMER_VERIFY_ATTEMPTS must be positive"
[[ "${VERIFY_INTERVAL}" =~ ^[0-9]+$ ]] || fail "CONSUMER_VERIFY_INTERVAL must be a non-negative integer"
[[ "${STABILIZATION_SECONDS}" =~ ^[0-9]+$ ]] || fail "CONSUMER_STABILIZATION_SECONDS must be a non-negative integer"
[[ -f "${COMPOSE_FILE}" ]] || fail "missing consumer Compose file: ${COMPOSE_FILE}"
[[ -f "${ENV_FILE}" ]] || fail "missing consumer environment file: ${ENV_FILE}"
[[ "$(file_mode "${ENV_FILE}")" == "600" ]] || fail "consumer environment file must use mode 0600"
[[ "$(grep -c '^BACKEND_IMAGE=' "${ENV_FILE}")" == "1" ]] || fail "consumer environment must contain exactly one BACKEND_IMAGE"

container_exists && had_container=1
backup_file="$(mktemp "${DEPLOY_DIR}/.env.deploy-backup.XXXXXX")"
cp "${ENV_FILE}" "${backup_file}"
chmod 600 "${backup_file}"
trap on_exit EXIT

set_backend_image "${TARGET_IMAGE}"
deployment_mutated=1
compose config --quiet
compose pull backend-consumer

started_after="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
target_started=1
compose up -d --force-recreate backend-consumer
wait_for_startup "${started_after}" || fail "consumer did not reach a stable Spring started state"
verify_stability || fail "consumer did not remain stable after Spring startup"

deploy_succeeded=1

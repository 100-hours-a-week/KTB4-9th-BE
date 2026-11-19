#!/usr/bin/env bash

set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

USER_COUNT="${USER_COUNT:-500}"
CONCURRENCY="${RABBITMQ_CONSUMER_CONCURRENCY:-1}"
MQ_USER="${RABBITMQ_DEFAULT_USER:-cosmos}"
MQ_PASS="${RABBITMQ_DEFAULT_PASS:-cosmos-local}"
MQ_QUEUE_API='http://localhost:15673/api/queues/%2F/battle.grading.queue'

if (( $# > 0 )); then
  PREFETCH_VALUES=("$@")
else
  PREFETCH_VALUES=(1 5 10)
fi

RUN_ROOT="loadtest/results/mq-prefetch-u${USER_COUNT}-c${CONCURRENCY}-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_ROOT"
SUMMARY_FILE="$RUN_ROOT/summary.tsv"
EMPTY_QUEUE_JSON='{}'

printf '%s\n' \
  $'user_count\tconcurrency\tprefetch\ttest_id\texit_code\tfirst_publish_epoch\tlast_publish_epoch\tlast_ack_epoch\tpipeline_seconds\tdrain_after_last_publish_seconds\tmax_ready\tmax_unacked\tmax_consumers\tpublished\tacked' \
  > "$SUMMARY_FILE"

read_queue_json() {
  curl --silent --show-error --fail \
    --user "$MQ_USER:$MQ_PASS" \
    "$MQ_QUEUE_API"
}

metric_value() {
  local json="$1"
  local metric="$2"
  jq -r --arg metric "$metric" '.message_stats[$metric] // 0' <<<"$json"
}

for prefetch in "${PREFETCH_VALUES[@]}"; do
  printf '\n===== users=%s, concurrency=%s, prefetch=%s 테스트 시작 =====\n' "$USER_COUNT" "$CONCURRENCY" "$prefetch"

  LOG_FILE="$RUN_ROOT/prefetch-${prefetch}.log"
  SNAPSHOT_FILE="$RUN_ROOT/prefetch-${prefetch}-queue.tsv"
  printf '%s\n' $'epoch\tpublished_delta\tacked_delta\tready\tunacked\tconsumers' > "$SNAPSHOT_FILE"

  RABBITMQ_CONSUMER_PREFETCH="$prefetch" \
    ./loadtest/battle-s2-run-local.sh "$USER_COUNT" with-worker \
    >"$LOG_FILE" 2>&1 &
  test_pid=$!

  until queue_json="$(read_queue_json 2>/dev/null)"; do
    if ! kill -0 "$test_pid" 2>/dev/null; then
      break
    fi
    sleep 2
  done

  base_publish="$(metric_value "${queue_json:-$EMPTY_QUEUE_JSON}" publish)"
  base_ack="$(metric_value "${queue_json:-$EMPTY_QUEUE_JSON}" ack)"

  first_publish_epoch=''
  last_publish_epoch=''
  last_ack_epoch=''
  max_ready=0
  max_unacked=0
  max_consumers=0
  published_delta=0
  acked_delta=0

  while kill -0 "$test_pid" 2>/dev/null; do
    if queue_json="$(read_queue_json 2>/dev/null)"; then
      now_epoch="$(date +%s)"
      current_publish="$(metric_value "$queue_json" publish)"
      current_ack="$(metric_value "$queue_json" ack)"
      ready="$(jq -r '.messages_ready // 0' <<<"$queue_json")"
      unacked="$(jq -r '.messages_unacknowledged // 0' <<<"$queue_json")"
      consumers="$(jq -r '.consumers // 0' <<<"$queue_json")"
      published_delta=$((current_publish - base_publish))
      acked_delta=$((current_ack - base_ack))

      if (( published_delta > 0 )) && [[ -z "$first_publish_epoch" ]]; then
        first_publish_epoch="$now_epoch"
      fi
      if (( published_delta >= USER_COUNT )) && [[ -z "$last_publish_epoch" ]]; then
        last_publish_epoch="$now_epoch"
      fi
      if (( acked_delta >= USER_COUNT )) && [[ -z "$last_ack_epoch" ]]; then
        last_ack_epoch="$now_epoch"
      fi
      if (( ready > max_ready )); then
        max_ready="$ready"
      fi
      if (( unacked > max_unacked )); then
        max_unacked="$unacked"
      fi
      if (( consumers > max_consumers )); then
        max_consumers="$consumers"
      fi

      printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
        "$now_epoch" "$published_delta" "$acked_delta" "$ready" "$unacked" "$consumers" \
        >> "$SNAPSHOT_FILE"
    fi
    sleep 1
  done

  wait "$test_pid"
  exit_code=$?
  test_id="$(sed -n 's/^TEST_ID=//p' "$LOG_FILE" | tail -n 1)"

  # 일부 제출이 MQ 도달 전에 실패해도 실제 최종 발행 건수를 기준으로 완료 시각을 계산합니다.
  if (( published_delta > 0 )); then
    if [[ -z "$last_publish_epoch" ]]; then
      last_publish_epoch="$(awk -F '\t' -v target="$published_delta" \
        'NR > 1 && $2 >= target { print $1; exit }' "$SNAPSHOT_FILE")"
    fi
    if [[ -z "$last_ack_epoch" ]]; then
      last_ack_epoch="$(awk -F '\t' -v target="$published_delta" \
        'NR > 1 && $3 >= target { print $1; exit }' "$SNAPSHOT_FILE")"
    fi
  fi

  # Worker 교체 전에 잠깐 보이는 이전 Consumer는 제외합니다.
  active_max_consumers="$(awk -F '\t' \
    'NR > 1 && $2 > 0 { if ($6 > max) max=$6 } END { print max+0 }' "$SNAPSHOT_FILE")"
  if (( active_max_consumers > 0 )); then
    max_consumers="$active_max_consumers"
  fi

  pipeline_seconds='NA'
  drain_after_last_publish_seconds='NA'
  if [[ -n "$first_publish_epoch" && -n "$last_ack_epoch" ]]; then
    pipeline_seconds=$((last_ack_epoch - first_publish_epoch))
  fi
  if [[ -n "$last_publish_epoch" && -n "$last_ack_epoch" ]]; then
    drain_after_last_publish_seconds=$((last_ack_epoch - last_publish_epoch))
  fi

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$USER_COUNT" "$CONCURRENCY" "$prefetch" "${test_id:-UNKNOWN}" "$exit_code" \
    "${first_publish_epoch:-NA}" "${last_publish_epoch:-NA}" "${last_ack_epoch:-NA}" \
    "$pipeline_seconds" "$drain_after_last_publish_seconds" \
    "$max_ready" "$max_unacked" "$max_consumers" "$published_delta" "$acked_delta" \
    >> "$SUMMARY_FILE"

  printf 'users=%s, concurrency=%s, prefetch=%s 완료: TEST_ID=%s, pipeline=%ss, maxReady=%s, maxUnacked=%s, consumers=%s\n' \
    "$USER_COUNT" "$CONCURRENCY" "$prefetch" "${test_id:-UNKNOWN}" "$pipeline_seconds" "$max_ready" "$max_unacked" "$max_consumers"

  sleep 5
done

printf '\n모든 테스트 완료: %s\n' "$RUN_ROOT"

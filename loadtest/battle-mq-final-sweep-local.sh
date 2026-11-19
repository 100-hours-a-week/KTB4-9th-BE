#!/usr/bin/env bash

set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

export RABBITMQ_CONSUMER_CONCURRENCY=4

# 500명에서는 이미 측정한 prefetch=1 결과를 기준값으로 재사용합니다.
USER_COUNT=500 ./loadtest/battle-mq-prefetch-sweep-local.sh 2 5

# 1,000명에서는 prefetch 1, 2, 5를 같은 조건으로 비교합니다.
USER_COUNT=1000 ./loadtest/battle-mq-prefetch-sweep-local.sh 1 2 5

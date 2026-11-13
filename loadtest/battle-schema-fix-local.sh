#!/usr/bin/env bash

set -Eeuo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-cosmos-mysql}"

if [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)" != 'true' ]]; then
  echo "실행 중인 MySQL 컨테이너를 찾을 수 없습니다: ${MYSQL_CONTAINER}" >&2
  exit 1
fi

mysql_scalar() {
  docker exec -i "$MYSQL_CONTAINER" sh -lc \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw --skip-column-names -uroot "$MYSQL_DATABASE"' \
    <<< "$1"
}

mysql_execute() {
  docker exec -i "$MYSQL_CONTAINER" sh -lc \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw -uroot "$MYSQL_DATABASE"' \
    <<< "$1"
}

submission_rows="$(mysql_scalar 'SELECT COUNT(*) FROM battle_submissions;')"
if (( submission_rows > 0 )); then
  echo "battle_submissions에 ${submission_rows}건이 있어 자동 수정하지 않습니다." >&2
  echo '기존 데이터 보존 방법을 먼저 결정해야 합니다.' >&2
  exit 1
fi

id_extra="$(mysql_scalar "
  SELECT EXTRA
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'battle_submissions'
    AND COLUMN_NAME = 'battle_submission_id';
")"
if [[ "$id_extra" != *auto_increment* ]]; then
  mysql_execute '
    ALTER TABLE battle_submissions
      MODIFY COLUMN battle_submission_id BIGINT NOT NULL AUTO_INCREMENT;
  '
fi

legacy_column_count="$(mysql_scalar "
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'battle_submissions'
    AND COLUMN_NAME = 'battle_participation_id';
")"
if (( legacy_column_count > 0 )); then
  mysql_execute '
    ALTER TABLE battle_submissions
      DROP COLUMN battle_participation_id;
  '
fi

legacy_sequence_count="$(mysql_scalar "
  SELECT COUNT(*)
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'battle_submissions_seq';
")"
if (( legacy_sequence_count > 0 )); then
  mysql_execute 'DROP TABLE battle_submissions_seq;'
fi

mysql_execute '
  SELECT
    COLUMN_NAME,
    COLUMN_TYPE,
    IS_NULLABLE,
    EXTRA
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = "battle_submissions"
  ORDER BY ORDINAL_POSITION;
'

echo '로컬 battle_submissions 스키마를 현재 엔티티와 일치시켰습니다.'

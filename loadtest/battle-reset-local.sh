#!/usr/bin/env bash

set -Eeuo pipefail

BATTLE_ID="${BATTLE_ID:-}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-cosmos-mysql}"

if [[ ! "$BATTLE_ID" =~ ^[0-9]+$ ]]; then
  echo 'BATTLE_ID는 숫자로 설정되어 있어야 합니다.' >&2
  exit 1
fi

if [[ "$(docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null || true)" != 'true' ]]; then
  echo "실행 중인 MySQL 컨테이너를 찾을 수 없습니다: ${MYSQL_CONTAINER}" >&2
  exit 1
fi

docker exec -i "$MYSQL_CONTAINER" sh -lc \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --batch --raw -uroot "$MYSQL_DATABASE"' <<SQL
START TRANSACTION;

DELETE bs
FROM battle_submissions bs
JOIN battle_participations bp
  ON bp.participation_id = bs.participation_id
WHERE bp.daily_battle_id = ${BATTLE_ID};

DELETE FROM battle_participations
WHERE daily_battle_id = ${BATTLE_ID};

UPDATE daily_battles
SET result_published_at = NULL
WHERE battle_id = ${BATTLE_ID};

COMMIT;

SELECT
  db.battle_id,
  db.battle_date,
  db.result_published_at,
  COUNT(bp.participation_id) AS remaining_participations,
  COUNT(bs.battle_submission_id) AS remaining_submissions
FROM daily_battles db
LEFT JOIN battle_participations bp
  ON bp.daily_battle_id = db.battle_id
LEFT JOIN battle_submissions bs
  ON bs.participation_id = bp.participation_id
WHERE db.battle_id = ${BATTLE_ID}
GROUP BY db.battle_id, db.battle_date, db.result_published_at;
SQL

echo "배틀 ${BATTLE_ID}의 참여·제출·결과 공개 기록을 초기화했습니다."

-- 데모 문제 5개 샘플 데이터 (문제 생성 API 테스트용)
-- 사용법:
--   1) 백엔드를 한 번 켜서 테이블을 먼저 만들어 둔다 (ddl-auto: update)
--   2) 아래 명령으로 이 파일을 실행한다
--      docker exec -i cosmos-mysql mysql --default-character-set=utf8mb4 -ucosmos -p본인비밀번호 cosmos < sample-problems.sql
-- 여러 번 실행해도 같은 제목의 문제가 이미 있으면 그 문제는 건너뜁니다 (중복 방지).

-- ===== 1. 계단 오르기 (LV2, DP) =====
SET @t := '계단 오르기 (데모)';
SET @exists := (SELECT COUNT(*) FROM problems WHERE title = @t);
INSERT INTO problems (difficulty, category, title, content, input_format, output_format, category_select_reason, constraints)
SELECT 'LV2', 'DP', @t,
       '계단이 N개 있다. 한 번에 1칸 또는 2칸을 오를 수 있다. N번째 칸에 도착하는 방법의 수를 구하라.',
       '첫째 줄에 계단 수 N이 주어진다.',
       '방법의 수를 정수로 출력한다.',
       '',
       '[{"scope": "INPUT", "target": "N", "dataType": "INT", "maxValue": 30, "minValue": 1, "specialConditions": []}, {"scope": "OUTPUT", "target": "output", "dataType": "INT", "maxValue": 1346269, "minValue": 1, "specialConditions": []}]'
WHERE @exists = 0;
SET @pid := (SELECT id FROM problems WHERE title = @t LIMIT 1);
INSERT INTO problem_examples (problem_id, input, output, description, display_order)
SELECT @pid, e.i, e.o, e.d, e.n FROM (
    SELECT '3' i, '3' o, '1+1+1, 1+2, 2+1 세 가지다.' d, 1 n
    UNION ALL SELECT '4', '5', NULL, 2
) e WHERE @exists = 0;
INSERT INTO running_limits (problem_id, language, time_limit_ms, memory_limit_kb)
SELECT @pid, l.language, l.t, 262144 FROM (
    SELECT 'PYTHON' language, 3000 t UNION ALL SELECT 'JAVASCRIPT', 2000 UNION ALL SELECT 'JAVA', 2000 UNION ALL SELECT 'CPP', 1000
) l WHERE @exists = 0;

-- ===== 2. 배열의 최댓값 (LV1, ARRAY) =====
SET @t := '배열의 최댓값 (데모)';
SET @exists := (SELECT COUNT(*) FROM problems WHERE title = @t);
INSERT INTO problems (difficulty, category, title, content, input_format, output_format, category_select_reason, constraints)
SELECT 'LV1', 'ARRAY', @t,
       'N개의 정수가 주어질 때 가장 큰 값을 구하라.',
       '첫째 줄에 N, 둘째 줄에 N개의 정수가 공백으로 주어진다.',
       '가장 큰 값을 출력한다.',
       '',
       '[{"scope": "INPUT", "target": "N", "dataType": "INT", "maxValue": 100, "minValue": 1, "specialConditions": []}, {"scope": "INPUT", "target": "arr", "dataType": "INT", "maxValue": 1000, "minValue": -1000, "specialConditions": []}, {"scope": "OUTPUT", "target": "output", "dataType": "INT", "maxValue": 1000, "minValue": -1000, "specialConditions": []}]'
WHERE @exists = 0;
SET @pid := (SELECT id FROM problems WHERE title = @t LIMIT 1);
INSERT INTO problem_examples (problem_id, input, output, description, display_order)
SELECT @pid, e.i, e.o, e.d, e.n FROM (
    SELECT '3\n1 5 2' i, '5' o, '가장 큰 값은 5다.' d, 1 n
    UNION ALL SELECT '2\n-1 -7', '-1', NULL, 2
) e WHERE @exists = 0;
INSERT INTO running_limits (problem_id, language, time_limit_ms, memory_limit_kb)
SELECT @pid, l.language, l.t, 262144 FROM (
    SELECT 'PYTHON' language, 3000 t UNION ALL SELECT 'JAVASCRIPT', 2000 UNION ALL SELECT 'JAVA', 2000 UNION ALL SELECT 'CPP', 1000
) l WHERE @exists = 0;

-- ===== 3. 연결 요소의 개수 (LV3, GRAPH) =====
SET @t := '연결 요소의 개수 (데모)';
SET @exists := (SELECT COUNT(*) FROM problems WHERE title = @t);
INSERT INTO problems (difficulty, category, title, content, input_format, output_format, category_select_reason, constraints)
SELECT 'LV3', 'GRAPH', @t,
       'N개의 정점과 M개의 간선으로 이루어진 무방향 그래프에서 연결 요소의 개수를 구하라.',
       '첫째 줄에 N과 M, 다음 M개의 줄에 간선의 두 정점이 주어진다.',
       '연결 요소의 개수를 출력한다.',
       '',
       '[{"scope": "INPUT", "target": "N", "dataType": "INT", "maxValue": 1000, "minValue": 1, "specialConditions": []}, {"scope": "INPUT", "target": "M", "dataType": "INT", "maxValue": 10000, "minValue": 0, "specialConditions": []}, {"scope": "OUTPUT", "target": "output", "dataType": "INT", "maxValue": 1000, "minValue": 1, "specialConditions": []}]'
WHERE @exists = 0;
SET @pid := (SELECT id FROM problems WHERE title = @t LIMIT 1);
INSERT INTO problem_examples (problem_id, input, output, description, display_order)
SELECT @pid, e.i, e.o, e.d, e.n FROM (
    SELECT '4 2\n1 2\n3 4' i, '2' o, '{1,2}와 {3,4} 두 덩어리다.' d, 1 n
) e WHERE @exists = 0;
INSERT INTO running_limits (problem_id, language, time_limit_ms, memory_limit_kb)
SELECT @pid, l.language, l.t, 262144 FROM (
    SELECT 'PYTHON' language, 3000 t UNION ALL SELECT 'JAVASCRIPT', 2000 UNION ALL SELECT 'JAVA', 2000 UNION ALL SELECT 'CPP', 1000
) l WHERE @exists = 0;

-- ===== 4. 타일 채우기 (LV3, DP) =====
SET @t := '타일 채우기 (데모)';
SET @exists := (SELECT COUNT(*) FROM problems WHERE title = @t);
INSERT INTO problems (difficulty, category, title, content, input_format, output_format, category_select_reason, constraints)
SELECT 'LV3', 'DP', @t,
       '2×N 크기의 직사각형을 1×2, 2×1 타일로 빈틈없이 채우는 방법의 수를 구하라.',
       '첫째 줄에 N이 주어진다.',
       '방법의 수를 정수로 출력한다.',
       '',
       '[{"scope": "INPUT", "target": "N", "dataType": "INT", "maxValue": 30, "minValue": 1, "specialConditions": []}, {"scope": "OUTPUT", "target": "output", "dataType": "INT", "maxValue": 1346269, "minValue": 1, "specialConditions": []}]'
WHERE @exists = 0;
SET @pid := (SELECT id FROM problems WHERE title = @t LIMIT 1);
INSERT INTO problem_examples (problem_id, input, output, description, display_order)
SELECT @pid, e.i, e.o, e.d, e.n FROM (
    SELECT '3' i, '3' o, NULL d, 1 n
) e WHERE @exists = 0;
INSERT INTO running_limits (problem_id, language, time_limit_ms, memory_limit_kb)
SELECT @pid, l.language, l.t, 262144 FROM (
    SELECT 'PYTHON' language, 3000 t UNION ALL SELECT 'JAVASCRIPT', 2000 UNION ALL SELECT 'JAVA', 2000 UNION ALL SELECT 'CPP', 1000
) l WHERE @exists = 0;

-- ===== 5. 문자열 뒤집기 (LV2, STRING) =====
SET @t := '문자열 뒤집기 (데모)';
SET @exists := (SELECT COUNT(*) FROM problems WHERE title = @t);
INSERT INTO problems (difficulty, category, title, content, input_format, output_format, category_select_reason, constraints)
SELECT 'LV2', 'STRING', @t,
       '문자열이 주어질 때 순서를 뒤집어서 출력하라.',
       '첫째 줄에 영문 소문자로 이루어진 문자열이 주어진다.',
       '뒤집은 문자열을 출력한다.',
       '',
       '[{"scope": "INPUT", "target": "s", "dataType": "STRING", "maxValue": 100, "minValue": 1, "specialConditions": []}, {"scope": "OUTPUT", "target": "output", "dataType": "STRING", "maxValue": 100, "minValue": 1, "specialConditions": []}]'
WHERE @exists = 0;
SET @pid := (SELECT id FROM problems WHERE title = @t LIMIT 1);
INSERT INTO problem_examples (problem_id, input, output, description, display_order)
SELECT @pid, e.i, e.o, e.d, e.n FROM (
    SELECT 'abc' i, 'cba' o, NULL d, 1 n
) e WHERE @exists = 0;
INSERT INTO running_limits (problem_id, language, time_limit_ms, memory_limit_kb)
SELECT @pid, l.language, l.t, 262144 FROM (
    SELECT 'PYTHON' language, 3000 t UNION ALL SELECT 'JAVASCRIPT', 2000 UNION ALL SELECT 'JAVA', 2000 UNION ALL SELECT 'CPP', 1000
) l WHERE @exists = 0;

SELECT id, difficulty, category, title FROM problems ORDER BY id;

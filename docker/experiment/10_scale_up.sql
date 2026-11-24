-- =====================================================================
-- 성능 실험 1단계: 평가 데이터를 약 10만 건으로 늘린다
--   기존 '제출'된 PM·동료 평가(1,254건)를 80번 복제한다.
--   평가자, 피평가자, 프로젝트, 평가구분, 평가일자, 문항별 점수를 그대로 복사하고 ID만 새로 준다.
--   새 평가ID = 회차(1~80) * 10000 + 원래 평가ID  (원래 ID는 모두 10000 미만)
--   그래서 복사본도 원본과 같은 규칙을 따른다.
--     - PM 평가의 평가자는 그 프로젝트의 PM, 동료 평가는 투입 기간이 겹친 팀원끼리
--     - 문항은 평가구분의 사용여부가 'Y'인 것만, 점수는 0~10
--     - 외래키(프로젝트참여, 평가자)도 그대로 만족한다
--   평가내용(TB_EVAL_CN)과 고객평가는 실험 SQL이 쓰지 않거나 요청 범위가 아니라 복제하지 않는다.
--
-- 실행 (프로젝트 폴더, PowerShell. Get-Content 파이프는 한글이 깨지므로 cmd 리다이렉트를 쓴다):
--   cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\10_scale_up.sql"
-- 다시 실행해도 된다 (먼저 예전 복제분을 지운다). 되돌리기: 11_scale_down.sql
-- =====================================================================
USE si_company;
SET NAMES utf8mb4;

-- 예전 복제분이 있으면 지운다 (11_scale_down.sql과 같음)
DELETE FROM TB_EVAL_QSTN_SCORE WHERE EVAL_ID >= 10000;
DELETE FROM TB_EVAL_CN         WHERE EVAL_ID >= 10000;
DELETE FROM TB_PM_EVAL         WHERE EVAL_ID >= 10000;
DELETE FROM TB_PEER_EVAL       WHERE EVAL_ID >= 10000;
DELETE FROM TB_EVAL            WHERE EVAL_ID >= 10000;

-- 복제 회차 1~80
DROP TEMPORARY TABLE IF EXISTS tmp_seq;
CREATE TEMPORARY TABLE tmp_seq (N INT PRIMARY KEY);
INSERT INTO tmp_seq (N)
WITH RECURSIVE seq (N) AS (
    SELECT 1
    UNION ALL
    SELECT N + 1 FROM seq WHERE N < 80
)
SELECT N FROM seq;

-- 복제할 원본: 제출된 PM·동료 평가
DROP TEMPORARY TABLE IF EXISTS tmp_src;
CREATE TEMPORARY TABLE tmp_src (EVAL_ID INT PRIMARY KEY);
INSERT INTO tmp_src (EVAL_ID)
SELECT EVAL_ID
FROM TB_EVAL
WHERE EVAL_STAT = '제출'
  AND EVAL_DIV IN ('PM', '동료')
  AND EVAL_ID < 10000;

START TRANSACTION;

-- 평가 (슈퍼타입)
INSERT INTO TB_EVAL (EVAL_ID, EVAL_DIV, EVAL_STAT, EVAL_DT, EVLTE_EMP_ID, PRJ_ID)
SELECT q.N * 10000 + e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT, e.EVLTE_EMP_ID, e.PRJ_ID
FROM TB_EVAL e
JOIN tmp_src s ON s.EVAL_ID = e.EVAL_ID
CROSS JOIN tmp_seq q;

-- PM평가 (서브타입)
INSERT INTO TB_PM_EVAL (EVAL_ID, EVLTR_EMP_ID, PRJ_ID)
SELECT q.N * 10000 + pm.EVAL_ID, pm.EVLTR_EMP_ID, pm.PRJ_ID
FROM TB_PM_EVAL pm
JOIN tmp_src s ON s.EVAL_ID = pm.EVAL_ID
CROSS JOIN tmp_seq q;

-- 동료평가 (서브타입)
INSERT INTO TB_PEER_EVAL (EVAL_ID, EVLTR_EMP_ID, PRJ_ID)
SELECT q.N * 10000 + pe.EVAL_ID, pe.EVLTR_EMP_ID, pe.PRJ_ID
FROM TB_PEER_EVAL pe
JOIN tmp_src s ON s.EVAL_ID = pe.EVAL_ID
CROSS JOIN tmp_seq q;

-- 문항별 점수
INSERT INTO TB_EVAL_QSTN_SCORE (EVAL_ID, QSTN_ID, SCORE)
SELECT q.N * 10000 + sc.EVAL_ID, sc.QSTN_ID, sc.SCORE
FROM TB_EVAL_QSTN_SCORE sc
JOIN tmp_src s ON s.EVAL_ID = sc.EVAL_ID
CROSS JOIN tmp_seq q;

COMMIT;

DROP TEMPORARY TABLE tmp_seq;
DROP TEMPORARY TABLE tmp_src;

-- 옵티마이저 통계 갱신
ANALYZE TABLE TB_EVAL, TB_PM_EVAL, TB_PEER_EVAL, TB_EVAL_QSTN_SCORE;

-- 결과 확인
SELECT EVAL_DIV, COUNT(*) AS EVAL_CNT
FROM TB_EVAL
GROUP BY EVAL_DIV;

SELECT COUNT(*) AS TOTAL_EVAL FROM TB_EVAL;
SELECT COUNT(*) AS TOTAL_SCORE FROM TB_EVAL_QSTN_SCORE;

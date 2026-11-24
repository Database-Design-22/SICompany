-- =====================================================================
-- 성능 실험 1단계 되돌리기: 10_scale_up.sql로 복제한 평가만 지운다 (평가ID 10000 이상)
--   원래 데이터(평가ID 10000 미만)는 그대로 남는다.
--   전부 처음 상태로 돌리려면 docker 폴더에서 docker compose down -v 후 docker compose up -d
--
-- 실행:
--   cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\11_scale_down.sql"
-- =====================================================================
USE si_company;

START TRANSACTION;
DELETE FROM TB_EVAL_QSTN_SCORE WHERE EVAL_ID >= 10000;
DELETE FROM TB_EVAL_CN         WHERE EVAL_ID >= 10000;
DELETE FROM TB_PM_EVAL         WHERE EVAL_ID >= 10000;
DELETE FROM TB_PEER_EVAL       WHERE EVAL_ID >= 10000;
DELETE FROM TB_EVAL            WHERE EVAL_ID >= 10000;
COMMIT;

-- 자동 증가 값을 남은 최대 ID 다음으로 되돌린다
ALTER TABLE TB_EVAL AUTO_INCREMENT = 1;

ANALYZE TABLE TB_EVAL, TB_PM_EVAL, TB_PEER_EVAL, TB_EVAL_QSTN_SCORE;

SELECT COUNT(*) AS TOTAL_EVAL FROM TB_EVAL;
SELECT COUNT(*) AS TOTAL_SCORE FROM TB_EVAL_QSTN_SCORE;

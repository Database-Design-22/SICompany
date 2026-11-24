-- =====================================================================
-- 후보 B 되돌리기: 40_denorm_apply.sql 로 추가한 원점수 열을 지운다
-- =====================================================================
USE si_company;

ALTER TABLE TB_EVAL DROP COLUMN RAW_SCORE;

ANALYZE TABLE TB_EVAL;

SHOW COLUMNS FROM TB_EVAL;

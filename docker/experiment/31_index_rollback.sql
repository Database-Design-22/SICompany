-- =====================================================================
-- 후보 A 되돌리기: 30_index_apply.sql 로 만든 인덱스를 지운다
-- =====================================================================
USE si_company;

DROP INDEX idx_eval_stat_dt_div ON TB_EVAL;

ANALYZE TABLE TB_EVAL;

SHOW INDEX FROM TB_EVAL;

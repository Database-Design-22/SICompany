-- =====================================================================
-- 후보 A. 인덱스 추가
--   평가 결과 조회의 WHERE (EVAL_STAT = '제출' AND EVAL_DT 범위 AND EVAL_DIV)를 인덱스로 찾게 한다.
--   등호 조건 열(EVAL_STAT)을 앞에, 범위 조건 열(EVAL_DT)을 다음에, 거르기 열(EVAL_DIV)을 뒤에 둔다.
--   지금 TB_EVAL에는 PK와 외래키 인덱스만 있어 연도 조건으로 찾을 인덱스가 없다.
--
-- 적용 후 20_query_baseline.sql(기준 SQL)과 50_query_lateral.sql(후보 C)을 다시 측정한다.
-- 되돌리기: 31_index_rollback.sql
-- =====================================================================
USE si_company;

CREATE INDEX idx_eval_stat_dt_div ON TB_EVAL (EVAL_STAT, EVAL_DT, EVAL_DIV);

ANALYZE TABLE TB_EVAL;

SHOW INDEX FROM TB_EVAL WHERE Key_name = 'idx_eval_stat_dt_div';

-- =====================================================================
-- 후보 B. 반정규화: 평가에 원점수(문항 점수 평균) 열을 둔다
--   기준 SQL은 조회할 때마다 문항별 점수 전체를 GROUP BY 해서 평균을 낸다.
--   평균을 TB_EVAL.RAW_SCORE 에 미리 저장해 두면 그 집계가 사라진다.
--
--   비용: 같은 값이 두 곳(점수 테이블, 평가 테이블)에 있게 된다.
--         이 DB에는 트리거가 없으므로 실제로 쓰려면 평가 [제출](EvaluationService.submit)이
--         RAW_SCORE 를 함께 저장해야 하고, 점수를 고치는 경로가 생기면 어긋날 위험이 있다.
--         (제출된 평가는 점수를 고칠 수 없다는 업무 규칙 덕분에 위험은 작다)
--   이 실험은 데모 DB와 앱 코드에는 적용하지 않는다.
--
-- 적용 후 42_query_denorm.sql 로 측정한다. 되돌리기: 41_denorm_rollback.sql
-- =====================================================================
USE si_company;

ALTER TABLE TB_EVAL
    ADD COLUMN RAW_SCORE DECIMAL(4,2) NULL COMMENT '원점수: 문항 점수 평균 (반정규화)';

-- 기존 평가의 원점수를 채운다 (점수가 없는 평가는 NULL)
UPDATE TB_EVAL e
JOIN (SELECT EVAL_ID, AVG(SCORE) AS AVG_SCORE
      FROM TB_EVAL_QSTN_SCORE
      GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
SET e.RAW_SCORE = ROUND(sc.AVG_SCORE, 2);

ANALYZE TABLE TB_EVAL;

SELECT COUNT(*) AS EVAL_CNT, COUNT(RAW_SCORE) AS FILLED_CNT FROM TB_EVAL;

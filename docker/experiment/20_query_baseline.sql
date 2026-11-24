-- =====================================================================
-- 기준(baseline): 평가 결과 조회 SQL을 그대로 EXPLAIN ANALYZE 한다
--   SQL 본문은 AdjustmentRepository.FIND_EVAL_RESULTS 와 같다. (? 네 개만 @변수로 바꿈)
--   측정 조건 ① 2025년 동료 평가  ② 2025년 PM 평가
--
-- 실행:
--   cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\20_query_baseline.sql"
-- 결과의 맨 윗줄 actual time=시작..끝 의 '끝' 값(ms)이 전체 실행 시간이다. 3번 실행해 중간값을 기록한다.
-- =====================================================================
USE si_company;
SET NAMES utf8mb4;

-- ① 2025년 동료 평가
SET @from = '2025-01-01', @to = '2026-01-01', @div = '동료';

EXPLAIN ANALYZE
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_DT,
       e.EVLTE_EMP_ID, te.EMP_NM AS EVLTE_EMP_NM,
       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
       COALESCE(re.EMP_NM, cm.MGR_NM) AS EVLTR_NM,
       e.PRJ_ID, p.PRJ_NM,
       ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
            WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
       END AS ADJ_SCORE
FROM TB_EVAL e
JOIN (SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE
      FROM TB_EVAL_QSTN_SCORE
      GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p  ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP te ON te.EMP_ID = e.EVLTE_EMP_ID
LEFT JOIN TB_PM_EVAL pm    ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe  ON pe.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_EMP re        ON re.EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
LEFT JOIN TB_CUST_EVAL ce  ON ce.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_CLIENT_MGR cm ON cm.CLIENT_ID = ce.CLIENT_ID AND cm.MGR_ID = ce.EVAL_MGR_ID
LEFT JOIN TB_ADJ_RND r     ON r.ADJ_YR = YEAR(e.EVAL_DT)
LEFT JOIN TB_EVLTR_ADJ_VAL av
       ON av.ADJ_YR = YEAR(e.EVAL_DT)
      AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
      AND av.EVAL_DIV = e.EVAL_DIV
WHERE e.EVAL_STAT = '제출'
  AND e.EVAL_DT >= @from AND e.EVAL_DT < @to
  AND (@div IS NULL OR e.EVAL_DIV = @div)
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID;

-- ② 2025년 PM 평가
SET @from = '2025-01-01', @to = '2026-01-01', @div = 'PM';

EXPLAIN ANALYZE
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_DT,
       e.EVLTE_EMP_ID, te.EMP_NM AS EVLTE_EMP_NM,
       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
       COALESCE(re.EMP_NM, cm.MGR_NM) AS EVLTR_NM,
       e.PRJ_ID, p.PRJ_NM,
       ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
            WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
       END AS ADJ_SCORE
FROM TB_EVAL e
JOIN (SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE
      FROM TB_EVAL_QSTN_SCORE
      GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
JOIN TB_PRJ p  ON p.PRJ_ID = e.PRJ_ID
JOIN TB_EMP te ON te.EMP_ID = e.EVLTE_EMP_ID
LEFT JOIN TB_PM_EVAL pm    ON pm.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_PEER_EVAL pe  ON pe.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_EMP re        ON re.EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
LEFT JOIN TB_CUST_EVAL ce  ON ce.EVAL_ID = e.EVAL_ID
LEFT JOIN TB_CLIENT_MGR cm ON cm.CLIENT_ID = ce.CLIENT_ID AND cm.MGR_ID = ce.EVAL_MGR_ID
LEFT JOIN TB_ADJ_RND r     ON r.ADJ_YR = YEAR(e.EVAL_DT)
LEFT JOIN TB_EVLTR_ADJ_VAL av
       ON av.ADJ_YR = YEAR(e.EVAL_DT)
      AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
      AND av.EVAL_DIV = e.EVAL_DIV
WHERE e.EVAL_STAT = '제출'
  AND e.EVAL_DT >= @from AND e.EVAL_DT < @to
  AND (@div IS NULL OR e.EVAL_DIV = @div)
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID;

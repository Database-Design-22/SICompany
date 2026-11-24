-- =====================================================================
-- 후보 B 측정: 반정규화한 원점수 열(TB_EVAL.RAW_SCORE)을 쓰는 평가 결과 조회
--   기준 SQL과 다른 곳: 문항별 점수 집계 파생 테이블(sc)을 빼고 e.RAW_SCORE 를 쓴다.
--   기준 SQL은 점수가 있는 평가만 나오므로(내부 조인) 같은 뜻으로 RAW_SCORE IS NOT NULL 을 둔다.
--   먼저 40_denorm_apply.sql 을 적용해야 한다.
--
-- 실행:
--   cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\42_query_denorm.sql"
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
       e.RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN e.RAW_SCORE
            WHEN r.CNFM_DT IS NOT NULL THEN e.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0)
       END AS ADJ_SCORE
FROM TB_EVAL e
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
  AND e.RAW_SCORE IS NOT NULL
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID;

-- ② 2025년 PM 평가
SET @from = '2025-01-01', @to = '2026-01-01', @div = 'PM';

EXPLAIN ANALYZE
SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_DT,
       e.EVLTE_EMP_ID, te.EMP_NM AS EVLTE_EMP_NM,
       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
       COALESCE(re.EMP_NM, cm.MGR_NM) AS EVLTR_NM,
       e.PRJ_ID, p.PRJ_NM,
       e.RAW_SCORE,
       CASE WHEN e.EVAL_DIV = '고객' THEN NULL
            ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
       r.ADJ_YR AS RND_YR, r.CNFM_DT,
       CASE WHEN e.EVAL_DIV = '고객' THEN e.RAW_SCORE
            WHEN r.CNFM_DT IS NOT NULL THEN e.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0)
       END AS ADJ_SCORE
FROM TB_EVAL e
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
  AND e.RAW_SCORE IS NOT NULL
ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID;

-- ③ 결과가 기준 SQL과 같은지 확인 (모든 연도, 모든 평가구분). DIFF_ROWS = 0 이어야 한다.
SET @from = '2000-01-01', @to = '2100-01-01', @div = NULL;

SELECT COUNT(*) AS BASE_ROWS,
       SUM(b.EVAL_ID IS NULL) AS MISSING_ROWS,
       SUM(b.EVAL_ID IS NOT NULL
           AND NOT (a.RAW_SCORE <=> b.RAW_SCORE
                    AND a.APLY_ADJ_VAL <=> b.APLY_ADJ_VAL
                    AND a.ADJ_SCORE <=> b.ADJ_SCORE)) AS DIFF_ROWS
FROM (
    -- 기준 SQL (AdjustmentRepository.FIND_EVAL_RESULTS, 정렬 제외)
    SELECT e.EVAL_ID,
           ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
           CASE WHEN e.EVAL_DIV = '고객' THEN NULL ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
           CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
                WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
           END AS ADJ_SCORE
    FROM TB_EVAL e
    JOIN (SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE FROM TB_EVAL_QSTN_SCORE GROUP BY EVAL_ID) sc ON sc.EVAL_ID = e.EVAL_ID
    LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
    LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
    LEFT JOIN TB_ADJ_RND r    ON r.ADJ_YR = YEAR(e.EVAL_DT)
    LEFT JOIN TB_EVLTR_ADJ_VAL av
           ON av.ADJ_YR = YEAR(e.EVAL_DT)
          AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
          AND av.EVAL_DIV = e.EVAL_DIV
    WHERE e.EVAL_STAT = '제출'
      AND e.EVAL_DT >= @from AND e.EVAL_DT < @to
      AND (@div IS NULL OR e.EVAL_DIV = @div)
) a
LEFT JOIN (
    -- 후보 B SQL (정렬 제외)
    SELECT e.EVAL_ID,
           e.RAW_SCORE,
           CASE WHEN e.EVAL_DIV = '고객' THEN NULL ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
           CASE WHEN e.EVAL_DIV = '고객' THEN e.RAW_SCORE
                WHEN r.CNFM_DT IS NOT NULL THEN e.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0)
           END AS ADJ_SCORE
    FROM TB_EVAL e
    LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
    LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
    LEFT JOIN TB_ADJ_RND r    ON r.ADJ_YR = YEAR(e.EVAL_DT)
    LEFT JOIN TB_EVLTR_ADJ_VAL av
           ON av.ADJ_YR = YEAR(e.EVAL_DT)
          AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
          AND av.EVAL_DIV = e.EVAL_DIV
    WHERE e.EVAL_STAT = '제출'
      AND e.EVAL_DT >= @from AND e.EVAL_DT < @to
      AND (@div IS NULL OR e.EVAL_DIV = @div)
      AND e.RAW_SCORE IS NOT NULL
) b ON b.EVAL_ID = a.EVAL_ID;

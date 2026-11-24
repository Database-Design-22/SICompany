-- =====================================================================
-- 후보 C 측정: SQL 재작성 (LATERAL 조인)
--   기준 SQL은 파생 테이블이 문항별 점수 전체를 먼저 GROUP BY 하므로, 연도로 평가를 몇 건 고르든
--   점수 테이블 전체를 읽는다. 그래서 인덱스(후보 A)를 걸어도 그 부분은 빨라지지 않는다.
--   LATERAL 조인으로 바꾸면 "고른 평가마다" 그 평가의 점수만 PK(EVAL_ID, QSTN_ID)로 찾아 평균을 낸다.
--   테이블 구조는 바꾸지 않는다. 후보 A 인덱스와 함께 쓸 때 효과가 크다.
--
--   기준 SQL은 점수가 있는 평가만 나오므로(내부 조인) 같은 뜻으로 sc.RAW_SCORE IS NOT NULL 을 둔다.
--   (집계 함수만 있는 LATERAL 서브쿼리는 점수가 없어도 NULL 한 행을 돌려주기 때문)
--
-- 실행:
--   cmd /c "docker exec -i si-mysql mysql -uroot -proot1234 --default-character-set=utf8mb4 < docker\experiment\50_query_lateral.sql"
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
JOIN LATERAL (SELECT AVG(s.SCORE) AS RAW_SCORE
              FROM TB_EVAL_QSTN_SCORE s
              WHERE s.EVAL_ID = e.EVAL_ID) sc ON TRUE
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
  AND sc.RAW_SCORE IS NOT NULL
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
JOIN LATERAL (SELECT AVG(s.SCORE) AS RAW_SCORE
              FROM TB_EVAL_QSTN_SCORE s
              WHERE s.EVAL_ID = e.EVAL_ID) sc ON TRUE
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
  AND sc.RAW_SCORE IS NOT NULL
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
    -- 후보 C SQL (정렬 제외)
    SELECT e.EVAL_ID,
           ROUND(sc.RAW_SCORE, 2) AS RAW_SCORE,
           CASE WHEN e.EVAL_DIV = '고객' THEN NULL ELSE COALESCE(av.APLY_ADJ_VAL, 0) END AS APLY_ADJ_VAL,
           CASE WHEN e.EVAL_DIV = '고객' THEN ROUND(sc.RAW_SCORE, 2)
                WHEN r.CNFM_DT IS NOT NULL THEN ROUND(sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0), 2)
           END AS ADJ_SCORE
    FROM TB_EVAL e
    JOIN LATERAL (SELECT AVG(s.SCORE) AS RAW_SCORE
                  FROM TB_EVAL_QSTN_SCORE s
                  WHERE s.EVAL_ID = e.EVAL_ID) sc ON TRUE
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
      AND sc.RAW_SCORE IS NOT NULL
) b ON b.EVAL_ID = a.EVAL_ID;

package com.databasedesign.SiCompany.adjustment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.AdjRound;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvalResult;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvaluateeSummary;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class AdjustmentRepository {

    /** 1. 보정회차 목록 */
    public static final String FIND_ROUNDS = """
            SELECT r.ADJ_YR, r.TNDCY_CALC_PRD, r.MIN_PRJ_CNT, r.MAX_ADJ_WDTH,
                   r.EXEC_DT, r.CNFM_DT, r.EXECR_EMP_ID, x.EMP_NM AS EXECR_NM,
                   (SELECT COUNT(*)
                    FROM TB_EVLTR_ADJ_VAL v
                    WHERE v.ADJ_YR = r.ADJ_YR) AS ADJ_VAL_CNT
            FROM TB_ADJ_RND r
            JOIN TB_EMP x ON x.EMP_ID = r.EXECR_EMP_ID
            ORDER BY r.ADJ_YR DESC
            """;

    /** 2. 연도 선택 목록 (제출된 평가가 있는 연도) */
    public static final String FIND_EVAL_YEARS = """
            SELECT DISTINCT YEAR(EVAL_DT) AS EVAL_YR
            FROM TB_EVAL
            WHERE EVAL_STAT = '제출'
            ORDER BY EVAL_YR DESC
            """;

    /**
     * 3. 평가 결과 조회 (성능 실험 대상. 하나의 SELECT로 둔다)
     * - 원점수 = 문항 점수 평균, 보정연도 = 평가일자의 연도
     * - 적용보정값 = (보정연도, 평가자, 평가구분)의 APLY_ADJ_VAL, 없으면 0. 고객평가는 보정하지 않는다.
     * - 보정점수 = 원점수 - 적용보정값. 그 연도 회차가 확정됐을 때만 값이 있다.
     * 파라미터: 시작일, 끝일(다음 해 1월 1일), 평가구분, 평가구분 (전체면 NULL)
     */
    public static final String FIND_EVAL_RESULTS = """
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
              AND e.EVAL_DT >= ? AND e.EVAL_DT < ?
              AND (? IS NULL OR e.EVAL_DIV = ?)
            ORDER BY te.EMP_NM, e.EVAL_DT, e.EVAL_ID
            """;

    /** 4. 피평가자별 요약 (3번 SQL을 그대로 감싸 계산 기준을 맞춘다) */
    public static final String FIND_EVALUATEE_SUMMARY = """
            SELECT x.EVLTE_EMP_ID, x.EVLTE_EMP_NM,
                   COUNT(*) AS EVAL_CNT,
                   ROUND(AVG(x.RAW_SCORE), 2) AS RAW_AVG,
                   ROUND(AVG(x.ADJ_SCORE), 2) AS ADJ_AVG
            FROM (
            """ + FIND_EVAL_RESULTS + """
            ) x
            GROUP BY x.EVLTE_EMP_ID, x.EVLTE_EMP_NM
            ORDER BY x.EVLTE_EMP_NM
            """;

    /** 5. 회차 확정 (미확정일 때만 바뀐다) */
    public static final String CONFIRM_ROUND = """
            UPDATE TB_ADJ_RND
            SET CNFM_DT = CURDATE()
            WHERE ADJ_YR = ?
              AND CNFM_DT IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<AdjRound> findRounds() {
        return jdbcTemplate.query(FIND_ROUNDS, (rs, i) -> new AdjRound(
                rs.getInt("ADJ_YR"),
                rs.getInt("TNDCY_CALC_PRD"),
                rs.getInt("MIN_PRJ_CNT"),
                rs.getBigDecimal("MAX_ADJ_WDTH"),
                rs.getObject("EXEC_DT", LocalDate.class),
                rs.getObject("CNFM_DT", LocalDate.class),
                rs.getString("EXECR_EMP_ID"),
                rs.getString("EXECR_NM"),
                rs.getInt("ADJ_VAL_CNT")));
    }

    public List<Integer> findEvalYears() {
        return jdbcTemplate.queryForList(FIND_EVAL_YEARS, Integer.class);
    }

    public List<EvalResult> findEvalResults(LocalDate from, LocalDate to, String evalDiv) {
        return jdbcTemplate.query(FIND_EVAL_RESULTS, (rs, i) -> new EvalResult(
                rs.getInt("EVAL_ID"),
                rs.getString("EVAL_DIV"),
                rs.getObject("EVAL_DT", LocalDate.class),
                rs.getString("EVLTE_EMP_ID"),
                rs.getString("EVLTE_EMP_NM"),
                rs.getString("EVLTR_EMP_ID"),
                rs.getString("EVLTR_NM"),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getBigDecimal("RAW_SCORE"),
                rs.getBigDecimal("APLY_ADJ_VAL"),
                rs.getObject("RND_YR", Integer.class),
                rs.getObject("CNFM_DT", LocalDate.class),
                rs.getBigDecimal("ADJ_SCORE")), from, to, evalDiv, evalDiv);
    }

    public List<EvaluateeSummary> findEvaluateeSummary(LocalDate from, LocalDate to, String evalDiv) {
        return jdbcTemplate.query(FIND_EVALUATEE_SUMMARY, (rs, i) -> new EvaluateeSummary(
                rs.getString("EVLTE_EMP_ID"),
                rs.getString("EVLTE_EMP_NM"),
                rs.getInt("EVAL_CNT"),
                rs.getBigDecimal("RAW_AVG"),
                (BigDecimal) rs.getObject("ADJ_AVG")), from, to, evalDiv, evalDiv);
    }

    public int confirmRound(int adjYr) {
        return jdbcTemplate.update(CONFIRM_ROUND, adjYr);
    }
}

package com.databasedesign.SiCompany.adjustment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.AdjRound;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvalResult;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvaluateeSummary;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvltrAdjVal;

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

    /**
     * 보정 계산 공통 부분 (보정연도 하나를 파라미터로 받는다).
     * - r      : 그 회차의 정책값 (성향산정기간, 최소프로젝트수, 최대보정폭, 실행일자)
     * - ev     : 실행일자 기준 최근 N년 안에 제출된 PM·동료 평가와 평가 점수(문항 점수 평균)
     * - evltr  : 평가자·평가구분별 평가건수, 평가프로젝트수, 평균점수
     * - div_avg: 평가구분별 전체 평균
     * 적용보정값 = (평가자 평균 - 유형 평균)을 ±최대보정폭으로 제한, 평가프로젝트수 < 최소프로젝트수면 0
     */
    private static final String ADJ_CALC_CTE = """
            WITH r AS (
                SELECT ADJ_YR, TNDCY_CALC_PRD, MIN_PRJ_CNT, MAX_ADJ_WDTH, EXEC_DT
                FROM TB_ADJ_RND
                WHERE ADJ_YR = ?
            ),
            ev AS (
                SELECT e.EVAL_ID, e.EVAL_DIV, e.PRJ_ID,
                       COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID) AS EVLTR_EMP_ID,
                       AVG(s.SCORE) AS RAW_SCORE
                FROM r
                JOIN TB_EVAL e
                  ON e.EVAL_STAT = '제출'
                 AND e.EVAL_DIV IN ('PM', '동료')
                 AND e.EVAL_DT > DATE_SUB(r.EXEC_DT, INTERVAL r.TNDCY_CALC_PRD YEAR)
                 AND e.EVAL_DT <= r.EXEC_DT
                JOIN TB_EVAL_QSTN_SCORE s ON s.EVAL_ID = e.EVAL_ID
                LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
                LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
                GROUP BY e.EVAL_ID, e.EVAL_DIV, e.PRJ_ID, pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID
            ),
            evltr AS (
                SELECT EVLTR_EMP_ID, EVAL_DIV,
                       COUNT(*) AS EVAL_CNT,
                       COUNT(DISTINCT PRJ_ID) AS EVAL_PRJ_CNT,
                       AVG(RAW_SCORE) AS AVG_SCORE
                FROM ev
                GROUP BY EVLTR_EMP_ID, EVAL_DIV
            ),
            div_avg AS (
                SELECT EVAL_DIV, AVG(RAW_SCORE) AS DIV_AVG
                FROM ev
                GROUP BY EVAL_DIV
            )
            """;

    /** 실행 ①: 보정회차 생성 (실행일자 오늘, 확정 전) */
    public static final String INSERT_ROUND = """
            INSERT INTO TB_ADJ_RND (ADJ_YR, TNDCY_CALC_PRD, MIN_PRJ_CNT, MAX_ADJ_WDTH, EXEC_DT, CNFM_DT, EXECR_EMP_ID)
            VALUES (?, ?, ?, ?, CURDATE(), NULL, ?)
            """;

    /** 실행 ②: 평가자보정값 계산과 저장 (보정연도 파라미터 1개) */
    public static final String INSERT_EVLTR_ADJ_VALS = """
            INSERT INTO TB_EVLTR_ADJ_VAL
                   (ADJ_YR, EVLTR_EMP_ID, EVAL_DIV, EVAL_CNT, EVAL_PRJ_CNT, EVLTR_AVG_SCORE, APLY_ADJ_VAL)
            """ + ADJ_CALC_CTE + """
            SELECT r.ADJ_YR, x.EVLTR_EMP_ID, x.EVAL_DIV, x.EVAL_CNT, x.EVAL_PRJ_CNT,
                   ROUND(x.AVG_SCORE, 2),
                   CASE WHEN x.EVAL_PRJ_CNT < r.MIN_PRJ_CNT THEN 0
                        ELSE LEAST(r.MAX_ADJ_WDTH,
                                   GREATEST(-r.MAX_ADJ_WDTH, ROUND(x.AVG_SCORE - d.DIV_AVG, 1)))
                   END
            FROM evltr x
            JOIN div_avg d ON d.EVAL_DIV = x.EVAL_DIV
            CROSS JOIN r
            """;

    /** 재계산 ①: 정책값 변경 (최소프로젝트수, 최대보정폭만. 확정 전일 때만 바뀐다) */
    public static final String UPDATE_ROUND_POLICY = """
            UPDATE TB_ADJ_RND
            SET MIN_PRJ_CNT = ?, MAX_ADJ_WDTH = ?
            WHERE ADJ_YR = ? AND CNFM_DT IS NULL
            """;

    /**
     * 재계산 ②: 적용보정값만 다시 계산 (보정연도 파라미터 2개).
     * 성향산정기간과 실행일자는 그대로라 평가자 명단은 바뀌지 않으므로 행을 고치기만 한다.
     */
    public static final String UPDATE_EVLTR_ADJ_VALS = ADJ_CALC_CTE + """
            UPDATE TB_EVLTR_ADJ_VAL v
            JOIN evltr x   ON x.EVLTR_EMP_ID = v.EVLTR_EMP_ID AND x.EVAL_DIV = v.EVAL_DIV
            JOIN div_avg d ON d.EVAL_DIV = v.EVAL_DIV
            JOIN r         ON r.ADJ_YR = v.ADJ_YR
            SET v.APLY_ADJ_VAL =
                CASE WHEN x.EVAL_PRJ_CNT < r.MIN_PRJ_CNT THEN 0
                     ELSE LEAST(r.MAX_ADJ_WDTH,
                                GREATEST(-r.MAX_ADJ_WDTH, ROUND(x.AVG_SCORE - d.DIV_AVG, 1)))
                END
            WHERE v.ADJ_YR = ?
            """;

    /** 평가자 성향: 그 연도의 평가자보정값 (보정값이 큰 순 = 후하게 준 평가자가 위) */
    public static final String FIND_EVLTR_ADJ_VALS = """
            SELECT v.EVAL_DIV, v.EVLTR_EMP_ID, e.EMP_NM, v.EVAL_CNT, v.EVAL_PRJ_CNT,
                   v.EVLTR_AVG_SCORE, v.APLY_ADJ_VAL
            FROM TB_EVLTR_ADJ_VAL v
            JOIN TB_EMP e ON e.EMP_ID = v.EVLTR_EMP_ID
            WHERE v.ADJ_YR = ?
            ORDER BY FIELD(v.EVAL_DIV, 'PM', '동료'), v.APLY_ADJ_VAL DESC, e.EMP_NM
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

    public void insertRound(int adjYr, int tndcyCalcPrd, int minPrjCnt, BigDecimal maxAdjWdth, String execrEmpId) {
        jdbcTemplate.update(INSERT_ROUND, adjYr, tndcyCalcPrd, minPrjCnt, maxAdjWdth, execrEmpId);
    }

    /** @return 저장한 평가자보정값 수 */
    public int insertEvltrAdjVals(int adjYr) {
        return jdbcTemplate.update(INSERT_EVLTR_ADJ_VALS, adjYr);
    }

    public int updateRoundPolicy(int adjYr, int minPrjCnt, BigDecimal maxAdjWdth) {
        return jdbcTemplate.update(UPDATE_ROUND_POLICY, minPrjCnt, maxAdjWdth, adjYr);
    }

    /** @return 고친 평가자보정값 수 (값이 같으면 0으로 셀 수 있다) */
    public int updateEvltrAdjVals(int adjYr) {
        return jdbcTemplate.update(UPDATE_EVLTR_ADJ_VALS, adjYr, adjYr);
    }

    public List<EvltrAdjVal> findEvltrAdjVals(int adjYr) {
        return jdbcTemplate.query(FIND_EVLTR_ADJ_VALS, (rs, i) -> new EvltrAdjVal(
                rs.getString("EVAL_DIV"),
                rs.getString("EVLTR_EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getInt("EVAL_CNT"),
                rs.getInt("EVAL_PRJ_CNT"),
                rs.getBigDecimal("EVLTR_AVG_SCORE"),
                rs.getBigDecimal("APLY_ADJ_VAL")), adjYr);
    }

    public int confirmRound(int adjYr) {
        return jdbcTemplate.update(CONFIRM_ROUND, adjYr);
    }
}

package com.databasedesign.SiCompany.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.dashboard.DashboardDtos.ActiveProject;
import com.databasedesign.SiCompany.dashboard.DashboardDtos.CurrentMember;
import com.databasedesign.SiCompany.dashboard.DashboardDtos.IncentiveRow;
import com.databasedesign.SiCompany.dashboard.DashboardDtos.Summary;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    /** 1. 요약: 상태별 프로젝트 수, 재직 개발직원 수, 지금 투입 중인 직원 수 */
    public static final String FIND_SUMMARY = """
            SELECT SUM(p.PRJ_STAT = '진행') AS ACTIVE_CNT,
                   SUM(p.PRJ_STAT = '지연') AS DELAYED_CNT,
                   SUM(p.PRJ_STAT = '종료') AS ENDED_CNT,
                   SUM(p.PRJ_STAT = '중단') AS STOPPED_CNT,
                   (SELECT COUNT(*)
                    FROM TB_DEV_EMP d
                    JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
                    WHERE e.EMPL_STAT = '재직') AS DEV_CNT,
                   (SELECT COUNT(DISTINCT h.EMP_ID)
                    FROM TB_PRJ_HIST h
                    WHERE h.END_DT IS NULL) AS DEPLOYED_CNT
            FROM TB_PRJ p
            """;

    /** 2. 수행 중인 프로젝트: 현재 투입 인원, 누적 투입 인원, 일정 경과 (지연이 먼저, 예정 종료가 가까운 순) */
    public static final String FIND_ACTIVE_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_NM, p.STRT_DT, p.SCHD_END_DT,
                   pe.EMP_NM AS PM_NM,
                   COUNT(DISTINCT CASE WHEN h.END_DT IS NULL THEN h.EMP_ID END) AS CUR_CNT,
                   COUNT(DISTINCT h.EMP_ID) AS TOTAL_CNT,
                   DATEDIFF(CURDATE(), p.STRT_DT) AS ELAPSED_DAYS,
                   DATEDIFF(p.SCHD_END_DT, p.STRT_DT) AS PLAN_DAYS
            FROM TB_PRJ p
            JOIN TB_CLIENT c         ON c.CLIENT_ID = p.CLIENT_ID
            LEFT JOIN TB_PRJ_PTCP pm ON pm.PRJ_ID = p.PRJ_ID AND pm.ROLE_ID = 'PM'
            LEFT JOIN TB_EMP pe      ON pe.EMP_ID = pm.EMP_ID
            LEFT JOIN TB_PRJ_HIST h  ON h.PRJ_ID = p.PRJ_ID
            WHERE p.PRJ_STAT IN ('진행', '지연')
            GROUP BY p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_NM, p.STRT_DT, p.SCHD_END_DT, pe.EMP_NM
            ORDER BY FIELD(p.PRJ_STAT, '지연', '진행'), p.SCHD_END_DT
            """;

    /** 3. 수행 중인 프로젝트에 지금 투입 중인 직원 (직무, 기술등급, 투입 일수) */
    public static final String FIND_CURRENT_MEMBERS = """
            SELECT h.PRJ_ID, e.EMP_ID, e.EMP_NM, pt.ROLE_ID, r.ROLE_NM, g.GRD_NM,
                   h.STRT_DT, DATEDIFF(CURDATE(), h.STRT_DT) + 1 AS DAYS
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ p            ON p.PRJ_ID = h.PRJ_ID AND p.PRJ_STAT IN ('진행', '지연')
            JOIN TB_PRJ_PTCP pt      ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_ROLE r           ON r.ROLE_ID = pt.ROLE_ID
            JOIN TB_EMP e            ON e.EMP_ID = h.EMP_ID
            JOIN TB_DEV_EMP d        ON d.EMP_ID = h.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            WHERE h.END_DT IS NULL
            ORDER BY h.PRJ_ID, FIELD(pt.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
            """;

    /** 4. 인센티브 연도 선택 목록 (제출된 평가가 있는 연도와 올해) */
    public static final String FIND_INCENTIVE_YEARS = """
            SELECT YEAR(EVAL_DT) AS YR FROM TB_EVAL WHERE EVAL_STAT = '제출'
            UNION
            SELECT YEAR(CURDATE())
            ORDER BY YR DESC
            """;

    /** 5. 그 연도 보정회차의 확정일자 (없거나 확정 전이면 보정점수 대신 원점수만 쓴다) */
    public static final String FIND_ROUND_CNFM_DT = """
            SELECT CNFM_DT
            FROM TB_ADJ_RND
            WHERE ADJ_YR = ?
            """;

    /**
     * 6. 인센티브 기초자료 (재직 개발직원별, 연도 파라미터 1개)
     * - 투입: 그 연도와 겹친 투입 이력의 프로젝트 수, 연도 안의 투입 일수, 맡은 직무
     * - 평가: 그 연도에 제출된 평가(피평가자 기준). PM·동료는 원점수 평균과 보정점수 평균(회차 확정 때만), 고객은 원점수 평균
     * - 교체: 그 연도에 수용된 교체 요청에서 교체 대상이 된 수
     */
    public static final String FIND_INCENTIVE_ROWS = """
            WITH y AS (
                SELECT CAST(? AS SIGNED) AS YR,
                       MAKEDATE(CAST(? AS SIGNED), 1) AS Y_START,
                       MAKEDATE(CAST(? AS SIGNED) + 1, 1) - INTERVAL 1 DAY AS Y_END
            ),
            sc AS (
                SELECT EVAL_ID, AVG(SCORE) AS RAW_SCORE
                FROM TB_EVAL_QSTN_SCORE
                GROUP BY EVAL_ID
            ),
            ev AS (
                SELECT e.EVLTE_EMP_ID AS EMP_ID, e.EVAL_DIV, sc.RAW_SCORE,
                       CASE WHEN r.CNFM_DT IS NOT NULL
                            THEN sc.RAW_SCORE - COALESCE(av.APLY_ADJ_VAL, 0) END AS ADJ_SCORE
                FROM y
                JOIN TB_EVAL e ON e.EVAL_STAT = '제출' AND YEAR(e.EVAL_DT) = y.YR
                JOIN sc ON sc.EVAL_ID = e.EVAL_ID
                LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
                LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
                LEFT JOIN TB_ADJ_RND r    ON r.ADJ_YR = y.YR
                LEFT JOIN TB_EVLTR_ADJ_VAL av
                       ON av.ADJ_YR = y.YR
                      AND av.EVLTR_EMP_ID = COALESCE(pm.EVLTR_EMP_ID, pe.EVLTR_EMP_ID)
                      AND av.EVAL_DIV = e.EVAL_DIV
            ),
            dep AS (
                SELECT h.EMP_ID,
                       COUNT(DISTINCT h.PRJ_ID) AS PRJ_CNT,
                       SUM(DATEDIFF(LEAST(COALESCE(h.END_DT, CURDATE()), y.Y_END),
                                    GREATEST(h.STRT_DT, y.Y_START)) + 1) AS DEPLOY_DAYS,
                       GROUP_CONCAT(DISTINCT pt.ROLE_ID ORDER BY pt.ROLE_ID SEPARATOR ', ') AS ROLES
                FROM y
                JOIN TB_PRJ_HIST h
                  ON h.STRT_DT <= y.Y_END
                 AND COALESCE(h.END_DT, CURDATE()) >= y.Y_START
                 AND h.STRT_DT <= CURDATE()
                JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
                GROUP BY h.EMP_ID
            ),
            repl AS (
                SELECT h.EMP_ID, COUNT(*) AS REPL_CNT
                FROM y
                JOIN TB_STAFF_REPL_REQ q ON q.PROC_STAT = '수용' AND YEAR(q.PROC_DT) = y.YR
                JOIN TB_PRJ_HIST h       ON h.PRJ_HIST_ID = q.REPL_TRGT_HIST_ID
                GROUP BY h.EMP_ID
            )
            SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
                   COALESCE(dep.PRJ_CNT, 0) AS PRJ_CNT,
                   COALESCE(dep.DEPLOY_DAYS, 0) AS DEPLOY_DAYS,
                   dep.ROLES,
                   COUNT(ev.EMP_ID) AS EVAL_CNT,
                   ROUND(AVG(CASE WHEN ev.EVAL_DIV IN ('PM', '동료') THEN ev.RAW_SCORE END), 2) AS RAW_AVG,
                   ROUND(AVG(CASE WHEN ev.EVAL_DIV IN ('PM', '동료') THEN ev.ADJ_SCORE END), 2) AS ADJ_AVG,
                   ROUND(AVG(CASE WHEN ev.EVAL_DIV = '고객' THEN ev.RAW_SCORE END), 2) AS CUST_AVG,
                   COALESCE(repl.REPL_CNT, 0) AS REPL_CNT
            FROM TB_DEV_EMP d
            JOIN TB_EMP e            ON e.EMP_ID = d.EMP_ID AND e.EMPL_STAT = '재직'
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            LEFT JOIN dep            ON dep.EMP_ID = d.EMP_ID
            LEFT JOIN repl           ON repl.EMP_ID = d.EMP_ID
            LEFT JOIN ev             ON ev.EMP_ID = d.EMP_ID
            GROUP BY d.EMP_ID, e.EMP_NM, g.GRD_NM, dep.PRJ_CNT, dep.DEPLOY_DAYS, dep.ROLES, repl.REPL_CNT
            ORDER BY e.EMP_NM
            """;

    private final JdbcTemplate jdbcTemplate;

    public Summary findSummary() {
        return jdbcTemplate.queryForObject(FIND_SUMMARY, (rs, i) -> new Summary(
                rs.getInt("ACTIVE_CNT"),
                rs.getInt("DELAYED_CNT"),
                rs.getInt("ENDED_CNT"),
                rs.getInt("STOPPED_CNT"),
                rs.getInt("DEV_CNT"),
                rs.getInt("DEPLOYED_CNT")));
    }

    public List<ActiveProject> findActiveProjects() {
        return jdbcTemplate.query(FIND_ACTIVE_PROJECTS, (rs, i) -> new ActiveProject(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getString("CLIENT_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("SCHD_END_DT", LocalDate.class),
                rs.getString("PM_NM"),
                rs.getInt("CUR_CNT"),
                rs.getInt("TOTAL_CNT"),
                rs.getInt("ELAPSED_DAYS"),
                rs.getInt("PLAN_DAYS")));
    }

    public List<CurrentMember> findCurrentMembers() {
        return jdbcTemplate.query(FIND_CURRENT_MEMBERS, (rs, i) -> new CurrentMember(
                rs.getString("PRJ_ID"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getString("GRD_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getInt("DAYS")));
    }

    public List<Integer> findIncentiveYears() {
        return jdbcTemplate.queryForList(FIND_INCENTIVE_YEARS, Integer.class);
    }

    /** @return 회차가 없으면 empty, 확정 전이면 Optional.of(null)은 쓰지 않고 empty */
    public Optional<LocalDate> findRoundCnfmDt(int year) {
        return jdbcTemplate.query(FIND_ROUND_CNFM_DT, (rs, i) -> rs.getObject("CNFM_DT", LocalDate.class), year)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    public boolean existsRound(int year) {
        return !jdbcTemplate.query(FIND_ROUND_CNFM_DT, (rs, i) -> 1, year).isEmpty();
    }

    public List<IncentiveRow> findIncentiveRows(int year) {
        return jdbcTemplate.query(FIND_INCENTIVE_ROWS, (rs, i) -> new IncentiveRow(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("GRD_NM"),
                rs.getInt("PRJ_CNT"),
                rs.getInt("DEPLOY_DAYS"),
                rs.getString("ROLES"),
                rs.getInt("EVAL_CNT"),
                rs.getBigDecimal("RAW_AVG"),
                (BigDecimal) rs.getObject("ADJ_AVG"),
                rs.getBigDecimal("CUST_AVG"),
                rs.getInt("REPL_CNT")), year, year, year);
    }
}

package com.databasedesign.SiCompany.project;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.project.ProjectDtos.EvalCount;
import com.databasedesign.SiCompany.project.ProjectDtos.EvalPair;
import com.databasedesign.SiCompany.project.ProjectDtos.MemberHist;
import com.databasedesign.SiCompany.project.ProjectDtos.ProjectRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProjectRepository {

    /** 1. 프로젝트 목록 (상태로 거르기, 전체면 NULL. 진행·지연이 먼저) */
    public static final String FIND_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.STRT_DT, p.SCHD_END_DT, p.END_DT, p.PRJ_STAT,
                   c.CLIENT_NM, m.MGR_NM,
                   pm.EMP_ID AS PM_EMP_ID, pe.EMP_NM AS PM_EMP_NM,
                   (SELECT COUNT(*) FROM TB_PRJ_PTCP x
                    WHERE x.PRJ_ID = p.PRJ_ID) AS MEMBER_CNT,
                   (SELECT COUNT(*) FROM TB_PRJ_HIST h
                    WHERE h.PRJ_ID = p.PRJ_ID AND h.END_DT IS NULL) AS OPEN_HIST_CNT
            FROM TB_PRJ p
            JOIN TB_CLIENT c          ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_CLIENT_MGR m      ON m.CLIENT_ID = p.CLIENT_ID AND m.MGR_ID = p.MGR_ID
            LEFT JOIN TB_PRJ_PTCP pm  ON pm.PRJ_ID = p.PRJ_ID AND pm.ROLE_ID = 'PM'
            LEFT JOIN TB_EMP pe       ON pe.EMP_ID = pm.EMP_ID
            WHERE (? IS NULL OR p.PRJ_STAT = ?)
            ORDER BY FIELD(p.PRJ_STAT, '진행', '지연', '종료', '중단'), p.PRJ_ID DESC
            """;

    /** 2. 프로젝트 한 건 (1번과 같은 열) */
    public static final String FIND_PROJECT = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.STRT_DT, p.SCHD_END_DT, p.END_DT, p.PRJ_STAT,
                   c.CLIENT_NM, m.MGR_NM,
                   pm.EMP_ID AS PM_EMP_ID, pe.EMP_NM AS PM_EMP_NM,
                   (SELECT COUNT(*) FROM TB_PRJ_PTCP x
                    WHERE x.PRJ_ID = p.PRJ_ID) AS MEMBER_CNT,
                   (SELECT COUNT(*) FROM TB_PRJ_HIST h
                    WHERE h.PRJ_ID = p.PRJ_ID AND h.END_DT IS NULL) AS OPEN_HIST_CNT
            FROM TB_PRJ p
            JOIN TB_CLIENT c          ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_CLIENT_MGR m      ON m.CLIENT_ID = p.CLIENT_ID AND m.MGR_ID = p.MGR_ID
            LEFT JOIN TB_PRJ_PTCP pm  ON pm.PRJ_ID = p.PRJ_ID AND pm.ROLE_ID = 'PM'
            LEFT JOIN TB_EMP pe       ON pe.EMP_ID = pm.EMP_ID
            WHERE p.PRJ_ID = ?
            """;

    /** 3. 팀원과 투입 이력 (재투입이면 한 사람이 여러 줄) */
    public static final String FIND_MEMBER_HISTS = """
            SELECT h.PRJ_HIST_ID, h.EMP_ID, e.EMP_NM, pt.ROLE_ID, r.ROLE_NM,
                   h.STRT_DT, h.END_DT, h.WTHDR_RSN_ID, w.WTHDR_RSN_NM
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ_PTCP pt      ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_ROLE r           ON r.ROLE_ID = pt.ROLE_ID
            JOIN TB_EMP e            ON e.EMP_ID = h.EMP_ID
            LEFT JOIN TB_WTHDR_RSN w ON w.WTHDR_RSN_ID = h.WTHDR_RSN_ID
            WHERE h.PRJ_ID = ?
            ORDER BY FIELD(pt.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM, h.STRT_DT
            """;

    /** 4. 이 프로젝트의 평가 수 (평가구분, 상태별) */
    public static final String FIND_EVAL_COUNTS = """
            SELECT EVAL_DIV, EVAL_STAT, COUNT(*) AS CNT
            FROM TB_EVAL
            WHERE PRJ_ID = ?
            GROUP BY EVAL_DIV, EVAL_STAT
            ORDER BY FIELD(EVAL_DIV, 'PM', '동료', '고객'), EVAL_STAT
            """;

    /** 5. 검사: 종료일자는 모든 투입의 시작일자와 이미 닫힌 종료일자 이후여야 한다 */
    public static final String FIND_LAST_HIST_DATE = """
            SELECT MAX(GREATEST(STRT_DT, COALESCE(END_DT, STRT_DT)))
            FROM TB_PRJ_HIST
            WHERE PRJ_ID = ?
            """;

    /** 6. 검사: 아직 처리하지 않은 교체 요청 (있으면 먼저 수용·철회해야 한다) */
    public static final String COUNT_OPEN_REPL_REQUESTS = """
            SELECT COUNT(*)
            FROM TB_STAFF_REPL_REQ r
            JOIN TB_PRJ_HIST h ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
            WHERE h.PRJ_ID = ? AND r.PROC_STAT = '접수'
            """;

    /** 종료 ①: 프로젝트 상태 '종료', 종료일자 (진행·지연일 때만 바뀐다) */
    public static final String END_PROJECT = """
            UPDATE TB_PRJ
            SET PRJ_STAT = '종료', END_DT = ?
            WHERE PRJ_ID = ? AND PRJ_STAT IN ('진행', '지연')
            """;

    /** 중단 ①: 프로젝트 상태 '중단', 종료일자 (진행·지연일 때만 바뀐다) */
    public static final String STOP_PROJECT = """
            UPDATE TB_PRJ
            SET PRJ_STAT = '중단', END_DT = ?
            WHERE PRJ_ID = ? AND PRJ_STAT IN ('진행', '지연')
            """;

    /** 종료·중단 ②: 열린 투입 이력을 닫는다 (종료는 'PLAN', 중단은 'STOP') */
    public static final String CLOSE_OPEN_HISTS = """
            UPDATE TB_PRJ_HIST
            SET END_DT = ?, WTHDR_RSN_ID = ?
            WHERE PRJ_ID = ? AND END_DT IS NULL
            """;

    /**
     * 종료 ③: 만들 평가 (모든 이력이 닫힌 뒤에 실행한다)
     * - PM평가: PM → PM이 아닌 팀원 전원
     * - 동료평가: PM이 아닌 직원 → 투입 기간이 하루 이상 겹친 다른 직원 (PM 포함, 재투입은 한 쌍만 겹쳐도 됨)
     * - 평가자는 재직자만
     */
    public static final String FIND_EVAL_PAIRS = """
            SELECT 'PM' AS EVAL_DIV, pm.EMP_ID AS EVLTR_EMP_ID, t.EMP_ID AS EVLTE_EMP_ID
            FROM TB_PRJ_PTCP pm
            JOIN TB_PRJ_PTCP t ON t.PRJ_ID = pm.PRJ_ID AND t.ROLE_ID <> 'PM'
            JOIN TB_EMP ev     ON ev.EMP_ID = pm.EMP_ID AND ev.EMPL_STAT = '재직'
            WHERE pm.PRJ_ID = ? AND pm.ROLE_ID = 'PM'
            UNION
            SELECT '동료', a.EMP_ID, b.EMP_ID
            FROM TB_PRJ_PTCP pa
            JOIN TB_PRJ_HIST a ON a.EMP_ID = pa.EMP_ID AND a.PRJ_ID = pa.PRJ_ID
            JOIN TB_PRJ_HIST b ON b.PRJ_ID = a.PRJ_ID AND b.EMP_ID <> a.EMP_ID
            JOIN TB_EMP ev     ON ev.EMP_ID = a.EMP_ID AND ev.EMPL_STAT = '재직'
            WHERE pa.PRJ_ID = ? AND pa.ROLE_ID <> 'PM'
              AND GREATEST(a.STRT_DT, b.STRT_DT) <= LEAST(a.END_DT, b.END_DT)
            ORDER BY 1, 2, 3
            """;

    /** 종료 ④-1: 평가 (슈퍼타입) '작성중' */
    public static final String INSERT_EVAL = """
            INSERT INTO TB_EVAL (EVAL_DIV, EVAL_STAT, EVAL_DT, EVLTE_EMP_ID, PRJ_ID)
            VALUES (?, '작성중', NULL, ?, ?)
            """;

    /** 종료 ④-2: PM평가 (서브타입) */
    public static final String INSERT_PM_EVAL = """
            INSERT INTO TB_PM_EVAL (EVAL_ID, EVLTR_EMP_ID, PRJ_ID)
            VALUES (?, ?, ?)
            """;

    /** 종료 ④-2: 동료평가 (서브타입) */
    public static final String INSERT_PEER_EVAL = """
            INSERT INTO TB_PEER_EVAL (EVAL_ID, EVLTR_EMP_ID, PRJ_ID)
            VALUES (?, ?, ?)
            """;

    /** 중단 ③: 그 프로젝트의 평가메모 삭제 (중단 프로젝트는 평가하지 않는다) */
    public static final String DELETE_PROJECT_MEMOS = """
            DELETE FROM TB_EVAL_MEMO
            WHERE PRJ_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<ProjectRow> findProjects(String prjStat) {
        return jdbcTemplate.query(FIND_PROJECTS, (rs, i) -> toProjectRow(rs), prjStat, prjStat);
    }

    public Optional<ProjectRow> findProject(String prjId) {
        return jdbcTemplate.query(FIND_PROJECT, (rs, i) -> toProjectRow(rs), prjId).stream().findFirst();
    }

    public List<MemberHist> findMemberHists(String prjId) {
        return jdbcTemplate.query(FIND_MEMBER_HISTS, (rs, i) -> new MemberHist(
                rs.getInt("PRJ_HIST_ID"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("END_DT", LocalDate.class),
                rs.getString("WTHDR_RSN_ID"),
                rs.getString("WTHDR_RSN_NM")), prjId);
    }

    public List<EvalCount> findEvalCounts(String prjId) {
        return jdbcTemplate.query(FIND_EVAL_COUNTS, (rs, i) -> new EvalCount(
                rs.getString("EVAL_DIV"),
                rs.getString("EVAL_STAT"),
                rs.getInt("CNT")), prjId);
    }

    /** @return 투입 이력이 없으면 null */
    public LocalDate findLastHistDate(String prjId) {
        return jdbcTemplate.queryForObject(FIND_LAST_HIST_DATE, LocalDate.class, prjId);
    }

    public int countOpenReplRequests(String prjId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_OPEN_REPL_REQUESTS, Integer.class, prjId);
        return count == null ? 0 : count;
    }

    public int endProject(String prjId, LocalDate endDt) {
        return jdbcTemplate.update(END_PROJECT, endDt, prjId);
    }

    public int stopProject(String prjId, LocalDate endDt) {
        return jdbcTemplate.update(STOP_PROJECT, endDt, prjId);
    }

    /** @return 닫은 이력 수 */
    public int closeOpenHists(String prjId, LocalDate endDt, String wthdrRsnId) {
        return jdbcTemplate.update(CLOSE_OPEN_HISTS, endDt, wthdrRsnId, prjId);
    }

    public List<EvalPair> findEvalPairs(String prjId) {
        return jdbcTemplate.query(FIND_EVAL_PAIRS, (rs, i) -> new EvalPair(
                rs.getString("EVAL_DIV"),
                rs.getString("EVLTR_EMP_ID"),
                rs.getString("EVLTE_EMP_ID")), prjId, prjId);
    }

    /** @return 새 평가ID */
    public int insertEval(String evalDiv, String evlteEmpId, String prjId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_EVAL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, evalDiv);
            ps.setString(2, evlteEmpId);
            ps.setString(3, prjId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void insertPmEval(int evalId, String evltrEmpId, String prjId) {
        jdbcTemplate.update(INSERT_PM_EVAL, evalId, evltrEmpId, prjId);
    }

    public void insertPeerEval(int evalId, String evltrEmpId, String prjId) {
        jdbcTemplate.update(INSERT_PEER_EVAL, evalId, evltrEmpId, prjId);
    }

    /** @return 삭제한 메모 수 */
    public int deleteProjectMemos(String prjId) {
        return jdbcTemplate.update(DELETE_PROJECT_MEMOS, prjId);
    }

    private static ProjectRow toProjectRow(ResultSet rs) throws SQLException {
        return new ProjectRow(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("SCHD_END_DT", LocalDate.class),
                rs.getObject("END_DT", LocalDate.class),
                rs.getString("PRJ_STAT"),
                rs.getString("CLIENT_NM"),
                rs.getString("MGR_NM"),
                rs.getString("PM_EMP_ID"),
                rs.getString("PM_EMP_NM"),
                rs.getInt("MEMBER_CNT"),
                rs.getInt("OPEN_HIST_CNT"));
    }
}

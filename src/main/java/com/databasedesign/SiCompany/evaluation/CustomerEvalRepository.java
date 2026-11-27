package com.databasedesign.SiCompany.evaluation;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.CustEval;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.CustTarget;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.Manager;
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.PmProject;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CustomerEvalRepository {

    /** 1. 내가 PM인 종료 프로젝트 (고객평가 의뢰·제출 수) */
    public static final String FIND_MY_PM_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.END_DT, p.CLIENT_ID, c.CLIENT_NM,
                   (SELECT COUNT(*) FROM TB_PRJ_PTCP t
                    WHERE t.PRJ_ID = p.PRJ_ID) AS MEMBER_CNT,
                   (SELECT COUNT(*) FROM TB_EVAL e
                    WHERE e.PRJ_ID = p.PRJ_ID AND e.EVAL_DIV = '고객') AS REQ_CNT,
                   (SELECT COUNT(*) FROM TB_EVAL e
                    WHERE e.PRJ_ID = p.PRJ_ID AND e.EVAL_DIV = '고객' AND e.EVAL_STAT = '제출') AS SUBMIT_CNT
            FROM TB_PRJ_PTCP me
            JOIN TB_PRJ p    ON p.PRJ_ID = me.PRJ_ID
            JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
            WHERE me.EMP_ID = ? AND me.ROLE_ID = 'PM' AND p.PRJ_STAT = '종료'
            ORDER BY p.END_DT DESC, p.PRJ_ID DESC
            """;

    /** 2. 고객평가 대상: 그 프로젝트 참여자 전원과 고객평가 진행 상태 */
    public static final String FIND_TARGETS = """
            SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
                   ev.EVAL_ID, ev.EVAL_STAT, ev.EVAL_DT, ce.REQ_DT, ce.COLL_DT, m.MGR_NM,
                   (SELECT COUNT(*) FROM TB_EVAL_QSTN_SCORE s
                    WHERE s.EVAL_ID = ev.EVAL_ID) AS SCORE_CNT
            FROM TB_PRJ_PTCP t
            JOIN TB_EMP e  ON e.EMP_ID = t.EMP_ID
            JOIN TB_ROLE r ON r.ROLE_ID = t.ROLE_ID
            LEFT JOIN TB_EVAL ev
                   ON ev.PRJ_ID = t.PRJ_ID AND ev.EVLTE_EMP_ID = t.EMP_ID AND ev.EVAL_DIV = '고객'
            LEFT JOIN TB_CUST_EVAL ce  ON ce.EVAL_ID = ev.EVAL_ID
            LEFT JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = ce.CLIENT_ID AND m.MGR_ID = ce.EVAL_MGR_ID
            WHERE t.PRJ_ID = ?
            ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
            """;

    /** 3. 그 발주처의 담당자 (평가담당자는 프로젝트 담당자가 아니어도 된다) */
    public static final String FIND_MANAGERS = """
            SELECT MGR_ID, MGR_NM
            FROM TB_CLIENT_MGR
            WHERE CLIENT_ID = ?
            ORDER BY MGR_NM
            """;

    /** 의뢰 ①: 이미 의뢰한 고객평가가 있는지 (피평가자당 1건) */
    public static final String COUNT_CUST_EVAL = """
            SELECT COUNT(*)
            FROM TB_EVAL
            WHERE PRJ_ID = ? AND EVLTE_EMP_ID = ? AND EVAL_DIV = '고객'
            """;

    /** 의뢰 ②: 평가 (슈퍼타입) '작성중' */
    public static final String INSERT_EVAL = """
            INSERT INTO TB_EVAL (EVAL_DIV, EVAL_STAT, EVAL_DT, EVLTE_EMP_ID, PRJ_ID)
            VALUES ('고객', '작성중', NULL, ?, ?)
            """;

    /** 의뢰 ③: 고객평가 (서브타입) 의뢰일자 오늘, 회수 전 */
    public static final String INSERT_CUST_EVAL = """
            INSERT INTO TB_CUST_EVAL (EVAL_ID, CLIENT_ID, EVAL_MGR_ID, REQ_DT, COLL_DT)
            VALUES (?, ?, ?, CURDATE(), NULL)
            """;

    /** 회수 ①: 고객평가 한 건 */
    public static final String FIND_CUST_EVAL = """
            SELECT e.EVAL_ID, e.PRJ_ID, e.EVAL_STAT, ce.REQ_DT, ce.COLL_DT
            FROM TB_EVAL e
            JOIN TB_CUST_EVAL ce ON ce.EVAL_ID = e.EVAL_ID
            WHERE e.EVAL_ID = ?
            """;

    /** 회수 ②: 회수일자 기록 */
    public static final String UPDATE_COLL_DT = """
            UPDATE TB_CUST_EVAL
            SET COLL_DT = ?
            WHERE EVAL_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<PmProject> findMyPmProjects(String empId) {
        return jdbcTemplate.query(FIND_MY_PM_PROJECTS, (rs, i) -> new PmProject(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getObject("END_DT", LocalDate.class),
                rs.getInt("CLIENT_ID"),
                rs.getString("CLIENT_NM"),
                rs.getInt("MEMBER_CNT"),
                rs.getInt("REQ_CNT"),
                rs.getInt("SUBMIT_CNT")), empId);
    }

    public List<CustTarget> findTargets(String prjId) {
        return jdbcTemplate.query(FIND_TARGETS, (rs, i) -> new CustTarget(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getObject("EVAL_ID", Integer.class),
                rs.getString("EVAL_STAT"),
                rs.getObject("EVAL_DT", LocalDate.class),
                rs.getObject("REQ_DT", LocalDate.class),
                rs.getObject("COLL_DT", LocalDate.class),
                rs.getString("MGR_NM"),
                rs.getInt("SCORE_CNT")), prjId);
    }

    public List<Manager> findManagers(int clientId) {
        return jdbcTemplate.query(FIND_MANAGERS, (rs, i) -> new Manager(rs.getInt("MGR_ID"), rs.getString("MGR_NM")), clientId);
    }

    public boolean existsCustEval(String prjId, String empId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_CUST_EVAL, Integer.class, prjId, empId);
        return count != null && count > 0;
    }

    /** @return 새 평가ID */
    public int insertEval(String evlteEmpId, String prjId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_EVAL, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, evlteEmpId);
            ps.setString(2, prjId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void insertCustEval(int evalId, int clientId, int mgrId) {
        jdbcTemplate.update(INSERT_CUST_EVAL, evalId, clientId, mgrId);
    }

    public Optional<CustEval> findCustEval(int evalId) {
        return jdbcTemplate.query(FIND_CUST_EVAL, (rs, i) -> new CustEval(
                rs.getInt("EVAL_ID"),
                rs.getString("PRJ_ID"),
                rs.getString("EVAL_STAT"),
                rs.getObject("REQ_DT", LocalDate.class),
                rs.getObject("COLL_DT", LocalDate.class)), evalId).stream().findFirst();
    }

    public int updateCollDt(int evalId, LocalDate collDt) {
        return jdbcTemplate.update(UPDATE_COLL_DT, collDt, evalId);
    }
}

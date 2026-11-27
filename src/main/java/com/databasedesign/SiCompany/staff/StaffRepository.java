package com.databasedesign.SiCompany.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.staff.StaffDtos.Code;
import com.databasedesign.SiCompany.staff.StaffDtos.DraftEval;
import com.databasedesign.SiCompany.staff.StaffDtos.RetireImpact;
import com.databasedesign.SiCompany.staff.StaffDtos.StaffDetail;
import com.databasedesign.SiCompany.staff.StaffDtos.StaffRow;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class StaffRepository {

    /** 1. 직원 목록 (재직상태, 이름·직원ID로 거르기. 전체면 NULL) */
    public static final String FIND_STAFF = """
            SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.EMPL_STAT, e.HIRE_DT, e.RTR_DT, e.DSTR_DT,
                   a.AUTH_NM, g.GRD_NM, dp.DEPT_NM,
                   (SELECT COUNT(*) FROM TB_PRJ_HIST h
                    WHERE h.EMP_ID = e.EMP_ID AND h.END_DT IS NULL) AS OPEN_HIST_CNT
            FROM TB_EMP e
            JOIN TB_AUTH a           ON a.AUTH_ID = e.AUTH_ID
            LEFT JOIN TB_DEV_EMP d   ON d.EMP_ID = e.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            LEFT JOIN TB_NDEV_EMP n  ON n.EMP_ID = e.EMP_ID
            LEFT JOIN TB_DEPT dp     ON dp.DEPT_ID = n.DEPT_ID
            WHERE (? IS NULL OR e.EMPL_STAT = ?)
              AND (? IS NULL OR e.EMP_NM LIKE CONCAT('%', ?, '%') OR e.EMP_ID = ?)
            ORDER BY FIELD(e.EMPL_STAT, '재직', '퇴사'), e.EMP_NM, e.EMP_ID
            """;

    /** 2. 직원 한 명 */
    public static final String FIND_STAFF_DETAIL = """
            SELECT e.EMP_ID, e.EMP_NM, e.ENG_NM, e.LAST_EDU, e.EMP_DIV, e.EMPL_STAT,
                   e.HIRE_DT, e.RTR_DT, e.DSTR_DT, e.AUTH_ID, a.AUTH_NM,
                   d.GRD_ID, n.DEPT_ID, dp.DEPT_NM
            FROM TB_EMP e
            JOIN TB_AUTH a          ON a.AUTH_ID = e.AUTH_ID
            LEFT JOIN TB_DEV_EMP d  ON d.EMP_ID = e.EMP_ID
            LEFT JOIN TB_NDEV_EMP n ON n.EMP_ID = e.EMP_ID
            LEFT JOIN TB_DEPT dp    ON dp.DEPT_ID = n.DEPT_ID
            WHERE e.EMP_ID = ?
            """;

    /** 3. 코드 목록: 기술등급, 부서 */
    public static final String FIND_GRADES = """
            SELECT GRD_ID, GRD_NM
            FROM TB_SKILL_GRD
            ORDER BY FIELD(GRD_ID, 'JR', 'MD', 'SR', 'EX')
            """;

    public static final String FIND_DEPTS = """
            SELECT DEPT_ID, DEPT_NM
            FROM TB_DEPT
            ORDER BY DEPT_NM
            """;

    /** 퇴사 처리 ①: 함께 바뀌는 것 미리 세기 (직원ID 파라미터 6개) */
    public static final String FIND_RETIRE_IMPACT = """
            SELECT
              (SELECT COUNT(*) FROM TB_PRJ_HIST h
               WHERE h.EMP_ID = ? AND h.END_DT IS NULL) AS OPEN_HIST_CNT,
              (SELECT COUNT(*) FROM TB_EVAL e
               LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
               LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
               WHERE e.EVAL_STAT = '작성중'
                 AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)) AS DRAFT_EVAL_CNT,
              (SELECT COUNT(*) FROM TB_EVAL_MEMO m
               WHERE m.WRTR_EMP_ID = ?) AS MEMO_CNT,
              (SELECT COUNT(*) FROM TB_STAFF_REPL_REQ r
               JOIN TB_PRJ_HIST h ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
               WHERE h.EMP_ID = ? AND r.PROC_STAT = '접수') AS OPEN_REQ_CNT,
              (SELECT MAX(GREATEST(h.STRT_DT, COALESCE(h.END_DT, h.STRT_DT)))
               FROM TB_PRJ_HIST h
               WHERE h.EMP_ID = ?) AS LAST_HIST_DATE
            """;

    /** 퇴사 처리 ②: 재직상태 '퇴사', 퇴사일자 (재직일 때만 바뀐다) */
    public static final String RETIRE_EMP = """
            UPDATE TB_EMP
            SET EMPL_STAT = '퇴사', RTR_DT = ?
            WHERE EMP_ID = ? AND EMPL_STAT = '재직'
            """;

    /** 퇴사 처리 ③: 열린 투입 이력을 퇴사일자, 철수사유 'RET'(퇴사)로 닫는다 */
    public static final String CLOSE_OPEN_HISTS = """
            UPDATE TB_PRJ_HIST
            SET END_DT = ?, WTHDR_RSN_ID = 'RET'
            WHERE EMP_ID = ? AND END_DT IS NULL
            """;

    /** 퇴사 처리 ④: 그 직원이 평가자인 '작성중' 평가 (점수, 내용과 함께 지운다) */
    public static final String FIND_DRAFT_EVALS = """
            SELECT e.EVAL_ID, e.EVAL_DIV
            FROM TB_EVAL e
            LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
            LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
            WHERE e.EVAL_STAT = '작성중'
              AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
            """;

    /** 퇴사 처리 ⑤: 평가 한 건 지우기 (자식 → 서브타입 → 슈퍼타입 순서) */
    public static final String DELETE_EVAL_SCORES = """
            DELETE FROM TB_EVAL_QSTN_SCORE
            WHERE EVAL_ID = ?
            """;

    public static final String DELETE_EVAL_CONTENTS = """
            DELETE FROM TB_EVAL_CN
            WHERE EVAL_ID = ?
            """;

    public static final String DELETE_PM_EVAL = """
            DELETE FROM TB_PM_EVAL
            WHERE EVAL_ID = ?
            """;

    public static final String DELETE_PEER_EVAL = """
            DELETE FROM TB_PEER_EVAL
            WHERE EVAL_ID = ?
            """;

    public static final String DELETE_EVAL = """
            DELETE FROM TB_EVAL
            WHERE EVAL_ID = ?
            """;

    /** 퇴사 처리 ⑥: 그 직원이 쓴 평가메모 */
    public static final String DELETE_MEMOS_BY_WRITER = """
            DELETE FROM TB_EVAL_MEMO
            WHERE WRTR_EMP_ID = ?
            """;

    /** 직원 정보 수정: 이름, 영문이름, 최종학력 (주민등록번호는 고치지 않는다. 파기한 직원은 바뀌지 않는다) */
    public static final String UPDATE_BASIC = """
            UPDATE TB_EMP
            SET EMP_NM = ?, ENG_NM = ?, LAST_EDU = ?
            WHERE EMP_ID = ? AND DSTR_DT IS NULL
            """;

    /** 기술등급 부여·변경 (비우면 미부여) */
    public static final String UPDATE_GRADE = """
            UPDATE TB_DEV_EMP
            SET GRD_ID = ?
            WHERE EMP_ID = ?
            """;

    /** 비개발직원 부서 변경 */
    public static final String UPDATE_DEPT = """
            UPDATE TB_NDEV_EMP
            SET DEPT_ID = ?
            WHERE EMP_ID = ?
            """;

    /** 개인정보 파기 ①: 대상 수 (퇴사 3년이 지났고 아직 파기하지 않은 직원) */
    public static final String COUNT_PII_TARGETS = """
            SELECT COUNT(*)
            FROM TB_EMP
            WHERE EMPL_STAT = '퇴사'
              AND DSTR_DT IS NULL
              AND RTR_DT < CURDATE() - INTERVAL 3 YEAR
            """;

    /** 개인정보 파기 ②: 그 직원 경력증명서의 기재 값(성명, 재직기간, 소속)을 비운다 */
    public static final String CLEAR_CERT_STMTS = """
            UPDATE TB_CAREER_CERT c
            JOIN TB_EMP e ON e.EMP_ID = c.TRGT_EMP_ID
            SET c.STMT_EMP_NM = NULL, c.STMT_EMPL_PRD = NULL, c.STMT_BLNG = NULL
            WHERE e.EMPL_STAT = '퇴사'
              AND e.DSTR_DT IS NULL
              AND e.RTR_DT < CURDATE() - INTERVAL 3 YEAR
            """;

    /** 개인정보 파기 ③: 주민등록번호, 이름, 영문이름, 전화번호, 주소를 비우고 로그인ID를 바꾸고 파기일자 기록 */
    public static final String DESTROY_PII = """
            UPDATE TB_EMP
            SET RRN = NULL, EMP_NM = NULL, ENG_NM = NULL, TELNO = NULL, ADDR = NULL,
                LOGIN_ID = CONCAT('DEL-', EMP_ID), DSTR_DT = CURDATE()
            WHERE EMPL_STAT = '퇴사'
              AND DSTR_DT IS NULL
              AND RTR_DT < CURDATE() - INTERVAL 3 YEAR
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<StaffRow> findStaff(String emplStat, String keyword) {
        return jdbcTemplate.query(FIND_STAFF, (rs, i) -> new StaffRow(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("EMP_DIV"),
                rs.getString("EMPL_STAT"),
                rs.getObject("HIRE_DT", LocalDate.class),
                rs.getObject("RTR_DT", LocalDate.class),
                rs.getObject("DSTR_DT", LocalDate.class),
                rs.getString("AUTH_NM"),
                rs.getString("GRD_NM"),
                rs.getString("DEPT_NM"),
                rs.getInt("OPEN_HIST_CNT")), emplStat, emplStat, keyword, keyword, keyword);
    }

    public Optional<StaffDetail> findStaffDetail(String empId) {
        return jdbcTemplate.query(FIND_STAFF_DETAIL, (rs, i) -> new StaffDetail(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ENG_NM"),
                rs.getString("LAST_EDU"),
                rs.getString("EMP_DIV"),
                rs.getString("EMPL_STAT"),
                rs.getObject("HIRE_DT", LocalDate.class),
                rs.getObject("RTR_DT", LocalDate.class),
                rs.getObject("DSTR_DT", LocalDate.class),
                rs.getString("AUTH_ID"),
                rs.getString("AUTH_NM"),
                rs.getString("GRD_ID"),
                rs.getString("DEPT_ID"),
                rs.getString("DEPT_NM")), empId).stream().findFirst();
    }

    public List<Code> findGrades() {
        return jdbcTemplate.query(FIND_GRADES, (rs, i) -> new Code(rs.getString("GRD_ID"), rs.getString("GRD_NM")));
    }

    public List<Code> findDepts() {
        return jdbcTemplate.query(FIND_DEPTS, (rs, i) -> new Code(rs.getString("DEPT_ID"), rs.getString("DEPT_NM")));
    }

    public RetireImpact findRetireImpact(String empId) {
        return jdbcTemplate.queryForObject(FIND_RETIRE_IMPACT, (rs, i) -> new RetireImpact(
                rs.getInt("OPEN_HIST_CNT"),
                rs.getInt("DRAFT_EVAL_CNT"),
                rs.getInt("MEMO_CNT"),
                rs.getInt("OPEN_REQ_CNT"),
                rs.getObject("LAST_HIST_DATE", LocalDate.class)), empId, empId, empId, empId, empId, empId);
    }

    public int retireEmp(String empId, LocalDate rtrDt) {
        return jdbcTemplate.update(RETIRE_EMP, rtrDt, empId);
    }

    public int closeOpenHists(String empId, LocalDate endDt) {
        return jdbcTemplate.update(CLOSE_OPEN_HISTS, endDt, empId);
    }

    public List<DraftEval> findDraftEvals(String empId) {
        return jdbcTemplate.query(FIND_DRAFT_EVALS, (rs, i) -> new DraftEval(rs.getInt("EVAL_ID"), rs.getString("EVAL_DIV")),
                empId, empId);
    }

    /** 평가 한 건과 그 점수·내용·서브타입을 지운다. */
    public void deleteEval(DraftEval eval) {
        jdbcTemplate.update(DELETE_EVAL_SCORES, eval.evalId());
        jdbcTemplate.update(DELETE_EVAL_CONTENTS, eval.evalId());
        jdbcTemplate.update("PM".equals(eval.evalDiv()) ? DELETE_PM_EVAL : DELETE_PEER_EVAL, eval.evalId());
        jdbcTemplate.update(DELETE_EVAL, eval.evalId());
    }

    public int deleteMemosByWriter(String empId) {
        return jdbcTemplate.update(DELETE_MEMOS_BY_WRITER, empId);
    }

    public int updateBasic(String empId, String empNm, String engNm, String lastEdu) {
        return jdbcTemplate.update(UPDATE_BASIC, empNm, engNm, lastEdu, empId);
    }

    public int updateGrade(String empId, String grdId) {
        return jdbcTemplate.update(UPDATE_GRADE, grdId, empId);
    }

    public int updateDept(String empId, String deptId) {
        return jdbcTemplate.update(UPDATE_DEPT, deptId, empId);
    }

    public int countPiiTargets() {
        Integer count = jdbcTemplate.queryForObject(COUNT_PII_TARGETS, Integer.class);
        return count == null ? 0 : count;
    }

    public int clearCertStmts() {
        return jdbcTemplate.update(CLEAR_CERT_STMTS);
    }

    public int destroyPii() {
        return jdbcTemplate.update(DESTROY_PII);
    }
}

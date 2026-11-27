package com.databasedesign.SiCompany.employee;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.employee.EmployeeDtos.AsOfRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.AuthTarget;
import com.databasedesign.SiCompany.employee.EmployeeDtos.CareerRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Code;
import com.databasedesign.SiCompany.employee.EmployeeDtos.EmployeeDetail;
import com.databasedesign.SiCompany.employee.EmployeeDtos.EmployeeRow;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Grade;
import com.databasedesign.SiCompany.employee.EmployeeDtos.SearchCond;
import com.databasedesign.SiCompany.employee.EmployeeDtos.Skill;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EmployeeRepository {

    /**
     * 1. 직원 검색 (조건이 NULL이면 그 조건은 보지 않는다). 주민등록번호는 꺼내지 않는다.
     * 파라미터: 이름·직원ID 3개, 직원구분 2개, 기술등급 2개, 기술 2개, 재직상태 2개
     */
    public static final String SEARCH_EMPLOYEES = """
            SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.HIRE_DT, e.EMPL_STAT, a.AUTH_NM,
                   TIMESTAMPDIFF(YEAR, e.HIRE_DT, COALESCE(e.RTR_DT, CURDATE())) + 1 AS YEARS,
                   g.GRD_NM, dp.DEPT_NM,
                   (SELECT GROUP_CONCAT(s.SKILL_NM ORDER BY s.SKILL_NM SEPARATOR ', ')
                    FROM TB_HOLD_SKILL hs
                    JOIN TB_SKILL s ON s.SKILL_ID = hs.SKILL_ID
                    WHERE hs.EMP_ID = e.EMP_ID) AS SKILLS,
                   (SELECT GROUP_CONCAT(h.PRJ_ID ORDER BY h.PRJ_ID SEPARATOR ', ')
                    FROM TB_PRJ_HIST h
                    WHERE h.EMP_ID = e.EMP_ID AND h.END_DT IS NULL) AS CUR_PRJ_IDS
            FROM TB_EMP e
            JOIN TB_AUTH a           ON a.AUTH_ID = e.AUTH_ID
            LEFT JOIN TB_DEV_EMP d   ON d.EMP_ID = e.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            LEFT JOIN TB_NDEV_EMP n  ON n.EMP_ID = e.EMP_ID
            LEFT JOIN TB_DEPT dp     ON dp.DEPT_ID = n.DEPT_ID
            WHERE (? IS NULL OR e.EMP_NM LIKE CONCAT('%', ?, '%') OR e.EMP_ID = ?)
              AND (? IS NULL OR e.EMP_DIV = ?)
              AND (? IS NULL OR d.GRD_ID = ?)
              AND (? IS NULL OR EXISTS (SELECT 1
                                        FROM TB_HOLD_SKILL hs
                                        WHERE hs.EMP_ID = e.EMP_ID AND hs.SKILL_ID = ?))
              AND (? IS NULL OR e.EMPL_STAT = ?)
            ORDER BY e.EMP_NM, e.EMP_ID
            """;

    /** 2. 직원 한 명 (주민등록번호, 비밀번호 제외) */
    public static final String FIND_EMPLOYEE = """
            SELECT e.EMP_ID, e.EMP_NM, e.ENG_NM, e.LOGIN_ID, e.HIRE_DT, e.EMP_DIV, e.LAST_EDU,
                   e.TELNO, e.ADDR, e.EMPL_STAT, e.RTR_DT, a.AUTH_NM,
                   TIMESTAMPDIFF(YEAR, e.HIRE_DT, COALESCE(e.RTR_DT, CURDATE())) + 1 AS YEARS,
                   g.GRD_NM, dp.DEPT_NM
            FROM TB_EMP e
            JOIN TB_AUTH a           ON a.AUTH_ID = e.AUTH_ID
            LEFT JOIN TB_DEV_EMP d   ON d.EMP_ID = e.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            LEFT JOIN TB_NDEV_EMP n  ON n.EMP_ID = e.EMP_ID
            LEFT JOIN TB_DEPT dp     ON dp.DEPT_ID = n.DEPT_ID
            WHERE e.EMP_ID = ?
            """;

    /** 3. 보유기술 */
    public static final String FIND_HOLD_SKILLS = """
            SELECT s.SKILL_ID, s.SKILL_NM
            FROM TB_HOLD_SKILL hs
            JOIN TB_SKILL s ON s.SKILL_ID = hs.SKILL_ID
            WHERE hs.EMP_ID = ?
            ORDER BY s.SKILL_NM
            """;

    /** 4. 투입 이력 (경력): 프로젝트, 발주처, 직무, 투입 기간 (최근 순) */
    public static final String FIND_CAREER = """
            SELECT h.PRJ_HIST_ID, p.PRJ_ID, p.PRJ_NM, c.CLIENT_NM, r.ROLE_NM,
                   h.STRT_DT, h.END_DT, w.WTHDR_RSN_NM, p.PRJ_STAT
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ p            ON p.PRJ_ID = h.PRJ_ID
            JOIN TB_CLIENT c         ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_PRJ_PTCP pt      ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_ROLE r           ON r.ROLE_ID = pt.ROLE_ID
            LEFT JOIN TB_WTHDR_RSN w ON w.WTHDR_RSN_ID = h.WTHDR_RSN_ID
            WHERE h.EMP_ID = ?
            ORDER BY h.STRT_DT DESC
            """;

    /** 5. 특정 시점 참여 이력: 그 날짜에 투입 중이던 직원 (날짜 파라미터 2개) */
    public static final String FIND_AS_OF = """
            SELECT p.PRJ_ID, p.PRJ_NM, c.CLIENT_NM, e.EMP_ID, e.EMP_NM, r.ROLE_NM, h.STRT_DT, h.END_DT
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ p       ON p.PRJ_ID = h.PRJ_ID
            JOIN TB_CLIENT c    ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_ROLE r      ON r.ROLE_ID = pt.ROLE_ID
            JOIN TB_EMP e       ON e.EMP_ID = h.EMP_ID
            WHERE h.STRT_DT <= ?
              AND (h.END_DT IS NULL OR h.END_DT >= ?)
            ORDER BY p.PRJ_ID, FIELD(pt.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
            """;

    /** 6. 기술 목록, 기술등급 목록 (검색 조건과 보유기술 추가용) */
    public static final String FIND_ALL_SKILLS = """
            SELECT SKILL_ID, SKILL_NM
            FROM TB_SKILL
            ORDER BY SKILL_NM
            """;

    public static final String FIND_GRADES = """
            SELECT GRD_ID, GRD_NM
            FROM TB_SKILL_GRD
            ORDER BY FIELD(GRD_ID, 'JR', 'MD', 'SR', 'EX')
            """;

    /** 권한 변경 ①: 권한 목록 */
    public static final String FIND_AUTHS = """
            SELECT AUTH_ID, AUTH_NM
            FROM TB_AUTH
            ORDER BY FIELD(AUTH_ID, 'EMP', 'MGT', 'EXE')
            """;

    /** 권한 변경 ②: 대상 직원의 구분, 재직상태, 부서 (경영관리팀 권한은 경영관리 부서 비개발직원에게만) */
    public static final String FIND_AUTH_TARGET = """
            SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.EMPL_STAT, e.AUTH_ID, n.DEPT_ID
            FROM TB_EMP e
            LEFT JOIN TB_NDEV_EMP n ON n.EMP_ID = e.EMP_ID
            WHERE e.EMP_ID = ?
            """;

    /** 권한 변경 ③ (재직 중일 때만 바뀐다) */
    public static final String UPDATE_AUTH = """
            UPDATE TB_EMP
            SET AUTH_ID = ?
            WHERE EMP_ID = ? AND EMPL_STAT = '재직'
            """;

    /** 내 정보 수정 ①: 전화번호, 주소 */
    public static final String UPDATE_CONTACT = """
            UPDATE TB_EMP
            SET TELNO = ?, ADDR = ?
            WHERE EMP_ID = ?
            """;

    /** 내 정보 수정 ②: 비밀번호 (평문을 저장하지 않고 SHA-256 해시로 저장한다) */
    public static final String UPDATE_PASSWORD = """
            UPDATE TB_EMP
            SET PWD = CONCAT('HASH$', SHA2(?, 256))
            WHERE EMP_ID = ?
            """;

    /** 보유기술 등록 ①: 이미 가진 기술인지 */
    public static final String COUNT_HOLD_SKILL = """
            SELECT COUNT(*)
            FROM TB_HOLD_SKILL
            WHERE SKILL_ID = ? AND EMP_ID = ?
            """;

    /** 보유기술 등록 ② */
    public static final String INSERT_HOLD_SKILL = """
            INSERT INTO TB_HOLD_SKILL (SKILL_ID, EMP_ID)
            VALUES (?, ?)
            """;

    /** 보유기술 삭제 */
    public static final String DELETE_HOLD_SKILL = """
            DELETE FROM TB_HOLD_SKILL
            WHERE SKILL_ID = ? AND EMP_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<EmployeeRow> search(SearchCond c) {
        return jdbcTemplate.query(SEARCH_EMPLOYEES, (rs, i) -> new EmployeeRow(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("EMP_DIV"),
                rs.getObject("HIRE_DT", LocalDate.class),
                rs.getString("EMPL_STAT"),
                rs.getString("AUTH_NM"),
                rs.getInt("YEARS"),
                rs.getString("GRD_NM"),
                rs.getString("DEPT_NM"),
                rs.getString("SKILLS"),
                rs.getString("CUR_PRJ_IDS")),
                c.keyword(), c.keyword(), c.keyword(),
                c.empDiv(), c.empDiv(),
                c.grdId(), c.grdId(),
                c.skillId(), c.skillId(),
                c.emplStat(), c.emplStat());
    }

    public Optional<EmployeeDetail> findEmployee(String empId) {
        return jdbcTemplate.query(FIND_EMPLOYEE, (rs, i) -> new EmployeeDetail(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ENG_NM"),
                rs.getString("LOGIN_ID"),
                rs.getObject("HIRE_DT", LocalDate.class),
                rs.getString("EMP_DIV"),
                rs.getString("LAST_EDU"),
                rs.getString("TELNO"),
                rs.getString("ADDR"),
                rs.getString("EMPL_STAT"),
                rs.getObject("RTR_DT", LocalDate.class),
                rs.getString("AUTH_NM"),
                rs.getInt("YEARS"),
                rs.getString("GRD_NM"),
                rs.getString("DEPT_NM")), empId).stream().findFirst();
    }

    public List<Skill> findHoldSkills(String empId) {
        return jdbcTemplate.query(FIND_HOLD_SKILLS, (rs, i) -> new Skill(rs.getInt("SKILL_ID"), rs.getString("SKILL_NM")), empId);
    }

    public List<CareerRow> findCareer(String empId) {
        return jdbcTemplate.query(FIND_CAREER, (rs, i) -> new CareerRow(
                rs.getInt("PRJ_HIST_ID"),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("CLIENT_NM"),
                rs.getString("ROLE_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("END_DT", LocalDate.class),
                rs.getString("WTHDR_RSN_NM"),
                rs.getString("PRJ_STAT")), empId);
    }

    public List<AsOfRow> findAsOf(LocalDate date) {
        return jdbcTemplate.query(FIND_AS_OF, (rs, i) -> new AsOfRow(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("CLIENT_NM"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("END_DT", LocalDate.class)), date, date);
    }

    public List<Skill> findAllSkills() {
        return jdbcTemplate.query(FIND_ALL_SKILLS, (rs, i) -> new Skill(rs.getInt("SKILL_ID"), rs.getString("SKILL_NM")));
    }

    public List<Grade> findGrades() {
        return jdbcTemplate.query(FIND_GRADES, (rs, i) -> new Grade(rs.getString("GRD_ID"), rs.getString("GRD_NM")));
    }

    public int updateContact(String empId, String telno, String addr) {
        return jdbcTemplate.update(UPDATE_CONTACT, telno, addr, empId);
    }

    public int updatePassword(String empId, String password) {
        return jdbcTemplate.update(UPDATE_PASSWORD, password, empId);
    }

    public boolean existsHoldSkill(int skillId, String empId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_HOLD_SKILL, Integer.class, skillId, empId);
        return count != null && count > 0;
    }

    public void insertHoldSkill(int skillId, String empId) {
        jdbcTemplate.update(INSERT_HOLD_SKILL, skillId, empId);
    }

    public int deleteHoldSkill(int skillId, String empId) {
        return jdbcTemplate.update(DELETE_HOLD_SKILL, skillId, empId);
    }

    public List<Code> findAuths() {
        return jdbcTemplate.query(FIND_AUTHS, (rs, i) -> new Code(rs.getString("AUTH_ID"), rs.getString("AUTH_NM")));
    }

    public Optional<AuthTarget> findAuthTarget(String empId) {
        return jdbcTemplate.query(FIND_AUTH_TARGET, (rs, i) -> new AuthTarget(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("EMP_DIV"),
                rs.getString("EMPL_STAT"),
                rs.getString("AUTH_ID"),
                rs.getString("DEPT_ID")), empId).stream().findFirst();
    }

    public int updateAuth(String empId, String authId) {
        return jdbcTemplate.update(UPDATE_AUTH, authId, empId);
    }
}

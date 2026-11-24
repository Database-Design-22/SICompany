package com.databasedesign.SiCompany.designation;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.designation.DesignationDtos.Candidate;
import com.databasedesign.SiCompany.designation.DesignationDtos.Client;
import com.databasedesign.SiCompany.designation.DesignationDtos.Designation;
import com.databasedesign.SiCompany.designation.DesignationDtos.DevEmployee;
import com.databasedesign.SiCompany.designation.DesignationDtos.Overlap;
import com.databasedesign.SiCompany.designation.DesignationDtos.ReplRequestForExclusion;
import com.databasedesign.SiCompany.designation.DesignationDtos.Role;
import com.databasedesign.SiCompany.designation.DesignationDtos.StaffingProject;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class DesignationRepository {

    /** 1. 발주처 목록 */
    public static final String FIND_CLIENTS = """
            SELECT CLIENT_ID, CLIENT_NM
            FROM TB_CLIENT
            ORDER BY CLIENT_NM
            """;

    /**
     * 2. 지정인력 목록 (발주처로 거르기, 전체면 NULL)
     * 유효 = 지정일자가 오늘 이전이고, 해제일자가 없거나 오늘보다 뒤 (해제일자는 그날부터 효력 없음)
     */
    public static final String FIND_DESIGNATIONS = """
            SELECT d.DESIG_ID, d.DESIG_DIV, d.DESIG_RSN, d.DESIG_DT, d.RLS_DT,
                   d.CLIENT_ID, c.CLIENT_NM, d.EMP_ID, e.EMP_NM,
                   d.REPL_REQ_ID, m.MGR_NM AS REQ_MGR_NM,
                   (d.DESIG_DT <= CURDATE() AND (d.RLS_DT IS NULL OR d.RLS_DT > CURDATE())) AS VALID_YN
            FROM TB_CLIENT_DESIG_STAFF d
            JOIN TB_CLIENT c ON c.CLIENT_ID = d.CLIENT_ID
            JOIN TB_EMP e    ON e.EMP_ID = d.EMP_ID
            LEFT JOIN TB_STAFF_REPL_REQ r ON r.REPL_REQ_ID = d.REPL_REQ_ID
            LEFT JOIN TB_CLIENT_MGR m     ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
            WHERE (? IS NULL OR d.CLIENT_ID = ?)
            ORDER BY c.CLIENT_NM, VALID_YN DESC, d.DESIG_DT DESC, d.DESIG_ID DESC
            """;

    /** 3. 지명 대상: 재직 개발직원 */
    public static final String FIND_DEV_EMPLOYEES = """
            SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM
            FROM TB_DEV_EMP d
            JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            WHERE e.EMPL_STAT = '재직'
            ORDER BY e.EMP_NM
            """;

    /**
     * 4. 유효 기간이 겹치는 지정 (같은 발주처, 같은 직원, 구분 무관)
     * 새 지정의 기간은 [시작일, 해제 전까지)이므로 해제되지 않았거나 해제일자가 시작일보다 뒤인 지정과 겹친다.
     */
    public static final String FIND_OVERLAPS = """
            SELECT DESIG_ID, DESIG_DIV, DESIG_DT, RLS_DT
            FROM TB_CLIENT_DESIG_STAFF
            WHERE CLIENT_ID = ?
              AND EMP_ID = ?
              AND (RLS_DT IS NULL OR RLS_DT > ?)
            """;

    /** 5. 제외 등록용: 교체 요청 한 건 (발주처, 대상 직원) */
    public static final String FIND_REQUEST_FOR_EXCLUSION = """
            SELECT r.REPL_REQ_ID, r.PROC_STAT, r.PROC_DT, r.REQ_RSN, r.CLIENT_ID, c.CLIENT_NM, m.MGR_NM,
                   h.EMP_ID, e.EMP_NM, h.PRJ_ID, p.PRJ_NM
            FROM TB_STAFF_REPL_REQ r
            JOIN TB_CLIENT c     ON c.CLIENT_ID = r.CLIENT_ID
            JOIN TB_CLIENT_MGR m ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
            JOIN TB_PRJ_HIST h   ON h.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
            JOIN TB_EMP e        ON e.EMP_ID = h.EMP_ID
            JOIN TB_PRJ p        ON p.PRJ_ID = h.PRJ_ID
            WHERE r.REPL_REQ_ID = ?
            """;

    /** 6. 이 요청으로 이미 등록한 제외 지정 */
    public static final String FIND_DESIG_BY_REQUEST = """
            SELECT DESIG_ID
            FROM TB_CLIENT_DESIG_STAFF
            WHERE REPL_REQ_ID = ?
            """;

    /** 7. 지정 등록 (제외: 요청ID 있음 / 지명: 요청ID NULL), 지정일자는 오늘 */
    public static final String INSERT_DESIGNATION = """
            INSERT INTO TB_CLIENT_DESIG_STAFF (DESIG_DIV, DESIG_RSN, DESIG_DT, CLIENT_ID, EMP_ID, REPL_REQ_ID)
            VALUES (?, ?, CURDATE(), ?, ?, ?)
            """;

    /** 8. 해제 (행은 지우지 않고 해제일자만 넣는다. 아직 유효할 때만) */
    public static final String RELEASE_DESIGNATION = """
            UPDATE TB_CLIENT_DESIG_STAFF
            SET RLS_DT = CURDATE()
            WHERE DESIG_ID = ?
              AND (RLS_DT IS NULL OR RLS_DT > CURDATE())
            """;

    /** 9. 인력 배정: 진행/지연 프로젝트 (발주처, 기간) */
    public static final String FIND_STAFFING_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, p.SCHD_END_DT, p.CLIENT_ID, c.CLIENT_NM
            FROM TB_PRJ p
            JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
            WHERE p.PRJ_STAT IN ('진행', '지연')
            ORDER BY c.CLIENT_NM, p.PRJ_ID
            """;

    /**
     * 10. 배정 후보 목록 (하나의 SELECT)
     * 지금 투입 중이 아닌 재직 개발직원 + 그 프로젝트 발주처의 유효한 지정 + 제외의 근거 교체 요청.
     * 지명은 맨 위, 제외는 맨 아래. 겹치는 지정은 등록 때 막으므로 직원마다 유효한 지정은 많아야 1건이다.
     */
    public static final String FIND_CANDIDATES = """
            SELECT d.EMP_ID, e.EMP_NM, g.GRD_NM,
                   (SELECT GROUP_CONCAT(s.SKILL_NM ORDER BY s.SKILL_NM SEPARATOR ', ')
                    FROM TB_HOLD_SKILL hs
                    JOIN TB_SKILL s ON s.SKILL_ID = hs.SKILL_ID
                    WHERE hs.EMP_ID = d.EMP_ID) AS SKILLS,
                   ds.DESIG_ID, ds.DESIG_DIV, ds.DESIG_DT, ds.DESIG_RSN,
                   ds.REPL_REQ_ID, rq.REQ_DT, rq.REQ_RSN, mg.MGR_NM AS REQ_MGR_NM,
                   pt.ROLE_ID AS PTCP_ROLE_ID
            FROM TB_DEV_EMP d
            JOIN TB_EMP e ON e.EMP_ID = d.EMP_ID
            LEFT JOIN TB_SKILL_GRD g ON g.GRD_ID = d.GRD_ID
            JOIN TB_PRJ p ON p.PRJ_ID = ?
            LEFT JOIN TB_CLIENT_DESIG_STAFF ds
                   ON ds.CLIENT_ID = p.CLIENT_ID
                  AND ds.EMP_ID = d.EMP_ID
                  AND ds.DESIG_DT <= CURDATE()
                  AND (ds.RLS_DT IS NULL OR ds.RLS_DT > CURDATE())
            LEFT JOIN TB_STAFF_REPL_REQ rq ON rq.REPL_REQ_ID = ds.REPL_REQ_ID
            LEFT JOIN TB_CLIENT_MGR mg     ON mg.CLIENT_ID = rq.CLIENT_ID AND mg.MGR_ID = rq.MGR_ID
            LEFT JOIN TB_PRJ_PTCP pt       ON pt.EMP_ID = d.EMP_ID AND pt.PRJ_ID = p.PRJ_ID
            WHERE e.EMPL_STAT = '재직'
              AND NOT EXISTS (SELECT 1
                              FROM TB_PRJ_HIST h
                              WHERE h.EMP_ID = d.EMP_ID
                                AND h.END_DT IS NULL)
            ORDER BY CASE ds.DESIG_DIV WHEN '지명' THEN 0 WHEN '제외' THEN 2 ELSE 1 END,
                     FIELD(d.GRD_ID, 'EX', 'SR', 'MD', 'JR'), e.EMP_NM
            """;

    /** 11. 직무 목록 */
    public static final String FIND_ROLES = """
            SELECT ROLE_ID, ROLE_NM
            FROM TB_ROLE
            ORDER BY FIELD(ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS')
            """;

    /** 12. 그 프로젝트에 PM이 있는지 (프로젝트당 PM 1명) */
    public static final String COUNT_PM = """
            SELECT COUNT(*)
            FROM TB_PRJ_PTCP
            WHERE PRJ_ID = ?
              AND ROLE_ID = 'PM'
            """;

    /** 13. 투입 기간이 겹치는 이력 (시작일 이후에 끝나거나 아직 열린 이력) 중 가장 늦게 끝나는 것 */
    public static final String FIND_LATEST_OVERLAP_END = """
            SELECT MAX(COALESCE(END_DT, DATE '9999-12-31'))
            FROM TB_PRJ_HIST
            WHERE EMP_ID = ?
              AND (END_DT IS NULL OR END_DT >= ?)
            """;

    /** 14. 이 프로젝트 참여가 있는지 (있으면 기존 직무) */
    public static final String FIND_PTCP_ROLE = """
            SELECT ROLE_ID
            FROM TB_PRJ_PTCP
            WHERE EMP_ID = ?
              AND PRJ_ID = ?
            """;

    /** 15. 프로젝트참여 생성 (없을 때만) */
    public static final String INSERT_PTCP = """
            INSERT INTO TB_PRJ_PTCP (EMP_ID, PRJ_ID, ROLE_ID)
            VALUES (?, ?, ?)
            """;

    /** 16. 투입 이력 생성 */
    public static final String INSERT_HIST = """
            INSERT INTO TB_PRJ_HIST (EMP_ID, PRJ_ID, STRT_DT)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<StaffingProject> findStaffingProjects() {
        return jdbcTemplate.query(FIND_STAFFING_PROJECTS, (rs, i) -> new StaffingProject(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("SCHD_END_DT", LocalDate.class),
                rs.getInt("CLIENT_ID"),
                rs.getString("CLIENT_NM")));
    }

    public List<Candidate> findCandidates(String prjId) {
        return jdbcTemplate.query(FIND_CANDIDATES, (rs, i) -> new Candidate(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("GRD_NM"),
                rs.getString("SKILLS"),
                rs.getObject("DESIG_ID", Integer.class),
                rs.getString("DESIG_DIV"),
                rs.getObject("DESIG_DT", LocalDate.class),
                rs.getString("DESIG_RSN"),
                rs.getObject("REPL_REQ_ID", Integer.class),
                rs.getObject("REQ_DT", LocalDate.class),
                rs.getString("REQ_RSN"),
                rs.getString("REQ_MGR_NM"),
                rs.getString("PTCP_ROLE_ID")), prjId);
    }

    public List<Role> findRoles() {
        return jdbcTemplate.query(FIND_ROLES, (rs, i) -> new Role(rs.getString("ROLE_ID"), rs.getString("ROLE_NM")));
    }

    public boolean hasPm(String prjId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_PM, Integer.class, prjId);
        return count != null && count > 0;
    }

    public Optional<LocalDate> findLatestOverlapEnd(String empId, LocalDate strtDt) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(FIND_LATEST_OVERLAP_END, LocalDate.class, empId, strtDt));
    }

    public Optional<String> findPtcpRole(String empId, String prjId) {
        return jdbcTemplate.queryForList(FIND_PTCP_ROLE, String.class, empId, prjId).stream().findFirst();
    }

    public void insertPtcp(String empId, String prjId, String roleId) {
        jdbcTemplate.update(INSERT_PTCP, empId, prjId, roleId);
    }

    /** @return 새 프로젝트이력ID */
    public int insertHist(String empId, String prjId, LocalDate strtDt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_HIST, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, empId);
            ps.setString(2, prjId);
            ps.setObject(3, strtDt);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public List<Client> findClients() {
        return jdbcTemplate.query(FIND_CLIENTS, (rs, i) -> new Client(rs.getInt("CLIENT_ID"), rs.getString("CLIENT_NM")));
    }

    public List<Designation> findDesignations(Integer clientId) {
        return jdbcTemplate.query(FIND_DESIGNATIONS, (rs, i) -> new Designation(
                rs.getInt("DESIG_ID"),
                rs.getString("DESIG_DIV"),
                rs.getString("DESIG_RSN"),
                rs.getObject("DESIG_DT", LocalDate.class),
                rs.getObject("RLS_DT", LocalDate.class),
                rs.getInt("CLIENT_ID"),
                rs.getString("CLIENT_NM"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getObject("REPL_REQ_ID", Integer.class),
                rs.getString("REQ_MGR_NM"),
                rs.getBoolean("VALID_YN")), clientId, clientId);
    }

    public List<DevEmployee> findDevEmployees() {
        return jdbcTemplate.query(FIND_DEV_EMPLOYEES, (rs, i) -> new DevEmployee(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("GRD_NM")));
    }

    public List<Overlap> findOverlaps(int clientId, String empId, LocalDate start) {
        return jdbcTemplate.query(FIND_OVERLAPS, (rs, i) -> new Overlap(
                rs.getInt("DESIG_ID"),
                rs.getString("DESIG_DIV"),
                rs.getObject("DESIG_DT", LocalDate.class),
                rs.getObject("RLS_DT", LocalDate.class)), clientId, empId, start);
    }

    public Optional<ReplRequestForExclusion> findRequestForExclusion(int replReqId) {
        return jdbcTemplate.query(FIND_REQUEST_FOR_EXCLUSION, (rs, i) -> new ReplRequestForExclusion(
                rs.getInt("REPL_REQ_ID"),
                rs.getString("PROC_STAT"),
                rs.getObject("PROC_DT", LocalDate.class),
                rs.getString("REQ_RSN"),
                rs.getInt("CLIENT_ID"),
                rs.getString("CLIENT_NM"),
                rs.getString("MGR_NM"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM")), replReqId).stream().findFirst();
    }

    public Optional<Integer> findDesigIdByRequest(int replReqId) {
        return jdbcTemplate.queryForList(FIND_DESIG_BY_REQUEST, Integer.class, replReqId).stream().findFirst();
    }

    /** @return 새 지정ID */
    public int insertDesignation(String desigDiv, String desigRsn, int clientId, String empId, Integer replReqId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_DESIGNATION, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, desigDiv);
            ps.setString(2, desigRsn);
            ps.setInt(3, clientId);
            ps.setString(4, empId);
            if (replReqId == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, replReqId);
            }
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public int release(int desigId) {
        return jdbcTemplate.update(RELEASE_DESIGNATION, desigId);
    }
}

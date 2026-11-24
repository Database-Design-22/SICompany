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

import com.databasedesign.SiCompany.designation.DesignationDtos.Client;
import com.databasedesign.SiCompany.designation.DesignationDtos.Designation;
import com.databasedesign.SiCompany.designation.DesignationDtos.DevEmployee;
import com.databasedesign.SiCompany.designation.DesignationDtos.Overlap;
import com.databasedesign.SiCompany.designation.DesignationDtos.ReplRequestForExclusion;

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

    private final JdbcTemplate jdbcTemplate;

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

package com.databasedesign.SiCompany.certificate;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.certificate.CertificateDtos.Applicant;
import com.databasedesign.SiCompany.certificate.CertificateDtos.CertDetail;
import com.databasedesign.SiCompany.certificate.CertificateDtos.CertRow;
import com.databasedesign.SiCompany.certificate.CertificateDtos.InclRow;
import com.databasedesign.SiCompany.certificate.CertificateDtos.Participation;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CertificateRepository {

    /** 1. 신청자 (재직기간 판단용) */
    public static final String FIND_APPLICANT = """
            SELECT EMP_ID, EMP_NM, EMP_DIV, HIRE_DT, RTR_DT, EMPL_STAT
            FROM TB_EMP
            WHERE EMP_ID = ?
            """;

    /** 2. 수록할 수 있는 참여: 현재 재직기간(입사일자 이후)에 투입 이력이 있는 프로젝트 */
    public static final String FIND_PARTICIPATIONS = """
            SELECT p.PRJ_ID, p.PRJ_NM, c.CLIENT_NM, r.ROLE_NM,
                   MIN(h.STRT_DT) AS FIRST_STRT_DT,
                   MAX(h.END_DT) AS LAST_END_DT,
                   MAX(h.END_DT IS NULL) AS ONGOING
            FROM TB_PRJ_PTCP pt
            JOIN TB_EMP e       ON e.EMP_ID = pt.EMP_ID
            JOIN TB_PRJ_HIST h  ON h.EMP_ID = pt.EMP_ID AND h.PRJ_ID = pt.PRJ_ID AND h.STRT_DT >= e.HIRE_DT
            JOIN TB_PRJ p       ON p.PRJ_ID = pt.PRJ_ID
            JOIN TB_CLIENT c    ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_ROLE r      ON r.ROLE_ID = pt.ROLE_ID
            WHERE pt.EMP_ID = ?
            GROUP BY p.PRJ_ID, p.PRJ_NM, c.CLIENT_NM, r.ROLE_NM
            ORDER BY FIRST_STRT_DT DESC
            """;

    /** 3. 증명서 목록 (대상 직원으로 거르기, 전체면 NULL. 발급 전이 먼저) */
    public static final String FIND_CERTS = """
            SELECT c.CERT_ID, c.APLC_DT, c.APLC_MTHD, c.EMPL_PRD_STMT_YN, c.BLNG_STMT_YN, c.USG,
                   c.ISSU_DT, c.VRFC_NO, c.TRGT_EMP_ID, t.EMP_NM AS TRGT_EMP_NM, x.EMP_NM AS PROC_EMP_NM,
                   (SELECT COUNT(*) FROM TB_CERT_INCL_HIST i WHERE i.CERT_ID = c.CERT_ID) AS INCL_CNT
            FROM TB_CAREER_CERT c
            JOIN TB_EMP t      ON t.EMP_ID = c.TRGT_EMP_ID
            LEFT JOIN TB_EMP x ON x.EMP_ID = c.PROC_EMP_ID
            WHERE (? IS NULL OR c.TRGT_EMP_ID = ?)
            ORDER BY c.ISSU_DT IS NOT NULL, c.APLC_DT DESC, c.CERT_ID DESC
            """;

    /** 4. 증명서 한 장 */
    public static final String FIND_CERT = """
            SELECT c.CERT_ID, c.APLC_DT, c.APLC_MTHD, c.EMPL_PRD_STMT_YN, c.BLNG_STMT_YN, c.USG,
                   c.ISSU_DT, c.VRFC_NO, c.STMT_EMP_NM, c.STMT_EMPL_PRD, c.STMT_BLNG,
                   c.TRGT_EMP_ID, x.EMP_NM AS PROC_EMP_NM
            FROM TB_CAREER_CERT c
            LEFT JOIN TB_EMP x ON x.EMP_ID = c.PROC_EMP_ID
            WHERE c.CERT_ID = ?
            """;

    /** 5. 수록 이력 */
    public static final String FIND_INCL_HISTS = """
            SELECT i.PRJ_ID, i.PRJ_NM, i.CLIENT_NM, i.ROLE_NM, i.ASGN_PRD
            FROM TB_CERT_INCL_HIST i
            WHERE i.CERT_ID = ?
            ORDER BY i.PRJ_ID
            """;

    /** 신청 ①: 경력증명서 (신청일자 오늘, 본인 신청, 발급 전) */
    public static final String INSERT_CERT = """
            INSERT INTO TB_CAREER_CERT
                   (APLC_DT, APLC_MTHD, EMPL_PRD_STMT_YN, BLNG_STMT_YN, USG, TRGT_EMP_ID)
            VALUES (CURDATE(), '본인', ?, ?, ?, ?)
            """;

    /** 신청 ②: 수록할 참여 (기재 값은 발급할 때 채운다) */
    public static final String INSERT_INCL_HIST = """
            INSERT INTO TB_CERT_INCL_HIST (CERT_ID, PRJ_ID, EMP_ID)
            VALUES (?, ?, ?)
            """;

    /**
     * 발급 ①: 진위확인번호와 기재 값 (발급 당시 값을 복사해 둔다. 발급 전일 때만 바뀐다)
     * - 진위확인번호 = 'V' + 발급일(yyMMdd) + '-' + 증명서ID 세 자리
     * - 기재재직기간 = 입사일 ~ 퇴사일(재직 중이면 '현재'), 기재소속 = 비개발은 부서명, 개발은 '개발직'
     */
    public static final String ISSUE_CERT = """
            UPDATE TB_CAREER_CERT c
            JOIN TB_EMP e           ON e.EMP_ID = c.TRGT_EMP_ID
            LEFT JOIN TB_NDEV_EMP n ON n.EMP_ID = e.EMP_ID
            LEFT JOIN TB_DEPT d     ON d.DEPT_ID = n.DEPT_ID
            SET c.ISSU_DT = CURDATE(),
                c.VRFC_NO = CONCAT('V', DATE_FORMAT(CURDATE(), '%y%m%d'), '-', LPAD(c.CERT_ID, 3, '0')),
                c.STMT_EMP_NM = e.EMP_NM,
                c.STMT_EMPL_PRD = CASE WHEN c.EMPL_PRD_STMT_YN = 'Y'
                                       THEN CONCAT(DATE_FORMAT(e.HIRE_DT, '%Y.%m.%d'), ' ~ ',
                                                   COALESCE(DATE_FORMAT(e.RTR_DT, '%Y.%m.%d'), '현재')) END,
                c.STMT_BLNG = CASE WHEN c.BLNG_STMT_YN = 'Y'
                                   THEN COALESCE(d.DEPT_NM, '개발직') END,
                c.PROC_EMP_ID = ?
            WHERE c.CERT_ID = ? AND c.ISSU_DT IS NULL
            """;

    /** 발급 ②: 수록 이력에 프로젝트명, 발주처명, 직무명, 투입기간(재투입은 쉼표로 이어 적음) 복사 */
    public static final String FILL_INCL_HISTS = """
            UPDATE TB_CERT_INCL_HIST i
            JOIN TB_PRJ p       ON p.PRJ_ID = i.PRJ_ID
            JOIN TB_CLIENT c    ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = i.EMP_ID AND pt.PRJ_ID = i.PRJ_ID
            JOIN TB_ROLE r      ON r.ROLE_ID = pt.ROLE_ID
            SET i.PRJ_NM = p.PRJ_NM,
                i.CLIENT_NM = c.CLIENT_NM,
                i.ROLE_NM = r.ROLE_NM,
                i.ASGN_PRD = (SELECT GROUP_CONCAT(CONCAT(DATE_FORMAT(h.STRT_DT, '%Y.%m.%d'), '~',
                                                         COALESCE(DATE_FORMAT(h.END_DT, '%Y.%m.%d'), '현재'))
                                                  ORDER BY h.STRT_DT SEPARATOR ', ')
                              FROM TB_PRJ_HIST h
                              WHERE h.EMP_ID = i.EMP_ID AND h.PRJ_ID = i.PRJ_ID)
            WHERE i.CERT_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public Optional<Applicant> findApplicant(String empId) {
        return jdbcTemplate.query(FIND_APPLICANT, (rs, i) -> new Applicant(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("EMP_DIV"),
                rs.getObject("HIRE_DT", LocalDate.class),
                rs.getObject("RTR_DT", LocalDate.class),
                rs.getString("EMPL_STAT")), empId).stream().findFirst();
    }

    public List<Participation> findParticipations(String empId) {
        return jdbcTemplate.query(FIND_PARTICIPATIONS, (rs, i) -> new Participation(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("CLIENT_NM"),
                rs.getString("ROLE_NM"),
                rs.getObject("FIRST_STRT_DT", LocalDate.class),
                rs.getObject("LAST_END_DT", LocalDate.class),
                rs.getBoolean("ONGOING")), empId);
    }

    public List<CertRow> findCerts(String trgtEmpId) {
        return jdbcTemplate.query(FIND_CERTS, (rs, i) -> new CertRow(
                rs.getInt("CERT_ID"),
                rs.getObject("APLC_DT", LocalDate.class),
                rs.getString("APLC_MTHD"),
                rs.getString("EMPL_PRD_STMT_YN"),
                rs.getString("BLNG_STMT_YN"),
                rs.getString("USG"),
                rs.getObject("ISSU_DT", LocalDate.class),
                rs.getString("VRFC_NO"),
                rs.getString("TRGT_EMP_ID"),
                rs.getString("TRGT_EMP_NM"),
                rs.getString("PROC_EMP_NM"),
                rs.getInt("INCL_CNT")), trgtEmpId, trgtEmpId);
    }

    public Optional<CertDetail> findCert(int certId) {
        return jdbcTemplate.query(FIND_CERT, (rs, i) -> new CertDetail(
                rs.getInt("CERT_ID"),
                rs.getObject("APLC_DT", LocalDate.class),
                rs.getString("APLC_MTHD"),
                rs.getString("EMPL_PRD_STMT_YN"),
                rs.getString("BLNG_STMT_YN"),
                rs.getString("USG"),
                rs.getObject("ISSU_DT", LocalDate.class),
                rs.getString("VRFC_NO"),
                rs.getString("STMT_EMP_NM"),
                rs.getString("STMT_EMPL_PRD"),
                rs.getString("STMT_BLNG"),
                rs.getString("TRGT_EMP_ID"),
                rs.getString("PROC_EMP_NM")), certId).stream().findFirst();
    }

    public List<InclRow> findInclHists(int certId) {
        return jdbcTemplate.query(FIND_INCL_HISTS, (rs, i) -> new InclRow(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("CLIENT_NM"),
                rs.getString("ROLE_NM"),
                rs.getString("ASGN_PRD")), certId);
    }

    /** @return 새 증명서ID */
    public int insertCert(String emplPrdStmtYn, String blngStmtYn, String usg, String trgtEmpId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_CERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, emplPrdStmtYn);
            ps.setString(2, blngStmtYn);
            ps.setString(3, usg);
            ps.setString(4, trgtEmpId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }

    public void insertInclHist(int certId, String prjId, String empId) {
        jdbcTemplate.update(INSERT_INCL_HIST, certId, prjId, empId);
    }

    public int issueCert(int certId, String procEmpId) {
        return jdbcTemplate.update(ISSUE_CERT, procEmpId, certId);
    }

    public int fillInclHists(int certId) {
        return jdbcTemplate.update(FILL_INCL_HISTS, certId);
    }
}

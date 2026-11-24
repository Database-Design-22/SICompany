package com.databasedesign.SiCompany.replacement;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.replacement.ReplacementDtos.ActiveProject;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ClientManager;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplRequest;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.ReplaceableHist;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.TargetHist;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ReplacementRepository {

    /** 1. 교체 요청 목록 (처리상태로 거르기, 전체면 NULL. 접수가 먼저) */
    public static final String FIND_REQUESTS = """
            SELECT r.REPL_REQ_ID, r.PROC_STAT, r.REQ_DT, r.PROC_DT, r.REQ_RSN,
                   c.CLIENT_NM, m.MGR_NM,
                   th.PRJ_ID, p.PRJ_NM,
                   th.EMP_ID AS TRGT_EMP_ID, te.EMP_NM AS TRGT_EMP_NM, tp.ROLE_ID AS TRGT_ROLE_ID,
                   sh.EMP_ID AS SUCC_EMP_ID, se.EMP_NM AS SUCC_EMP_NM
            FROM TB_STAFF_REPL_REQ r
            JOIN TB_CLIENT c      ON c.CLIENT_ID = r.CLIENT_ID
            JOIN TB_CLIENT_MGR m  ON m.CLIENT_ID = r.CLIENT_ID AND m.MGR_ID = r.MGR_ID
            JOIN TB_PRJ_HIST th   ON th.PRJ_HIST_ID = r.REPL_TRGT_HIST_ID
            JOIN TB_PRJ p         ON p.PRJ_ID = th.PRJ_ID
            JOIN TB_EMP te        ON te.EMP_ID = th.EMP_ID
            JOIN TB_PRJ_PTCP tp   ON tp.EMP_ID = th.EMP_ID AND tp.PRJ_ID = th.PRJ_ID
            LEFT JOIN TB_PRJ_HIST sh ON sh.PRJ_HIST_ID = r.SUCC_HIST_ID
            LEFT JOIN TB_EMP se      ON se.EMP_ID = sh.EMP_ID
            WHERE (? IS NULL OR r.PROC_STAT = ?)
            ORDER BY FIELD(r.PROC_STAT, '접수', '수용', '철회'), r.REQ_DT DESC, r.REPL_REQ_ID DESC
            """;

    /** 2. 진행/지연 프로젝트 (발주처와 함께) */
    public static final String FIND_ACTIVE_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_ID, c.CLIENT_NM
            FROM TB_PRJ p
            JOIN TB_CLIENT c ON c.CLIENT_ID = p.CLIENT_ID
            WHERE p.PRJ_STAT IN ('진행', '지연')
            ORDER BY c.CLIENT_NM, p.PRJ_ID
            """;

    /** 3. 교체 대상 후보: 그 프로젝트의 열린 투입 이력 (PM 제외) */
    public static final String FIND_REPLACEABLE_HISTS = """
            SELECT h.PRJ_HIST_ID, h.EMP_ID, e.EMP_NM, pt.ROLE_ID, ro.ROLE_NM, h.STRT_DT,
                   (SELECT COUNT(*)
                    FROM TB_STAFF_REPL_REQ q
                    WHERE q.REPL_TRGT_HIST_ID = h.PRJ_HIST_ID
                      AND q.PROC_STAT = '접수') AS OPEN_REQ_CNT
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_EMP e       ON e.EMP_ID = h.EMP_ID
            JOIN TB_ROLE ro     ON ro.ROLE_ID = pt.ROLE_ID
            WHERE h.PRJ_ID = ?
              AND h.END_DT IS NULL
              AND pt.ROLE_ID <> 'PM'
            ORDER BY FIELD(pt.ROLE_ID, 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
            """;

    /** 4. 그 프로젝트 발주처의 담당자 (프로젝트 담당자가 먼저) */
    public static final String FIND_CLIENT_MANAGERS = """
            SELECT m.MGR_ID, m.MGR_NM, (m.MGR_ID = p.MGR_ID) AS PRJ_MGR_YN
            FROM TB_PRJ p
            JOIN TB_CLIENT_MGR m ON m.CLIENT_ID = p.CLIENT_ID
            WHERE p.PRJ_ID = ?
            ORDER BY PRJ_MGR_YN DESC, m.MGR_NM
            """;

    /** 5. 등록 검사용: 대상 이력 한 건 (프로젝트 상태, 직무, 발주처) */
    public static final String FIND_TARGET_HIST = """
            SELECT h.PRJ_HIST_ID, h.EMP_ID, h.PRJ_ID, h.STRT_DT, h.END_DT,
                   pt.ROLE_ID, p.PRJ_STAT, p.CLIENT_ID
            FROM TB_PRJ_HIST h
            JOIN TB_PRJ_PTCP pt ON pt.EMP_ID = h.EMP_ID AND pt.PRJ_ID = h.PRJ_ID
            JOIN TB_PRJ p       ON p.PRJ_ID = h.PRJ_ID
            WHERE h.PRJ_HIST_ID = ?
            """;

    /** 6. 담당자가 그 발주처 소속인지 */
    public static final String COUNT_CLIENT_MANAGER = """
            SELECT COUNT(*)
            FROM TB_CLIENT_MGR
            WHERE CLIENT_ID = ?
              AND MGR_ID = ?
            """;

    /** 7. 같은 이력에 걸린 '접수' 요청 (있으면 그 요청ID) */
    public static final String FIND_OPEN_REQUEST_ID = """
            SELECT REPL_REQ_ID
            FROM TB_STAFF_REPL_REQ
            WHERE REPL_TRGT_HIST_ID = ?
              AND PROC_STAT = '접수'
            """;

    /** 8. 교체 요청 등록 (처리상태 '접수', 발주처는 프로젝트의 발주처) */
    public static final String INSERT_REQUEST = """
            INSERT INTO TB_STAFF_REPL_REQ (REQ_DT, REQ_RSN, PROC_STAT, REPL_TRGT_HIST_ID, CLIENT_ID, MGR_ID)
            VALUES (?, ?, '접수', ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<ReplRequest> findRequests(String procStat) {
        return jdbcTemplate.query(FIND_REQUESTS, (rs, i) -> new ReplRequest(
                rs.getInt("REPL_REQ_ID"),
                rs.getString("PROC_STAT"),
                rs.getObject("REQ_DT", LocalDate.class),
                rs.getObject("PROC_DT", LocalDate.class),
                rs.getString("REQ_RSN"),
                rs.getString("CLIENT_NM"),
                rs.getString("MGR_NM"),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("TRGT_EMP_ID"),
                rs.getString("TRGT_EMP_NM"),
                rs.getString("TRGT_ROLE_ID"),
                rs.getString("SUCC_EMP_ID"),
                rs.getString("SUCC_EMP_NM")), procStat, procStat);
    }

    public List<ActiveProject> findActiveProjects() {
        return jdbcTemplate.query(FIND_ACTIVE_PROJECTS, (rs, i) -> new ActiveProject(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getInt("CLIENT_ID"),
                rs.getString("CLIENT_NM")));
    }

    public List<ReplaceableHist> findReplaceableHists(String prjId) {
        return jdbcTemplate.query(FIND_REPLACEABLE_HISTS, (rs, i) -> new ReplaceableHist(
                rs.getInt("PRJ_HIST_ID"),
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getInt("OPEN_REQ_CNT")), prjId);
    }

    public List<ClientManager> findClientManagers(String prjId) {
        return jdbcTemplate.query(FIND_CLIENT_MANAGERS, (rs, i) -> new ClientManager(
                rs.getInt("MGR_ID"),
                rs.getString("MGR_NM"),
                rs.getBoolean("PRJ_MGR_YN")), prjId);
    }

    public Optional<TargetHist> findTargetHist(int prjHistId) {
        return jdbcTemplate.query(FIND_TARGET_HIST, (rs, i) -> new TargetHist(
                rs.getInt("PRJ_HIST_ID"),
                rs.getString("EMP_ID"),
                rs.getString("PRJ_ID"),
                rs.getObject("STRT_DT", LocalDate.class),
                rs.getObject("END_DT", LocalDate.class),
                rs.getString("ROLE_ID"),
                rs.getString("PRJ_STAT"),
                rs.getInt("CLIENT_ID")), prjHistId).stream().findFirst();
    }

    public boolean existsClientManager(int clientId, int mgrId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_CLIENT_MANAGER, Integer.class, clientId, mgrId);
        return count != null && count > 0;
    }

    public Optional<Integer> findOpenRequestId(int prjHistId) {
        return jdbcTemplate.queryForList(FIND_OPEN_REQUEST_ID, Integer.class, prjHistId).stream().findFirst();
    }

    /** @return 새 교체요청ID */
    public int insertRequest(LocalDate reqDt, String reqRsn, int replTrgtHistId, int clientId, int mgrId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(INSERT_REQUEST, Statement.RETURN_GENERATED_KEYS);
            ps.setObject(1, reqDt);
            ps.setString(2, reqRsn);
            ps.setInt(3, replTrgtHistId);
            ps.setInt(4, clientId);
            ps.setInt(5, mgrId);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().intValue();
    }
}

package com.databasedesign.SiCompany.memo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.memo.MemoDtos.Memo;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoKey;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoTarget;
import com.databasedesign.SiCompany.memo.MemoDtos.MyMemo;
import com.databasedesign.SiCompany.memo.MemoDtos.MyProject;
import com.databasedesign.SiCompany.memo.MemoDtos.Participation;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MemoRepository {

    /** 1. 내 프로젝트: 참여 중인 진행/지연 프로젝트, 내 직무, 내 투입 기간 (열린 투입이 있으면 종료일 없음) */
    public static final String FIND_MY_ACTIVE_PROJECTS = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM,
                   MIN(h.STRT_DT) AS MY_STRT_DT,
                   CASE WHEN COUNT(*) = COUNT(h.END_DT) THEN MAX(h.END_DT) END AS MY_END_DT
            FROM TB_PRJ_PTCP pt
            JOIN TB_PRJ p      ON p.PRJ_ID = pt.PRJ_ID
            JOIN TB_CLIENT c   ON c.CLIENT_ID = p.CLIENT_ID
            JOIN TB_ROLE r     ON r.ROLE_ID = pt.ROLE_ID
            JOIN TB_PRJ_HIST h ON h.EMP_ID = pt.EMP_ID AND h.PRJ_ID = pt.PRJ_ID
            WHERE pt.EMP_ID = ?
              AND p.PRJ_STAT IN ('진행', '지연')
            GROUP BY p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, p.STRT_DT, c.CLIENT_NM, pt.ROLE_ID, r.ROLE_NM
            ORDER BY p.STRT_DT DESC
            """;

    /** 2. 이 프로젝트에서 내 참여 (프로젝트 상태와 내 직무) */
    public static final String FIND_PARTICIPATION = """
            SELECT p.PRJ_ID, p.PRJ_NM, p.PRJ_STAT, pt.ROLE_ID
            FROM TB_PRJ_PTCP pt
            JOIN TB_PRJ p ON p.PRJ_ID = pt.PRJ_ID
            WHERE pt.EMP_ID = ?
              AND pt.PRJ_ID = ?
            """;

    /** 3. 메모 대상: PM이면 팀원 전원, 아니면 투입 기간이 하루 이상 겹친 직원 (열린 투입은 오늘까지로 본다) */
    public static final String FIND_MEMO_TARGETS = """
            SELECT t.EMP_ID, e.EMP_NM, t.ROLE_ID, r.ROLE_NM,
                   (SELECT COUNT(*)
                    FROM TB_EVAL_MEMO m
                    WHERE m.WRTR_EMP_ID = me.EMP_ID
                      AND m.TRGT_EMP_ID = t.EMP_ID
                      AND m.PRJ_ID = t.PRJ_ID) AS MEMO_CNT
            FROM TB_PRJ_PTCP me
            JOIN TB_PRJ_PTCP t ON t.PRJ_ID = me.PRJ_ID AND t.EMP_ID <> me.EMP_ID
            JOIN TB_EMP e      ON e.EMP_ID = t.EMP_ID
            JOIN TB_ROLE r     ON r.ROLE_ID = t.ROLE_ID
            WHERE me.EMP_ID = ?
              AND me.PRJ_ID = ?
              AND ( me.ROLE_ID = 'PM'
                    OR EXISTS (
                         SELECT 1
                         FROM TB_PRJ_HIST mh
                         JOIN TB_PRJ_HIST th ON th.PRJ_ID = mh.PRJ_ID AND th.EMP_ID = t.EMP_ID
                         WHERE mh.EMP_ID = me.EMP_ID
                           AND mh.PRJ_ID = me.PRJ_ID
                           AND GREATEST(mh.STRT_DT, th.STRT_DT)
                               <= LEAST(COALESCE(mh.END_DT, CURDATE()), COALESCE(th.END_DT, CURDATE()))
                       ) )
            ORDER BY FIELD(t.ROLE_ID, 'PM', 'PL', 'AN', 'DS', 'PG', 'TS'), e.EMP_NM
            """;

    /** 4. 제출된 평가 수 (작성자 = 평가자, 대상자 = 피평가자, 같은 프로젝트) */
    public static final String COUNT_SUBMITTED_EVAL = """
            SELECT COUNT(*)
            FROM TB_EVAL e
            LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
            LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
            WHERE e.PRJ_ID = ?
              AND e.EVLTE_EMP_ID = ?
              AND e.EVAL_STAT = '제출'
              AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
            """;

    /** 5. 대상별 내 메모 */
    public static final String FIND_MEMOS_BY_TARGET = """
            SELECT MEMO_ID, MEMO_CN, WRT_DTTM, MOD_DTTM
            FROM TB_EVAL_MEMO
            WHERE WRTR_EMP_ID = ?
              AND TRGT_EMP_ID = ?
              AND PRJ_ID = ?
            ORDER BY WRT_DTTM DESC, MEMO_ID DESC
            """;

    /** 6. 내 메모 전체 (평가메모 메뉴) */
    public static final String FIND_MY_MEMOS = """
            SELECT m.MEMO_ID, m.PRJ_ID, p.PRJ_NM, p.PRJ_STAT,
                   m.TRGT_EMP_ID, e.EMP_NM AS TRGT_EMP_NM,
                   m.MEMO_CN, m.WRT_DTTM, m.MOD_DTTM
            FROM TB_EVAL_MEMO m
            JOIN TB_PRJ p ON p.PRJ_ID = m.PRJ_ID
            JOIN TB_EMP e ON e.EMP_ID = m.TRGT_EMP_ID
            WHERE m.WRTR_EMP_ID = ?
            ORDER BY m.WRT_DTTM DESC, m.MEMO_ID DESC
            """;

    /** 7. 수정·삭제 전에 메모 한 건 조회 (본인이 쓴 것만) */
    public static final String FIND_OWNED_MEMO = """
            SELECT MEMO_ID, PRJ_ID, TRGT_EMP_ID
            FROM TB_EVAL_MEMO
            WHERE MEMO_ID = ?
              AND WRTR_EMP_ID = ?
            """;

    /** 8. 메모 작성 */
    public static final String INSERT_MEMO = """
            INSERT INTO TB_EVAL_MEMO (MEMO_CN, WRT_DTTM, WRTR_EMP_ID, TRGT_EMP_ID, PRJ_ID)
            VALUES (?, NOW(), ?, ?, ?)
            """;

    /** 9-1. 메모 수정 (작성자 본인 것만 바뀐다) */
    public static final String UPDATE_MEMO = """
            UPDATE TB_EVAL_MEMO
            SET MEMO_CN = ?,
                MOD_DTTM = NOW()
            WHERE MEMO_ID = ?
              AND WRTR_EMP_ID = ?
            """;

    /** 9-2. 메모 삭제 (작성자 본인 것만 지워진다) */
    public static final String DELETE_MEMO = """
            DELETE FROM TB_EVAL_MEMO
            WHERE MEMO_ID = ?
              AND WRTR_EMP_ID = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<MyProject> findMyActiveProjects(String empId) {
        return jdbcTemplate.query(FIND_MY_ACTIVE_PROJECTS, (rs, i) -> new MyProject(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getString("CLIENT_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getObject("MY_STRT_DT", LocalDate.class),
                rs.getObject("MY_END_DT", LocalDate.class)), empId);
    }

    public Optional<Participation> findParticipation(String empId, String prjId) {
        return jdbcTemplate.query(FIND_PARTICIPATION, (rs, i) -> new Participation(
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getString("ROLE_ID")), empId, prjId).stream().findFirst();
    }

    public List<MemoTarget> findMemoTargets(String empId, String prjId) {
        return jdbcTemplate.query(FIND_MEMO_TARGETS, (rs, i) -> new MemoTarget(
                rs.getString("EMP_ID"),
                rs.getString("EMP_NM"),
                rs.getString("ROLE_ID"),
                rs.getString("ROLE_NM"),
                rs.getInt("MEMO_CNT")), empId, prjId);
    }

    public int countSubmittedEval(String prjId, String evlteEmpId, String evltrEmpId) {
        Integer count = jdbcTemplate.queryForObject(COUNT_SUBMITTED_EVAL, Integer.class,
                prjId, evlteEmpId, evltrEmpId, evltrEmpId);
        return count == null ? 0 : count;
    }

    public List<Memo> findMemosByTarget(String wrtrEmpId, String trgtEmpId, String prjId) {
        return jdbcTemplate.query(FIND_MEMOS_BY_TARGET, (rs, i) -> new Memo(
                rs.getInt("MEMO_ID"),
                rs.getString("MEMO_CN"),
                rs.getObject("WRT_DTTM", LocalDateTime.class),
                rs.getObject("MOD_DTTM", LocalDateTime.class)), wrtrEmpId, trgtEmpId, prjId);
    }

    public List<MyMemo> findMyMemos(String wrtrEmpId) {
        return jdbcTemplate.query(FIND_MY_MEMOS, (rs, i) -> new MyMemo(
                rs.getInt("MEMO_ID"),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("PRJ_STAT"),
                rs.getString("TRGT_EMP_ID"),
                rs.getString("TRGT_EMP_NM"),
                rs.getString("MEMO_CN"),
                rs.getObject("WRT_DTTM", LocalDateTime.class),
                rs.getObject("MOD_DTTM", LocalDateTime.class)), wrtrEmpId);
    }

    public Optional<MemoKey> findOwnedMemo(int memoId, String wrtrEmpId) {
        return jdbcTemplate.query(FIND_OWNED_MEMO, (rs, i) -> new MemoKey(
                rs.getInt("MEMO_ID"),
                rs.getString("PRJ_ID"),
                rs.getString("TRGT_EMP_ID")), memoId, wrtrEmpId).stream().findFirst();
    }

    public void insertMemo(String memoCn, String wrtrEmpId, String trgtEmpId, String prjId) {
        jdbcTemplate.update(INSERT_MEMO, memoCn, wrtrEmpId, trgtEmpId, prjId);
    }

    public int updateMemo(int memoId, String wrtrEmpId, String memoCn) {
        return jdbcTemplate.update(UPDATE_MEMO, memoCn, memoId, wrtrEmpId);
    }

    public int deleteMemo(int memoId, String wrtrEmpId) {
        return jdbcTemplate.update(DELETE_MEMO, memoId, wrtrEmpId);
    }
}

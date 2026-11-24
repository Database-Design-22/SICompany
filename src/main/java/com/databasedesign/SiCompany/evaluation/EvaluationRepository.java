package com.databasedesign.SiCompany.evaluation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Completeness;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalSummary;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Evaluation;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.ItemContent;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Question;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class EvaluationRepository {

    /** 1. 내 평가 목록: 내가 평가자인 PM·동료 평가 (작성중이 위로) */
    public static final String FIND_MY_EVALUATIONS = """
            SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
                   e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM,
                   (SELECT COUNT(*)
                    FROM TB_EVAL_QSTN_SCORE s
                    WHERE s.EVAL_ID = e.EVAL_ID) AS SCORE_CNT,
                   (SELECT COUNT(*)
                    FROM TB_EVAL_QSTN q
                    WHERE (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
                       OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')) AS QSTN_CNT
            FROM TB_EVAL e
            LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
            LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
            JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
            JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
            WHERE (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
            ORDER BY FIELD(e.EVAL_STAT, '작성중', '제출'), e.EVAL_DT DESC, e.EVAL_ID
            """;

    /** 2. 평가 한 건 (내가 평가자인 것만) */
    public static final String FIND_MY_EVALUATION = """
            SELECT e.EVAL_ID, e.EVAL_DIV, e.EVAL_STAT, e.EVAL_DT,
                   e.PRJ_ID, p.PRJ_NM, e.EVLTE_EMP_ID, t.EMP_NM AS EVLTE_EMP_NM
            FROM TB_EVAL e
            LEFT JOIN TB_PM_EVAL pm   ON pm.EVAL_ID = e.EVAL_ID
            LEFT JOIN TB_PEER_EVAL pe ON pe.EVAL_ID = e.EVAL_ID
            JOIN TB_PRJ p ON p.PRJ_ID = e.PRJ_ID
            JOIN TB_EMP t ON t.EMP_ID = e.EVLTE_EMP_ID
            WHERE e.EVAL_ID = ?
              AND (pm.EVLTR_EMP_ID = ? OR pe.EVLTR_EMP_ID = ?)
            """;

    /** 3. 평가구분에 맞는 문항과 입력한 점수 (PM은 PM_USE_YN, 동료는 PEER_USE_YN) */
    public static final String FIND_QUESTIONS = """
            SELECT q.QSTN_ID, q.QSTN_CN, q.EVAL_ITEM_ID, s.SCORE
            FROM TB_EVAL e
            JOIN TB_EVAL_QSTN q
              ON (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
              OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')
            LEFT JOIN TB_EVAL_QSTN_SCORE s ON s.EVAL_ID = e.EVAL_ID AND s.QSTN_ID = q.QSTN_ID
            WHERE e.EVAL_ID = ?
            ORDER BY q.EVAL_ITEM_ID DESC, q.QSTN_ID
            """;

    /** 4. 평가항목별 내용 (WORK, COMM) */
    public static final String FIND_CONTENTS = """
            SELECT i.EVAL_ITEM_ID, i.EVAL_ITEM_NM, c.ADV, c.DISADV, c.EXPC
            FROM TB_EVAL_ITEM i
            LEFT JOIN TB_EVAL_CN c ON c.EVAL_ID = ? AND c.EVAL_ITEM_ID = i.EVAL_ITEM_ID
            ORDER BY i.EVAL_ITEM_ID DESC
            """;

    /** 5. 문항 점수 저장 (있으면 고침) */
    public static final String UPSERT_SCORE = """
            INSERT INTO TB_EVAL_QSTN_SCORE (EVAL_ID, QSTN_ID, SCORE)
            VALUES (?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE SCORE = new.SCORE
            """;

    /** 6. 문항 점수 지우기 (임시 저장에서 칸을 비웠을 때) */
    public static final String DELETE_SCORE = """
            DELETE FROM TB_EVAL_QSTN_SCORE
            WHERE EVAL_ID = ?
              AND QSTN_ID = ?
            """;

    /** 7. 평가내용 저장 (있으면 고침) */
    public static final String UPSERT_CONTENT = """
            INSERT INTO TB_EVAL_CN (EVAL_ID, EVAL_ITEM_ID, ADV, DISADV, EXPC)
            VALUES (?, ?, ?, ?, ?) AS new
            ON DUPLICATE KEY UPDATE ADV = new.ADV,
                                    DISADV = new.DISADV,
                                    EXPC = new.EXPC
            """;

    /** 8. 제출 전 완성 여부 검사 (모든 문항 점수, 두 항목의 장점·단점·바라는 점) */
    public static final String FIND_COMPLETENESS = """
            SELECT
              (SELECT COUNT(*)
               FROM TB_EVAL_QSTN q
               WHERE (e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
                  OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y')) AS QSTN_CNT,
              (SELECT COUNT(*)
               FROM TB_EVAL_QSTN_SCORE s
               JOIN TB_EVAL_QSTN q ON q.QSTN_ID = s.QSTN_ID
               WHERE s.EVAL_ID = e.EVAL_ID
                 AND ((e.EVAL_DIV = 'PM' AND q.PM_USE_YN = 'Y')
                   OR (e.EVAL_DIV = '동료' AND q.PEER_USE_YN = 'Y'))) AS SCORE_CNT,
              (SELECT COUNT(*) FROM TB_EVAL_ITEM) AS ITEM_CNT,
              (SELECT COUNT(*)
               FROM TB_EVAL_CN c
               WHERE c.EVAL_ID = e.EVAL_ID
                 AND TRIM(c.ADV) <> ''
                 AND TRIM(c.DISADV) <> ''
                 AND TRIM(c.EXPC) <> '') AS CN_CNT
            FROM TB_EVAL e
            WHERE e.EVAL_ID = ?
            """;

    /** 9. 제출 처리 (작성중일 때만 바뀐다) */
    public static final String SUBMIT_EVALUATION = """
            UPDATE TB_EVAL
            SET EVAL_STAT = '제출',
                EVAL_DT = CURDATE()
            WHERE EVAL_ID = ?
              AND EVAL_STAT = '작성중'
            """;

    /** 10. 그 (평가자, 피평가자, 프로젝트)의 평가메모 삭제 */
    public static final String DELETE_MEMOS_OF_EVALUATION = """
            DELETE FROM TB_EVAL_MEMO
            WHERE WRTR_EMP_ID = ?
              AND TRGT_EMP_ID = ?
              AND PRJ_ID = ?
            """;

    private static final RowMapper<Evaluation> EVALUATION_MAPPER = (rs, i) -> new Evaluation(
            rs.getInt("EVAL_ID"),
            rs.getString("EVAL_DIV"),
            rs.getString("EVAL_STAT"),
            rs.getObject("EVAL_DT", LocalDate.class),
            rs.getString("PRJ_ID"),
            rs.getString("PRJ_NM"),
            rs.getString("EVLTE_EMP_ID"),
            rs.getString("EVLTE_EMP_NM"));

    private final JdbcTemplate jdbcTemplate;

    public List<EvalSummary> findMyEvaluations(String evltrEmpId) {
        return jdbcTemplate.query(FIND_MY_EVALUATIONS, (rs, i) -> new EvalSummary(
                rs.getInt("EVAL_ID"),
                rs.getString("EVAL_DIV"),
                rs.getString("EVAL_STAT"),
                rs.getObject("EVAL_DT", LocalDate.class),
                rs.getString("PRJ_ID"),
                rs.getString("PRJ_NM"),
                rs.getString("EVLTE_EMP_ID"),
                rs.getString("EVLTE_EMP_NM"),
                rs.getInt("SCORE_CNT"),
                rs.getInt("QSTN_CNT")), evltrEmpId, evltrEmpId);
    }

    public Optional<Evaluation> findMyEvaluation(int evalId, String evltrEmpId) {
        return jdbcTemplate.query(FIND_MY_EVALUATION, EVALUATION_MAPPER, evalId, evltrEmpId, evltrEmpId)
                .stream().findFirst();
    }

    public List<Question> findQuestions(int evalId) {
        return jdbcTemplate.query(FIND_QUESTIONS, (rs, i) -> new Question(
                rs.getInt("QSTN_ID"),
                rs.getString("QSTN_CN"),
                rs.getString("EVAL_ITEM_ID"),
                rs.getObject("SCORE", Integer.class)), evalId);
    }

    public List<ItemContent> findContents(int evalId) {
        return jdbcTemplate.query(FIND_CONTENTS, (rs, i) -> new ItemContent(
                rs.getString("EVAL_ITEM_ID"),
                rs.getString("EVAL_ITEM_NM"),
                rs.getString("ADV"),
                rs.getString("DISADV"),
                rs.getString("EXPC")), evalId);
    }

    public void upsertScore(int evalId, int qstnId, int score) {
        jdbcTemplate.update(UPSERT_SCORE, evalId, qstnId, score);
    }

    public void deleteScore(int evalId, int qstnId) {
        jdbcTemplate.update(DELETE_SCORE, evalId, qstnId);
    }

    public void upsertContent(int evalId, String evalItemId, String adv, String disadv, String expc) {
        jdbcTemplate.update(UPSERT_CONTENT, evalId, evalItemId, adv, disadv, expc);
    }

    public Completeness findCompleteness(int evalId) {
        return jdbcTemplate.queryForObject(FIND_COMPLETENESS, (rs, i) -> new Completeness(
                rs.getInt("QSTN_CNT"),
                rs.getInt("SCORE_CNT"),
                rs.getInt("ITEM_CNT"),
                rs.getInt("CN_CNT")), evalId);
    }

    public int submit(int evalId) {
        return jdbcTemplate.update(SUBMIT_EVALUATION, evalId);
    }

    public int deleteMemos(String wrtrEmpId, String trgtEmpId, String prjId) {
        return jdbcTemplate.update(DELETE_MEMOS_OF_EVALUATION, wrtrEmpId, trgtEmpId, prjId);
    }
}

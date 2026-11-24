package com.databasedesign.SiCompany.evaluation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Completeness;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.ContentInput;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalInput;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalPage;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalSummary;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Evaluation;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.ItemContent;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.Question;
import com.databasedesign.SiCompany.memo.MemoRepository;

import lombok.RequiredArgsConstructor;

/**
 * 평가 작성과 제출 규칙 (CLAUDE.md 시나리오 1)
 * - 현재 사용자가 평가자(TB_PM_EVAL 또는 TB_PEER_EVAL의 EVLTR_EMP_ID)인 '작성중' 평가만 작성한다.
 * - 문항은 평가구분에 맞는 사용여부가 'Y'인 것만 쓴다. 점수는 0~10 정수.
 * - 임시 저장은 점수와 내용만 저장하고 상태는 '작성중'으로 둔다.
 * - 제출은 한 트랜잭션: 완성 여부 확인 → EVAL_STAT='제출', EVAL_DT=오늘 → 그 평가의 메모 삭제.
 * - 제출된 평가의 점수와 내용은 고칠 수 없다.
 */
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final EvaluationRepository evaluationRepository;
    private final MemoRepository memoRepository;

    public List<EvalSummary> findMyEvaluations(CurrentUser user) {
        return evaluationRepository.findMyEvaluations(user.empId());
    }

    /** 제출된 평가는 메모가 이미 삭제됐으므로 메모를 조회하지 않는다. */
    public EvalPage getEvalPage(CurrentUser user, int evalId) {
        Evaluation evaluation = getMyEvaluation(user, evalId);
        return new EvalPage(
                evaluation,
                evaluationRepository.findQuestions(evalId),
                evaluationRepository.findContents(evalId),
                evaluation.isSubmitted()
                        ? List.of()
                        : memoRepository.findMemosByTarget(user.empId(), evaluation.evlteEmpId(), evaluation.prjId()));
    }

    /** 임시 저장: 점수와 내용만 저장하고 상태는 '작성중' 그대로 둔다. */
    @Transactional
    public void saveDraft(CurrentUser user, int evalId, EvalInput input) {
        Evaluation evaluation = getMyEvaluation(user, evalId);
        saveInput(evaluation, input);
    }

    /**
     * 제출 (한 트랜잭션). 중간에 예외가 나면 이번 입력의 저장까지 모두 되돌린다.
     * @return 삭제한 평가메모 수
     */
    @Transactional
    public int submit(CurrentUser user, int evalId, EvalInput input) {
        Evaluation evaluation = getMyEvaluation(user, evalId);

        // 1. 화면 입력 저장
        saveInput(evaluation, input);

        // 2. DB에 저장된 값으로 완성 여부 확인
        Completeness c = evaluationRepository.findCompleteness(evalId);
        if (c.scoreCnt() < c.qstnCnt()) {
            throw new BusinessException("문항 점수가 빠졌습니다. 모든 문항에 점수를 입력하세요. ("
                    + c.scoreCnt() + "/" + c.qstnCnt() + ")");
        }
        if (c.cnCnt() < c.itemCnt()) {
            throw new BusinessException("평가내용이 빠졌습니다. 업무수행(WORK)과 커뮤니케이션(COMM) 항목마다 "
                    + "장점, 단점, 바라는 점을 모두 쓰세요. (" + c.cnCnt() + "/" + c.itemCnt() + ")");
        }

        // 3. 상태 '제출', 평가일자 오늘
        if (evaluationRepository.submit(evalId) != 1) {
            throw new BusinessException("이미 제출된 평가입니다.");
        }

        // 4. 그 (평가자, 피평가자, 프로젝트)의 평가메모 삭제
        return evaluationRepository.deleteMemos(user.empId(), evaluation.evlteEmpId(), evaluation.prjId());
    }

    private Evaluation getMyEvaluation(CurrentUser user, int evalId) {
        return evaluationRepository.findMyEvaluation(evalId, user.empId())
                .orElseThrow(() -> new BusinessException("본인이 평가자인 평가만 볼 수 있습니다. (평가 " + evalId + ")"));
    }

    private void saveInput(Evaluation evaluation, EvalInput input) {
        if (evaluation.isSubmitted()) {
            throw new BusinessException("제출된 평가는 고칠 수 없습니다.");
        }
        int evalId = evaluation.evalId();

        // 점수: 평가구분에 맞는 문항만 받는다. 입력한 칸은 저장하고 비운 칸은 지운다.
        List<Question> questions = evaluationRepository.findQuestions(evalId);
        Set<Integer> allowed = questions.stream().map(Question::qstnId).collect(Collectors.toSet());
        for (Integer qstnId : input.scores().keySet()) {
            if (!allowed.contains(qstnId)) {
                throw new BusinessException(evaluation.evalDiv() + " 평가에서 쓰지 않는 문항입니다. (문항 " + qstnId + ")");
            }
        }
        for (Question q : questions) {
            String raw = input.scores().get(q.qstnId());
            if (raw == null || raw.isBlank()) {
                evaluationRepository.deleteScore(evalId, q.qstnId());
            } else {
                evaluationRepository.upsertScore(evalId, q.qstnId(), parseScore(raw, q.qstnId()));
            }
        }

        // 내용: WORK, COMM 항목별 장점, 단점, 바라는 점. 빈 칸은 NULL로 저장한다.
        for (ItemContent item : evaluationRepository.findContents(evalId)) {
            ContentInput ci = input.contents().get(item.evalItemId());
            if (ci == null) {
                continue;
            }
            evaluationRepository.upsertContent(evalId, item.evalItemId(),
                    cleanContent(ci.adv(), item, "장점"),
                    cleanContent(ci.disadv(), item, "단점"),
                    cleanContent(ci.expc(), item, "바라는 점"));
        }
    }

    private int parseScore(String raw, int qstnId) {
        try {
            int score = Integer.parseInt(raw.strip());
            if (score >= 0 && score <= 10) {
                return score;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 같은 메시지로 막는다.
        }
        throw new BusinessException("점수는 0~10 사이의 정수로 입력하세요. (문항 " + qstnId + ": " + raw + ")");
    }

    private String cleanContent(String value, ItemContent item, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String content = value.strip();
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(item.evalItemNm() + " " + label + "은(는) " + MAX_CONTENT_LENGTH + "자까지 쓸 수 있습니다.");
        }
        return content;
    }
}

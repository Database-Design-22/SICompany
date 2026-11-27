package com.databasedesign.SiCompany.evaluation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.ContentInput;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalInput;
import com.databasedesign.SiCompany.evaluation.EvaluationDtos.EvalPage;
import com.databasedesign.SiCompany.memo.MemoRepository;

import lombok.RequiredArgsConstructor;

/**
 * 평가 작성과 제출 (시나리오 1)
 * ① 내 평가 목록 → ② 평가 화면 (임시 저장, 제출, 제출 후 읽기 전용)
 */
@Controller
@RequiredArgsConstructor
public class EvaluationController {

    private static final String SCORE_PREFIX = "score_";

    private final EvaluationService evaluationService;

    /** ① 내 평가 목록 */
    @GetMapping("/evaluations")
    public String list(CurrentUser user, Model model) {
        user.requireDev();
        model.addAttribute("evaluations", evaluationService.findMyEvaluations(user));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("내가 평가자인 PM·동료 평가", EvaluationRepository.FIND_MY_EVALUATIONS)));
        return "evaluation/list";
    }

    /** ② 평가 화면 */
    @GetMapping("/evaluations/{evalId}")
    public String form(@PathVariable int evalId, CurrentUser user, Model model) {
        user.requireDev();
        EvalPage page = evaluationService.getEvalPage(user, evalId);
        model.addAttribute("page", page);

        // 실제로 실행한 SQL만 보여 준다. 제출된 평가는 메모 조회와 저장·제출 SQL을 실행하지 않는다.
        List<ExecutedSql> sqls = new ArrayList<>(List.of(
                new ExecutedSql("평가 한 건 (내가 평가자인지 확인)", EvaluationRepository.FIND_MY_EVALUATION),
                new ExecutedSql("평가구분에 맞는 문항과 입력한 점수", EvaluationRepository.FIND_QUESTIONS),
                new ExecutedSql("평가항목별 내용", EvaluationRepository.FIND_CONTENTS)));
        if (page.evaluation().isCustomer()) {
            sqls.add(new ExecutedSql("고객평가 의뢰·회수 정보", EvaluationRepository.FIND_CUST_INFO));
        }
        if (!page.evaluation().isSubmitted() && page.evaluation().isCustomer()) {
            sqls.addAll(List.of(
                    new ExecutedSql("저장할 때: 문항 점수", EvaluationRepository.UPSERT_SCORE),
                    new ExecutedSql("저장할 때: 비운 점수 지우기", EvaluationRepository.DELETE_SCORE),
                    new ExecutedSql("저장할 때: 평가내용", EvaluationRepository.UPSERT_CONTENT),
                    new ExecutedSql("제출할 때 ①: 완성 여부 확인", EvaluationRepository.FIND_COMPLETENESS),
                    new ExecutedSql("제출할 때 ②: 상태 '제출', 평가일자 오늘", EvaluationRepository.SUBMIT_EVALUATION)));
        } else if (!page.evaluation().isSubmitted()) {
            sqls.addAll(List.of(
                    new ExecutedSql("이 피평가자에 대해 내가 쓴 메모", MemoRepository.FIND_MEMOS_BY_TARGET),
                    new ExecutedSql("저장할 때: 문항 점수", EvaluationRepository.UPSERT_SCORE),
                    new ExecutedSql("저장할 때: 비운 점수 지우기", EvaluationRepository.DELETE_SCORE),
                    new ExecutedSql("저장할 때: 평가내용", EvaluationRepository.UPSERT_CONTENT),
                    new ExecutedSql("제출할 때 ①: 완성 여부 확인", EvaluationRepository.FIND_COMPLETENESS),
                    new ExecutedSql("제출할 때 ②: 상태 '제출', 평가일자 오늘", EvaluationRepository.SUBMIT_EVALUATION),
                    new ExecutedSql("제출할 때 ③: 평가메모 삭제", EvaluationRepository.DELETE_MEMOS_OF_EVALUATION)));
        }
        model.addAttribute("executedSqls", sqls);
        return "evaluation/form";
    }

    /** 임시 저장(action=save) 또는 제출(action=submit) */
    @PostMapping("/evaluations/{evalId}")
    public String save(@PathVariable int evalId, @RequestParam String action,
                       @RequestParam Map<String, String> params,
                       CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        EvalInput input = toInput(params);
        switch (action) {
            case "save" -> {
                evaluationService.saveDraft(user, evalId, input);
                redirect.addFlashAttribute("notice", "임시 저장했습니다. 상태는 '작성중' 그대로입니다.");
            }
            case "submit" -> {
                int deletedMemos = evaluationService.submit(user, evalId, input);
                redirect.addFlashAttribute("notice", deletedMemos < 0 ? "평가를 제출했습니다."
                        : "평가를 제출했습니다. 이 피평가자에 대한 평가메모 " + deletedMemos + "건을 삭제했습니다.");
            }
            default -> throw new BusinessException("알 수 없는 요청입니다. (" + action + ")");
        }
        return "redirect:/evaluations/" + evalId;
    }

    /** 폼 이름: score_{문항ID}, adv_{항목ID}, disadv_{항목ID}, expc_{항목ID} */
    private EvalInput toInput(Map<String, String> params) {
        Map<Integer, String> scores = new HashMap<>();
        Map<String, ContentInput> contents = new HashMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String name = e.getKey();
            if (name.startsWith(SCORE_PREFIX)) {
                try {
                    scores.put(Integer.parseInt(name.substring(SCORE_PREFIX.length())), e.getValue());
                } catch (NumberFormatException ex) {
                    throw new BusinessException("잘못된 문항 번호입니다. (" + name + ")");
                }
            } else if (name.startsWith("adv_")) {
                String item = name.substring("adv_".length());
                contents.put(item, new ContentInput(e.getValue(),
                        params.get("disadv_" + item), params.get("expc_" + item)));
            }
        }
        return new EvalInput(scores, contents);
    }
}

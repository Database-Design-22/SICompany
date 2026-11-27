package com.databasedesign.SiCompany.adjustment;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.AdjRound;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;

import lombok.RequiredArgsConstructor;

/**
 * 보정점수 조회와 확정 (시나리오 1, 경영진만)
 * ① 보정회차 목록과 확정 → ② 평가 결과 / ③ 피평가자별 요약
 */
@Controller
@RequiredArgsConstructor
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    /** ① 보정회차 목록 */
    @GetMapping("/adjustments")
    public String rounds(CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        model.addAttribute("rounds", adjustmentService.findRounds());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("보정회차 목록", AdjustmentRepository.FIND_ROUNDS),
                new ExecutedSql("확정할 때", AdjustmentRepository.CONFIRM_ROUND)));
        return "adjustment/rounds";
    }

    @PostMapping("/adjustments/{year}/confirm")
    public String confirm(@PathVariable int year, CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.EXE);
        adjustmentService.confirm(year);
        redirect.addFlashAttribute("notice", year + "년 보정회차를 확정했습니다. 이제 보정점수를 볼 수 있습니다.");
        return "redirect:/adjustments";
    }

    /** ④ 보정 실행·재계산과 평가자 성향 (year: 평가자보정값을 볼 연도) */
    @GetMapping("/adjustments/run")
    public String runPage(@RequestParam(required = false) Integer year, CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        List<AdjRound> rounds = adjustmentService.findRounds();
        Integer selectedYear = year != null ? year : (rounds.isEmpty() ? null : rounds.get(0).adjYr());
        model.addAttribute("rounds", rounds);
        model.addAttribute("year", selectedYear);
        model.addAttribute("nextYear", rounds.isEmpty() ? LocalDate.now().getYear() : rounds.get(0).adjYr() + 1);
        model.addAttribute("selectedRound", selectedYear == null ? null : adjustmentService.findRound(selectedYear).orElse(null));
        model.addAttribute("values", selectedYear == null ? List.of() : adjustmentService.findEvltrAdjVals(selectedYear));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("보정회차 목록", AdjustmentRepository.FIND_ROUNDS),
                new ExecutedSql("평가자 성향 (그 연도의 평가자보정값)", AdjustmentRepository.FIND_EVLTR_ADJ_VALS),
                new ExecutedSql("실행 ①: 보정회차 생성", AdjustmentRepository.INSERT_ROUND),
                new ExecutedSql("실행 ②: 평가자보정값 계산·저장", AdjustmentRepository.INSERT_EVLTR_ADJ_VALS),
                new ExecutedSql("재계산 ①: 정책값 변경", AdjustmentRepository.UPDATE_ROUND_POLICY),
                new ExecutedSql("재계산 ②: 적용보정값 다시 계산", AdjustmentRepository.UPDATE_EVLTR_ADJ_VALS)));
        return "adjustment/run";
    }

    @PostMapping("/adjustments/run")
    public String run(@RequestParam(required = false) String adjYr,
                      @RequestParam(required = false) String tndcyCalcPrd,
                      @RequestParam(required = false) String minPrjCnt,
                      @RequestParam(required = false) String maxAdjWdth,
                      CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.EXE);
        int count = adjustmentService.run(user.empId(), adjYr, tndcyCalcPrd, minPrjCnt, maxAdjWdth);
        redirect.addFlashAttribute("notice", adjYr + "년 보정을 실행했습니다. 평가자보정값 " + count + "건을 저장했습니다. (확정 전)");
        return "redirect:/adjustments/run?year=" + adjYr;
    }

    @PostMapping("/adjustments/{year}/recalculate")
    public String recalculate(@PathVariable int year,
                              @RequestParam(required = false) String minPrjCnt,
                              @RequestParam(required = false) String maxAdjWdth,
                              CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.EXE);
        adjustmentService.recalculate(year, minPrjCnt, maxAdjWdth);
        redirect.addFlashAttribute("notice", year + "년 적용보정값을 최소 프로젝트 " + minPrjCnt + "개, 최대 보정폭 ±"
                + maxAdjWdth + "점으로 다시 계산했습니다.");
        return "redirect:/adjustments/run?year=" + year;
    }

    /** ② 평가 결과와 ③ 피평가자별 요약 */
    @GetMapping("/adjustments/results")
    public String results(@RequestParam(required = false) Integer year,
                          @RequestParam(required = false) String div,
                          CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        List<Integer> years = adjustmentService.findEvalYears();
        int selectedYear = year != null ? year : (years.isEmpty() ? LocalDate.now().getYear() : years.get(0));
        // 처음 들어오면 PM, "전체"를 고르면 빈 값(div=)이 온다. (defaultValue는 빈 값에도 적용되므로 쓰지 않는다)
        String selectedDiv = adjustmentService.normalizeDiv(div == null ? "PM" : div);

        model.addAttribute("years", years);
        model.addAttribute("divs", AdjustmentService.EVAL_DIVS);
        model.addAttribute("year", selectedYear);
        model.addAttribute("div", selectedDiv == null ? "" : selectedDiv);
        model.addAttribute("round", adjustmentService.findRound(selectedYear).orElse(null));
        model.addAttribute("results", adjustmentService.findEvalResults(selectedYear, selectedDiv));
        model.addAttribute("summaries", adjustmentService.findEvaluateeSummary(selectedYear, selectedDiv));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("평가 결과 (성능 실험 대상)", AdjustmentRepository.FIND_EVAL_RESULTS),
                new ExecutedSql("피평가자별 요약 (평가 결과 SQL을 감싼다)", AdjustmentRepository.FIND_EVALUATEE_SUMMARY),
                new ExecutedSql("연도 선택 목록", AdjustmentRepository.FIND_EVAL_YEARS),
                new ExecutedSql("보정회차 (확정 여부 표시)", AdjustmentRepository.FIND_ROUNDS)));
        return "adjustment/results";
    }
}

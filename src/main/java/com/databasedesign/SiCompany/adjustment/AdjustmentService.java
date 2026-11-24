package com.databasedesign.SiCompany.adjustment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.AdjRound;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvalResult;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvaluateeSummary;
import com.databasedesign.SiCompany.common.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 보정점수 조회와 확정 규칙 (CLAUDE.md 시나리오 1)
 * - 그 연도의 보정회차가 확정됐을 때만 보정점수를 보여 준다. 고객평가는 보정하지 않는다.
 * - 확정: CNFM_DT = 오늘. 확정된 회차는 다시 바꿀 수 없다.
 */
@Service
@RequiredArgsConstructor
public class AdjustmentService {

    /** 평가구분 선택값. 빈 문자열은 전체 */
    public static final List<String> EVAL_DIVS = List.of("PM", "동료", "고객");
    private static final Set<String> ALLOWED_DIVS = Set.copyOf(EVAL_DIVS);

    private final AdjustmentRepository adjustmentRepository;

    public List<AdjRound> findRounds() {
        return adjustmentRepository.findRounds();
    }

    public Optional<AdjRound> findRound(int year) {
        return findRounds().stream().filter(r -> r.adjYr() == year).findFirst();
    }

    public List<Integer> findEvalYears() {
        return adjustmentRepository.findEvalYears();
    }

    public List<EvalResult> findEvalResults(int year, String evalDiv) {
        return adjustmentRepository.findEvalResults(yearStart(year), yearStart(year + 1), normalizeDiv(evalDiv));
    }

    public List<EvaluateeSummary> findEvaluateeSummary(int year, String evalDiv) {
        return adjustmentRepository.findEvaluateeSummary(yearStart(year), yearStart(year + 1), normalizeDiv(evalDiv));
    }

    @Transactional
    public void confirm(int year) {
        AdjRound round = findRound(year)
                .orElseThrow(() -> new BusinessException(year + "년 보정회차가 없습니다."));
        if (round.isConfirmed()) {
            throw new BusinessException(year + "년 보정회차는 " + round.cnfmDt() + "에 이미 확정되어 바꿀 수 없습니다.");
        }
        if (LocalDate.now().isBefore(round.execDt())) {
            throw new BusinessException("실행일자(" + round.execDt() + ") 전에는 확정할 수 없습니다.");
        }
        if (adjustmentRepository.confirmRound(year) != 1) {
            throw new BusinessException(year + "년 보정회차는 이미 확정되었습니다.");
        }
    }

    /** 빈 값이면 전체(null), 그 밖에는 PM·동료·고객만 받는다. */
    public String normalizeDiv(String evalDiv) {
        if (evalDiv == null || evalDiv.isBlank()) {
            return null;
        }
        if (!ALLOWED_DIVS.contains(evalDiv)) {
            throw new BusinessException("평가구분은 PM, 동료, 고객 중에서 고르세요. (" + evalDiv + ")");
        }
        return evalDiv;
    }

    private LocalDate yearStart(int year) {
        return LocalDate.of(year, 1, 1);
    }
}

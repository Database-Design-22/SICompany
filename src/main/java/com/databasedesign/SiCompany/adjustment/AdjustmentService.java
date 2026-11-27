package com.databasedesign.SiCompany.adjustment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.AdjRound;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvalResult;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvaluateeSummary;
import com.databasedesign.SiCompany.adjustment.AdjustmentDtos.EvltrAdjVal;
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

    public List<EvltrAdjVal> findEvltrAdjVals(int year) {
        return adjustmentRepository.findEvltrAdjVals(year);
    }

    /**
     * 보정 실행 (한 트랜잭션): 보정회차 생성 → 평가자보정값 계산·저장.
     * 실행일자는 오늘이고, 성향산정기간은 실행일자 기준 최근 N년이다. 같은 연도 회차가 있으면 막는다.
     * @return 저장한 평가자보정값 수
     */
    @Transactional
    public int run(String execrEmpId, String yearText, String tndcyText, String minPrjText, String maxWidthText) {
        int year = parseInt(yearText, "보정연도", 2000, LocalDate.now().getYear() + 1);
        int tndcy = parseInt(tndcyText, "성향산정기간", 1, 10);
        int minPrj = parseInt(minPrjText, "최소프로젝트수", 1, 20);
        BigDecimal maxWidth = parseWidth(maxWidthText);
        if (findRound(year).isPresent()) {
            throw new BusinessException(year + "년 보정회차가 이미 있습니다. 확정 전이면 아래에서 재계산하세요.");
        }
        adjustmentRepository.insertRound(year, tndcy, minPrj, maxWidth, execrEmpId);
        return adjustmentRepository.insertEvltrAdjVals(year);
    }

    /**
     * 재계산 (한 트랜잭션): 확정 전 회차의 최소프로젝트수와 최대보정폭을 바꾸고 적용보정값을 다시 계산한다.
     * 성향산정기간과 실행일자는 바꾸지 않는다 (평가자 명단이 그대로 유지된다).
     */
    @Transactional
    public void recalculate(int year, String minPrjText, String maxWidthText) {
        AdjRound round = findRound(year)
                .orElseThrow(() -> new BusinessException(year + "년 보정회차가 없습니다."));
        if (round.isConfirmed()) {
            throw new BusinessException(year + "년 보정회차는 " + round.cnfmDt() + "에 확정되어 다시 계산할 수 없습니다.");
        }
        int minPrj = parseInt(minPrjText, "최소프로젝트수", 1, 20);
        BigDecimal maxWidth = parseWidth(maxWidthText);
        if (adjustmentRepository.updateRoundPolicy(year, minPrj, maxWidth) != 1) {
            throw new BusinessException(year + "년 보정회차는 이미 확정되었습니다.");
        }
        adjustmentRepository.updateEvltrAdjVals(year);
    }

    private int parseInt(String text, String label, int min, int max) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.strip());
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 같은 메시지로 막는다.
        }
        throw new BusinessException(label + "은(는) " + min + "~" + max + " 사이의 정수로 입력하세요. (" + text + ")");
    }

    /** 최대보정폭: 0.1 ~ 9.9, 소수 첫째 자리까지 (DECIMAL(3,1)) */
    private BigDecimal parseWidth(String text) {
        try {
            BigDecimal value = new BigDecimal(text == null ? "" : text.strip());
            if (value.scale() <= 1 && value.compareTo(new BigDecimal("0.1")) >= 0
                    && value.compareTo(new BigDecimal("9.9")) <= 0) {
                return value.setScale(1);
            }
        } catch (NumberFormatException ignored) {
            // 아래에서 같은 메시지로 막는다.
        }
        throw new BusinessException("최대보정폭은 0.1~9.9 사이로, 소수 첫째 자리까지 입력하세요. (" + text + ")");
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

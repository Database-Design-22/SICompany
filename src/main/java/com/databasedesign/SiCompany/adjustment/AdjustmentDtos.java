package com.databasedesign.SiCompany.adjustment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 보정점수 조회와 확정 화면에서 쓰는 DTO 모음.
 */
public final class AdjustmentDtos {

    private AdjustmentDtos() {
    }

    /** ① 보정회차 한 줄 */
    public record AdjRound(int adjYr, int tndcyCalcPrd, int minPrjCnt, BigDecimal maxAdjWdth,
                           LocalDate execDt, LocalDate cnfmDt, String execrEmpId, String execrNm,
                           int adjValCnt) {
        public boolean isConfirmed() {
            return cnfmDt != null;
        }
    }

    /**
     * ② 평가 결과 한 줄.
     * aplyAdjVal: 고객평가면 null (보정 안 함)
     * rndYr: 그 연도의 보정회차가 없으면 null
     * adjScore: 회차가 확정된 경우와 고객평가에만 값이 있다
     */
    public record EvalResult(int evalId, String evalDiv, LocalDate evalDt,
                             String evlteEmpId, String evlteEmpNm, String evltrEmpId, String evltrNm,
                             String prjId, String prjNm,
                             BigDecimal rawScore, BigDecimal aplyAdjVal, Integer rndYr, LocalDate cnfmDt,
                             BigDecimal adjScore) {
        public boolean isCustomer() {
            return "고객".equals(evalDiv);
        }

        /** 보정점수가 없을 때 보여 줄 말 */
        public String adjScoreNote() {
            if (adjScore != null) {
                return null;
            }
            return rndYr == null ? "회차 없음" : "확정 전";
        }
    }

    /** ③ 피평가자별 요약. adjAvg는 회차가 확정되지 않았으면 null */
    public record EvaluateeSummary(String evlteEmpId, String evlteEmpNm, int evalCnt,
                                   BigDecimal rawAvg, BigDecimal adjAvg) {
    }

    /** 평가자보정값 한 줄 (평가자 성향) */
    public record EvltrAdjVal(String evalDiv, String evltrEmpId, String evltrNm, int evalCnt, int evalPrjCnt,
                              BigDecimal evltrAvgScore, BigDecimal aplyAdjVal) {
    }
}

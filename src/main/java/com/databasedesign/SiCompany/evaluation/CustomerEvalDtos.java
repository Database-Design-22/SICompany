package com.databasedesign.SiCompany.evaluation;

import java.time.LocalDate;

/**
 * 고객평가 입력 화면에서 쓰는 DTO 모음.
 */
public final class CustomerEvalDtos {

    private CustomerEvalDtos() {
    }

    /** 내가 PM인 종료 프로젝트. reqCnt: 고객평가를 의뢰한 수, submitCnt: 그중 제출한 수 */
    public record PmProject(String prjId, String prjNm, LocalDate endDt, int clientId, String clientNm,
                            int memberCnt, int reqCnt, int submitCnt) {
    }

    /** 고객평가 대상(그 프로젝트 참여자) 한 줄. 의뢰 전이면 evalId가 null */
    public record CustTarget(String empId, String empNm, String roleId, String roleNm,
                             Integer evalId, String evalStat, LocalDate evalDt,
                             LocalDate reqDt, LocalDate collDt, String mgrNm, int scoreCnt) {
        public boolean isRequested() {
            return evalId != null;
        }

        public boolean isSubmitted() {
            return "제출".equals(evalStat);
        }
    }

    /** 발주처 담당자 */
    public record Manager(int mgrId, String mgrNm) {
    }

    /** 회수 처리용 고객평가 한 건 */
    public record CustEval(int evalId, String prjId, String evalStat, LocalDate reqDt, LocalDate collDt) {
    }
}

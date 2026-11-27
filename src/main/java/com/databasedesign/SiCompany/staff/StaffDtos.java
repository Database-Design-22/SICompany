package com.databasedesign.SiCompany.staff;

import java.time.LocalDate;

/**
 * 직원 관리(경영관리팀) 화면에서 쓰는 DTO 모음. 주민등록번호와 비밀번호는 담지 않는다.
 */
public final class StaffDtos {

    private StaffDtos() {
    }

    /** 목록 한 줄. 개인정보를 파기한 직원은 empNm이 null */
    public record StaffRow(String empId, String empNm, String empDiv, String emplStat, LocalDate hireDt,
                           LocalDate rtrDt, LocalDate dstrDt, String authNm, String grdNm, String deptNm, int openHistCnt) {
    }

    /** 직원 한 명 */
    public record StaffDetail(String empId, String empNm, String engNm, String lastEdu, String empDiv, String emplStat,
                              LocalDate hireDt, LocalDate rtrDt, LocalDate dstrDt, String authId, String authNm,
                              String grdId, String deptId, String deptNm) {
        public boolean isDev() {
            return "개발".equals(empDiv);
        }

        public boolean isActive() {
            return "재직".equals(emplStat);
        }
    }

    /** 퇴사 처리 때 함께 바뀌는 것: 열린 투입, 평가자로서 작성중인 평가, 작성한 평가메모, 접수된 교체 요청 */
    public record RetireImpact(int openHistCnt, int draftEvalCnt, int memoCnt, int openReqCnt, LocalDate lastHistDate) {
    }

    /** 평가자로서 작성중인 평가 (퇴사 처리 때 지운다) */
    public record DraftEval(int evalId, String evalDiv) {
    }

    public record Code(String id, String name) {
    }

    /** 퇴사 처리 결과 */
    public record RetireResult(int closedHistCnt, int deletedEvalCnt, int deletedMemoCnt) {
    }
}

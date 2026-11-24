package com.databasedesign.SiCompany.designation;

import java.time.LocalDate;

/**
 * 발주처 지정인력 화면에서 쓰는 DTO 모음.
 */
public final class DesignationDtos {

    private DesignationDtos() {
    }

    public record Client(int clientId, String clientNm) {
    }

    /** ② 지정인력 한 줄. replReqId가 있으면 교체 요청에서 온 제외 */
    public record Designation(int desigId, String desigDiv, String desigRsn, LocalDate desigDt, LocalDate rlsDt,
                              int clientId, String clientNm, String empId, String empNm,
                              Integer replReqId, String reqMgrNm, boolean valid) {
    }

    /** 지명 대상 개발직원 */
    public record DevEmployee(String empId, String empNm, String grdNm) {
    }

    /** 겹치는 지정 */
    public record Overlap(int desigId, String desigDiv, LocalDate desigDt, LocalDate rlsDt) {
    }

    /** 제외 등록용 교체 요청 */
    public record ReplRequestForExclusion(int replReqId, String procStat, LocalDate procDt, String reqRsn,
                                          int clientId, String clientNm, String mgrNm,
                                          String empId, String empNm, String prjId, String prjNm) {
        public boolean isAccepted() {
            return "수용".equals(procStat);
        }

        /** 사유 칸에 미리 채워 둘 문장 */
        public String defaultReason() {
            return "고객 인력교체 요청이 수용된 직원 (" + reqRsn + ")";
        }
    }
}

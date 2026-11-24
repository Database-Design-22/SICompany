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

    /** 인력 배정: 진행/지연 프로젝트 */
    public record StaffingProject(String prjId, String prjNm, String prjStat, LocalDate strtDt, LocalDate schdEndDt,
                                  int clientId, String clientNm) {
        public boolean isActive() {
            return "진행".equals(prjStat) || "지연".equals(prjStat);
        }
    }

    /**
     * 인력 배정 후보 한 명.
     * desigDiv: 그 발주처의 유효한 지정 ('지명', '제외', 없으면 null)
     * replReqId 등: 제외의 근거 교체 요청 (사전 제외면 null)
     * ptcpRoleId: 이 프로젝트 참여 기록이 있으면 기존 직무 (재투입)
     */
    public record Candidate(String empId, String empNm, String grdNm, String skills,
                            Integer desigId, String desigDiv, LocalDate desigDt, String desigRsn,
                            Integer replReqId, LocalDate reqDt, String reqRsn, String reqMgrNm,
                            String ptcpRoleId) {
        public boolean isNominated() {
            return "지명".equals(desigDiv);
        }

        public boolean isExcluded() {
            return "제외".equals(desigDiv);
        }

        /** 제외된 직원을 배정할 때 확인 창에 보여 줄 말 */
        public String exclusionWarning() {
            String basis = replReqId == null
                    ? "교체 요청 없이 지정 (" + desigRsn + ")"
                    : "교체 요청 " + replReqId + " (" + reqDt + ", 담당자 " + reqMgrNm + ", 사유: " + reqRsn + ")";
            return empNm + "은(는) 이 발주처의 제외 인력입니다.\n근거: " + basis + "\n그래도 배정할까요?";
        }
    }

    public record Role(String roleId, String roleNm) {
    }

    /** 배정 결과 */
    public record AssignResult(int prjHistId, String roleId, boolean ptcpCreated, boolean excluded) {
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

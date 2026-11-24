package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;

/**
 * 교체 요청 화면에서 쓰는 DTO 모음.
 */
public final class ReplacementDtos {

    private ReplacementDtos() {
    }

    /** ① 교체 요청 목록 한 줄. 후임이 없으면 succEmpId가 null, 제외 등록 전이면 exclDesigId가 null */
    public record ReplRequest(int replReqId, String procStat, LocalDate reqDt, LocalDate procDt, String reqRsn,
                              String clientNm, String mgrNm, String prjId, String prjNm,
                              String trgtEmpId, String trgtEmpNm, String trgtRoleId,
                              String succEmpId, String succEmpNm, Integer exclDesigId) {
    }

    /** 진행/지연 프로젝트 */
    public record ActiveProject(String prjId, String prjNm, String prjStat, int clientId, String clientNm) {
    }

    /** ② 교체 대상 후보: 열린 투입 이력 (PM 제외). openReqCnt가 있으면 이미 접수된 요청이 있다. */
    public record ReplaceableHist(int prjHistId, String empId, String empNm, String roleId, String roleNm,
                                  LocalDate strtDt, int openReqCnt) {
        public boolean hasOpenRequest() {
            return openReqCnt > 0;
        }
    }

    /** ② 발주처 담당자. prjMgr: 이 프로젝트의 담당자인지 */
    public record ClientManager(int mgrId, String mgrNm, boolean prjMgr) {
    }

    /** 요청 한 건 (수용 화면, 처리 검사용) */
    public record RequestDetail(int replReqId, String procStat, LocalDate reqDt, String reqRsn,
                                int clientId, String clientNm, String mgrNm,
                                int trgtHistId, String trgtEmpId, String trgtEmpNm, String trgtRoleId,
                                LocalDate trgtStrtDt, LocalDate trgtEndDt,
                                String prjId, String prjNm, String prjStat) {
        public boolean isReceived() {
            return "접수".equals(procStat);
        }
    }

    /**
     * 후임 후보. ptcp: 이 프로젝트에 참여 기록이 있으면 재투입.
     * desigDiv: 그 발주처의 유효한 지정 ('지명', '제외', 없으면 null), desigReqId 등: 제외의 근거 교체 요청
     */
    public record SuccessorCandidate(String empId, String empNm, String grdNm, LocalDate lastEndDt, boolean ptcp,
                                     String desigDiv, String desigRsn, Integer desigReqId,
                                     LocalDate desigReqDt, String desigReqMgrNm) {
        public boolean isNominated() {
            return "지명".equals(desigDiv);
        }

        public boolean isExcluded() {
            return "제외".equals(desigDiv);
        }

        /** 제외 인력을 후임으로 고를 때 확인 창에 보여 줄 말 */
        public String exclusionWarning() {
            String basis = desigReqId == null
                    ? "교체 요청 없이 지정 (" + desigRsn + ")"
                    : "교체 요청 " + desigReqId + " (" + desigReqDt + ", 담당자 " + desigReqMgrNm + ")";
            return empNm + "은(는) 이 발주처의 제외 인력입니다.\n근거: " + basis;
        }
    }

    /** 수용 결과. succHistId가 null이면 후임 없이 수용했다. succExcluded: 후임이 그 발주처의 제외 인력 */
    public record AcceptResult(Integer succHistId, String succEmpId, boolean ptcpCreated, boolean succExcluded) {
    }

    /** 등록할 때 검사하는 대상 이력 */
    public record TargetHist(int prjHistId, String empId, String prjId, LocalDate strtDt, LocalDate endDt,
                             String roleId, String prjStat, int clientId) {
    }
}

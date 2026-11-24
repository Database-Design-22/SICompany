package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;

/**
 * 교체 요청 화면에서 쓰는 DTO 모음.
 */
public final class ReplacementDtos {

    private ReplacementDtos() {
    }

    /** ① 교체 요청 목록 한 줄. 후임이 없으면 succEmpId가 null */
    public record ReplRequest(int replReqId, String procStat, LocalDate reqDt, LocalDate procDt, String reqRsn,
                              String clientNm, String mgrNm, String prjId, String prjNm,
                              String trgtEmpId, String trgtEmpNm, String trgtRoleId,
                              String succEmpId, String succEmpNm) {
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

    /** 후임 후보. ptcp: 이 프로젝트에 참여 기록이 있으면 재투입 */
    public record SuccessorCandidate(String empId, String empNm, String grdNm, LocalDate lastEndDt, boolean ptcp) {
    }

    /** 수용 결과. succHistId가 null이면 후임 없이 수용했다. */
    public record AcceptResult(Integer succHistId, String succEmpId, boolean ptcpCreated) {
    }

    /** 등록할 때 검사하는 대상 이력 */
    public record TargetHist(int prjHistId, String empId, String prjId, LocalDate strtDt, LocalDate endDt,
                             String roleId, String prjStat, int clientId) {
    }
}

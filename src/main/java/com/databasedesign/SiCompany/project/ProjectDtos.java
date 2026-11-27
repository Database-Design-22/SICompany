package com.databasedesign.SiCompany.project;

import java.time.LocalDate;

/**
 * 프로젝트 관리 화면에서 쓰는 DTO 모음.
 */
public final class ProjectDtos {

    private ProjectDtos() {
    }

    /** 프로젝트 한 줄 (목록, 상세 공통). PM이 없으면 pmEmpId가 null */
    public record ProjectRow(String prjId, String prjNm, LocalDate strtDt, LocalDate schdEndDt, LocalDate endDt,
                             String prjStat, String clientNm, String mgrNm,
                             String pmEmpId, String pmEmpNm, int memberCnt, int openHistCnt) {
        /** 종료·중단 처리를 할 수 있는 상태인지 */
        public boolean isActive() {
            return "진행".equals(prjStat) || "지연".equals(prjStat);
        }
    }

    /** 팀원의 투입 이력 한 줄. 열린 이력이면 endDt, wthdrRsnId가 null */
    public record MemberHist(int prjHistId, String empId, String empNm, String roleId, String roleNm,
                             LocalDate strtDt, LocalDate endDt, String wthdrRsnId, String wthdrRsnNm) {
    }

    /** 종료 때 만들 평가 한 건: 평가구분('PM' 또는 '동료'), 평가자, 피평가자 */
    public record EvalPair(String evalDiv, String evltrEmpId, String evlteEmpId) {
    }

    /** 이 프로젝트의 평가 수 (평가구분, 상태별) */
    public record EvalCount(String evalDiv, String evalStat, int cnt) {
    }

    /** 종료·중단 처리 결과 */
    public record CloseResult(LocalDate endDt, int closedHistCnt, int pmEvalCnt, int peerEvalCnt, int deletedMemoCnt) {
    }
}

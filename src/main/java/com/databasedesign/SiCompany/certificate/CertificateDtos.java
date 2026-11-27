package com.databasedesign.SiCompany.certificate;

import java.time.LocalDate;

/**
 * 경력증명서 신청·발급 화면에서 쓰는 DTO 모음.
 */
public final class CertificateDtos {

    private CertificateDtos() {
    }

    /** 신청할 수 있는지 판단할 직원 정보 */
    public record Applicant(String empId, String empNm, String empDiv, LocalDate hireDt, LocalDate rtrDt, String emplStat) {
        public boolean isDev() {
            return "개발".equals(empDiv);
        }
    }

    /** 수록할 수 있는 참여: 현재 재직기간(입사일자 이후)에 투입 이력이 있는 프로젝트 */
    public record Participation(String prjId, String prjNm, String clientNm, String roleNm,
                                LocalDate firstStrtDt, LocalDate lastEndDt, boolean ongoing) {
    }

    /** 증명서 목록 한 줄 (발급 전이면 issuDt, vrfcNo가 null) */
    public record CertRow(int certId, LocalDate aplcDt, String aplcMthd, String emplPrdStmtYn, String blngStmtYn,
                          String usg, LocalDate issuDt, String vrfcNo, String trgtEmpId, String trgtEmpNm,
                          String procEmpNm, int inclCnt) {
        public boolean isIssued() {
            return issuDt != null;
        }
    }

    /** 증명서 한 장 (발급 때 저장한 기재 값) */
    public record CertDetail(int certId, LocalDate aplcDt, String aplcMthd, String emplPrdStmtYn, String blngStmtYn,
                             String usg, LocalDate issuDt, String vrfcNo, String stmtEmpNm, String stmtEmplPrd,
                             String stmtBlng, String trgtEmpId, String procEmpNm) {
        public boolean isIssued() {
            return issuDt != null;
        }
    }

    /** 수록 이력 한 줄 (발급 전에는 기재 값이 비어 있다) */
    public record InclRow(String prjId, String prjNm, String clientNm, String roleNm, String asgnPrd) {
    }
}

package com.databasedesign.SiCompany.employee;

import java.time.LocalDate;

/**
 * 직원 검색, 특정 시점 참여 조회, 내 정보 화면에서 쓰는 DTO 모음. 주민등록번호와 비밀번호는 담지 않는다.
 */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** 검색 결과 한 줄. years: 입사 몇 년차 (퇴사자는 퇴사일 기준) */
    public record EmployeeRow(String empId, String empNm, String empDiv, LocalDate hireDt, String emplStat,
                              String authNm, int years, String grdNm, String deptNm, String skills, String curPrjIds) {
    }

    /** 직원 한 명의 정보 */
    public record EmployeeDetail(String empId, String empNm, String engNm, String loginId, LocalDate hireDt,
                                 String empDiv, String lastEdu, String telno, String addr, String emplStat,
                                 LocalDate rtrDt, String authNm, int years, String grdNm, String deptNm) {
        public boolean isDev() {
            return "개발".equals(empDiv);
        }
    }

    public record Skill(int skillId, String skillNm) {
    }

    public record Grade(String grdId, String grdNm) {
    }

    /** 투입 이력(경력) 한 줄. 철수사유는 회사 안에서만 쓰므로 본인 경력에는 보여 주지 않는다 */
    public record CareerRow(int prjHistId, String prjId, String prjNm, String clientNm, String roleNm,
                            LocalDate strtDt, LocalDate endDt, String wthdrRsnNm, String prjStat) {
    }

    /** 특정 시점에 투입 중이던 직원 한 줄 */
    public record AsOfRow(String prjId, String prjNm, String clientNm, String empId, String empNm, String roleNm,
                          LocalDate strtDt, LocalDate endDt) {
    }

    /** 검색 조건 (빈 값은 null) */
    public record SearchCond(String keyword, String empDiv, String grdId, Integer skillId, String emplStat) {
    }
}

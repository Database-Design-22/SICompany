package com.databasedesign.SiCompany.signup;

import java.util.List;

/**
 * 합격자 명부와 회원가입 화면에서 쓰는 DTO 모음. 주민등록번호는 해시로만 다루고 화면에 꺼내지 않는다.
 */
public final class SignupDtos {

    private SignupDtos() {
    }

    public record Passer(int passerId, String passerNm, String jobgrp, String deptId, String deptNm) {
    }

    public record Dept(String deptId, String deptNm) {
    }

    public record Skill(int skillId, String skillNm) {
    }

    /** 회원가입 대조에서 찾은 합격자 */
    public record MatchedPasser(int passerId, String deptId) {
    }

    /** 회원가입 입력 */
    public record SignupInput(String empNm, String engNm, String rrn, String lastEdu, String loginId, String password,
                              String telno, String addr, String hireDt, String empDiv, List<Integer> skillIds) {
    }
}

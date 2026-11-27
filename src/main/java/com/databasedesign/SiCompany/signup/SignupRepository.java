package com.databasedesign.SiCompany.signup;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.databasedesign.SiCompany.signup.SignupDtos.Dept;
import com.databasedesign.SiCompany.signup.SignupDtos.MatchedPasser;
import com.databasedesign.SiCompany.signup.SignupDtos.Passer;
import com.databasedesign.SiCompany.signup.SignupDtos.Skill;

import lombok.RequiredArgsConstructor;

/**
 * 합격자 명부와 회원가입 SQL.
 * 주민등록번호는 'ENC$' + SHA-256 해시, 비밀번호는 'HASH$' + SHA-256 해시로 저장하고 비교한다 (평문을 저장하지 않는다).
 */
@Repository
@RequiredArgsConstructor
public class SignupRepository {

    /** 1. 합격자 명부 (아직 가입하지 않은 사람) */
    public static final String FIND_PASSERS = """
            SELECT p.PASSER_ID, p.PASSER_NM, p.JOBGRP, p.DEPT_ID, d.DEPT_NM
            FROM TB_PASSER p
            LEFT JOIN TB_DEPT d ON d.DEPT_ID = p.DEPT_ID
            ORDER BY p.PASSER_ID
            """;

    /** 2. 부서 목록 (비개발 합격자의 배정 부서) */
    public static final String FIND_DEPTS = """
            SELECT DEPT_ID, DEPT_NM
            FROM TB_DEPT
            ORDER BY DEPT_NM
            """;

    /** 합격자 등록 ①: 같은 주민등록번호가 명부에 있는지 */
    public static final String COUNT_PASSER_BY_RRN = """
            SELECT COUNT(*)
            FROM TB_PASSER
            WHERE RRN = CONCAT('ENC$', SHA2(?, 256))
            """;

    /** 합격자 등록 ② · 회원가입 ①: 같은 주민등록번호의 직원이 있는지 (이미 가입했거나 재입사 대상) */
    public static final String COUNT_EMP_BY_RRN = """
            SELECT COUNT(*)
            FROM TB_EMP
            WHERE RRN = CONCAT('ENC$', SHA2(?, 256))
            """;

    /** 합격자 등록 ③ */
    public static final String INSERT_PASSER = """
            INSERT INTO TB_PASSER (PASSER_NM, RRN, JOBGRP, DEPT_ID)
            VALUES (?, CONCAT('ENC$', SHA2(?, 256)), ?, ?)
            """;

    /** 미가입 합격자 삭제 · 회원가입 ⑦: 가입하면 합격자 행을 지운다 */
    public static final String DELETE_PASSER = """
            DELETE FROM TB_PASSER
            WHERE PASSER_ID = ?
            """;

    /** 로그인ID 중복 확인 */
    public static final String COUNT_LOGIN_ID = """
            SELECT COUNT(*)
            FROM TB_EMP
            WHERE LOGIN_ID = ?
            """;

    /** 기술 목록 (개발직원은 가입할 때 경험한 기술을 함께 입력한다) */
    public static final String FIND_SKILLS = """
            SELECT SKILL_ID, SKILL_NM
            FROM TB_SKILL
            ORDER BY SKILL_NM
            """;

    /** 회원가입 ②: 직원명, 주민등록번호, 직군을 합격자 명부와 대조 (찾은 행을 잠근다) */
    public static final String FIND_MATCHING_PASSER = """
            SELECT PASSER_ID, DEPT_ID
            FROM TB_PASSER
            WHERE PASSER_NM = ?
              AND RRN = CONCAT('ENC$', SHA2(?, 256))
              AND JOBGRP = ?
            FOR UPDATE
            """;

    /** 회원가입 ③: 직원ID 'YY-NNNN'의 다음 일련번호 (YY = 입사연도). 같은 연도 행을 잠근다 */
    public static final String NEXT_EMP_SEQ = """
            SELECT COALESCE(MAX(CAST(SUBSTRING(EMP_ID, 4) AS UNSIGNED)), 0) + 1
            FROM TB_EMP
            WHERE EMP_ID LIKE ?
            FOR UPDATE
            """;

    /** 회원가입 ④: 직원 (슈퍼타입). 재직, 권한 일반직원 */
    public static final String INSERT_EMP = """
            INSERT INTO TB_EMP
                   (EMP_ID, RRN, EMP_NM, LOGIN_ID, PWD, HIRE_DT, EMP_DIV, ENG_NM, LAST_EDU,
                    TELNO, ADDR, EMPL_STAT, AUTH_ID)
            VALUES (?, CONCAT('ENC$', SHA2(?, 256)), ?, ?, CONCAT('HASH$', SHA2(?, 256)), ?, ?, ?, ?,
                    ?, ?, '재직', 'EMP')
            """;

    /** 회원가입 ⑤: 개발직원 (서브타입, 기술등급은 나중에 경영관리팀이 부여) */
    public static final String INSERT_DEV_EMP = """
            INSERT INTO TB_DEV_EMP (EMP_ID, GRD_ID)
            VALUES (?, NULL)
            """;

    /** 회원가입 ⑤: 비개발직원 (서브타입, 부서는 합격자의 배정 부서) */
    public static final String INSERT_NDEV_EMP = """
            INSERT INTO TB_NDEV_EMP (EMP_ID, DEPT_ID)
            VALUES (?, ?)
            """;

    /** 회원가입 ⑥: 보유기술 (개발직원) */
    public static final String INSERT_HOLD_SKILL = """
            INSERT INTO TB_HOLD_SKILL (SKILL_ID, EMP_ID)
            VALUES (?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public List<Passer> findPassers() {
        return jdbcTemplate.query(FIND_PASSERS, (rs, i) -> new Passer(
                rs.getInt("PASSER_ID"),
                rs.getString("PASSER_NM"),
                rs.getString("JOBGRP"),
                rs.getString("DEPT_ID"),
                rs.getString("DEPT_NM")));
    }

    public List<Dept> findDepts() {
        return jdbcTemplate.query(FIND_DEPTS, (rs, i) -> new Dept(rs.getString("DEPT_ID"), rs.getString("DEPT_NM")));
    }

    public boolean existsPasserByRrn(String rrn) {
        return count(COUNT_PASSER_BY_RRN, rrn) > 0;
    }

    public boolean existsEmpByRrn(String rrn) {
        return count(COUNT_EMP_BY_RRN, rrn) > 0;
    }

    public void insertPasser(String passerNm, String rrn, String jobgrp, String deptId) {
        jdbcTemplate.update(INSERT_PASSER, passerNm, rrn, jobgrp, deptId);
    }

    public int deletePasser(int passerId) {
        return jdbcTemplate.update(DELETE_PASSER, passerId);
    }

    public boolean existsLoginId(String loginId) {
        return count(COUNT_LOGIN_ID, loginId) > 0;
    }

    public List<Skill> findSkills() {
        return jdbcTemplate.query(FIND_SKILLS, (rs, i) -> new Skill(rs.getInt("SKILL_ID"), rs.getString("SKILL_NM")));
    }

    public Optional<MatchedPasser> findMatchingPasser(String passerNm, String rrn, String jobgrp) {
        return jdbcTemplate.query(FIND_MATCHING_PASSER, (rs, i) -> new MatchedPasser(
                rs.getInt("PASSER_ID"), rs.getString("DEPT_ID")), passerNm, rrn, jobgrp).stream().findFirst();
    }

    /** @param prefix 예: '26-' */
    public int nextEmpSeq(String prefix) {
        Integer seq = jdbcTemplate.queryForObject(NEXT_EMP_SEQ, Integer.class, prefix + "%");
        return seq == null ? 1 : seq;
    }

    public void insertEmp(String empId, String rrn, String empNm, String loginId, String password, LocalDate hireDt,
                          String empDiv, String engNm, String lastEdu, String telno, String addr) {
        jdbcTemplate.update(INSERT_EMP, empId, rrn, empNm, loginId, password, hireDt, empDiv, engNm, lastEdu, telno, addr);
    }

    public void insertDevEmp(String empId) {
        jdbcTemplate.update(INSERT_DEV_EMP, empId);
    }

    public void insertNdevEmp(String empId, String deptId) {
        jdbcTemplate.update(INSERT_NDEV_EMP, empId, deptId);
    }

    public void insertHoldSkill(int skillId, String empId) {
        jdbcTemplate.update(INSERT_HOLD_SKILL, skillId, empId);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }
}

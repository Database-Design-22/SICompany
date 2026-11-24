package com.databasedesign.SiCompany.common;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CurrentUserRepository {

    /** 현재 사용자 드롭다운: 재직 직원 목록 (경영진 → 경영관리팀 → 일반직원 순) */
    public static final String FIND_ACTIVE_EMPLOYEES = """
            SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.AUTH_ID, a.AUTH_NM
            FROM TB_EMP e
            JOIN TB_AUTH a ON a.AUTH_ID = e.AUTH_ID
            WHERE e.EMPL_STAT = '재직'
            ORDER BY e.AUTH_ID DESC, e.EMP_NM
            """;

    /** 세션의 직원ID로 현재 사용자 한 명 조회 (재직자만) */
    public static final String FIND_ACTIVE_EMPLOYEE_BY_ID = """
            SELECT e.EMP_ID, e.EMP_NM, e.EMP_DIV, e.AUTH_ID, a.AUTH_NM
            FROM TB_EMP e
            JOIN TB_AUTH a ON a.AUTH_ID = e.AUTH_ID
            WHERE e.EMP_ID = ?
              AND e.EMPL_STAT = '재직'
            """;

    private static final RowMapper<CurrentUser> CURRENT_USER_MAPPER = (rs, rowNum) -> new CurrentUser(
            rs.getString("EMP_ID"),
            rs.getString("EMP_NM"),
            rs.getString("EMP_DIV"),
            rs.getString("AUTH_ID"),
            rs.getString("AUTH_NM"));

    private final JdbcTemplate jdbcTemplate;

    public List<CurrentUser> findActiveEmployees() {
        return jdbcTemplate.query(FIND_ACTIVE_EMPLOYEES, CURRENT_USER_MAPPER);
    }

    public Optional<CurrentUser> findActiveEmployeeById(String empId) {
        return jdbcTemplate.query(FIND_ACTIVE_EMPLOYEE_BY_ID, CURRENT_USER_MAPPER, empId)
                .stream()
                .findFirst();
    }
}

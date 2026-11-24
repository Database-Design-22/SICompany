package com.databasedesign.SiCompany.common;

/**
 * 화면 아래쪽 "실행한 SQL" 영역에 보여 줄 SQL 한 건.
 * sql에는 Repository의 상수를 그대로 넣어 실제 실행한 SQL과 같게 한다.
 * 컨트롤러에서 {@code model.addAttribute("executedSqls", List.of(new ExecutedSql("제목", XxxRepository.SQL)))}
 */
public record ExecutedSql(String title, String sql) {
}

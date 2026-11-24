package com.databasedesign.SiCompany.common;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 화면 위쪽에서 고른 현재 사용자.
 * 컨트롤러 메서드 파라미터에 {@code CurrentUser user}를 적으면 세션의 직원ID로 조회한 값이 들어온다.
 */
public record CurrentUser(String empId, String empNm, String authId, String authNm) {

    public static final String EMP = "EMP";
    public static final String MGT = "MGT";
    public static final String EXE = "EXE";

    public boolean isExe() {
        return EXE.equals(authId);
    }

    public boolean isMgt() {
        return MGT.equals(authId);
    }

    /** 권한이 하나도 맞지 않으면 예외를 던진다. 예: {@code user.require(CurrentUser.EXE)} */
    public void require(String... allowedAuthIds) {
        if (Arrays.asList(allowedAuthIds).contains(authId)) {
            return;
        }
        String allowed = Arrays.stream(allowedAuthIds)
                .map(CurrentUser::authName)
                .collect(Collectors.joining(", "));
        throw new BusinessException(
                "이 화면은 " + allowed + " 권한만 사용할 수 있습니다. (현재 사용자: " + empNm + ", " + authNm + ")");
    }

    public static String authName(String authId) {
        return switch (authId) {
            case EMP -> "일반직원";
            case MGT -> "경영관리팀";
            case EXE -> "경영진";
            default -> authId;
        };
    }
}

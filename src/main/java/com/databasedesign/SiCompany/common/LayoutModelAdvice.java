package com.databasedesign.SiCompany.common;

import java.util.Map;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 모든 화면의 공통 헤더에 필요한 값을 모델에 넣는다.
 * - selectableUsers : 현재 사용자 드롭다운 목록
 * - currentUser     : 고른 사용자 (없으면 null)
 * - currentPath     : 사용자를 바꾼 뒤 돌아올 경로
 */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutModelAdvice {

    private final CurrentUserService currentUserService;

    @ModelAttribute
    public void addLayoutAttributes(Model model, HttpServletRequest request) {
        fill(model.asMap(), request);
    }

    /** 오류 화면처럼 @ModelAttribute가 돌지 않는 곳에서도 쓴다. */
    public void fill(Map<String, Object> model, HttpServletRequest request) {
        model.put("selectableUsers", currentUserService.findSelectableUsers());
        model.put("currentUser", currentUserService.findCurrentUser(request.getSession(false)).orElse(null));
        model.put("currentPath", request.getRequestURI());
    }
}

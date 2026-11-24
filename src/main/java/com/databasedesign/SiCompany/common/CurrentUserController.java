package com.databasedesign.SiCompany.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CurrentUserController {

    private final CurrentUserService currentUserService;

    /** 헤더 드롭다운에서 사용자를 고르면 세션에 직원ID를 저장하고 보던 화면으로 돌아간다. */
    @PostMapping("/current-user")
    public String select(@RequestParam(required = false) String empId,
                         @RequestParam(required = false) String returnUrl,
                         HttpSession session) {
        if (empId == null || empId.isBlank()) {
            currentUserService.clear(session);
        } else {
            currentUserService.select(empId, session);
        }
        return "redirect:" + safeReturnUrl(returnUrl);
    }

    /** 이 앱 안의 경로로만 돌아간다. */
    private String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || !returnUrl.startsWith("/") || returnUrl.startsWith("//")) {
            return "/";
        }
        return returnUrl;
    }
}

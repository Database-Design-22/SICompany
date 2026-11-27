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

    /**
     * 사용자를 고르면 세션에 직원ID를 저장하고, 보던 화면이 속한 메뉴로 이동한다.
     * 새 사용자가 볼 수 없는 메뉴이면 홈으로 간다.
     * (상세 화면은 다른 사람의 것일 수 있으므로 그 메뉴의 첫 화면으로 돌아간다)
     */
    @PostMapping("/current-user")
    public String select(@RequestParam(required = false) String empId,
                         @RequestParam(required = false) String returnUrl,
                         HttpSession session) {
        if (empId == null || empId.isBlank()) {
            currentUserService.clear(session);
            return "redirect:/";
        }
        CurrentUser user = currentUserService.select(empId, session);
        if (!isSafe(returnUrl)) {
            return "redirect:/";
        }
        return Menus.findItemContaining(returnUrl)
                .flatMap(item -> Menus.findGroupByPath(item.path())
                        .filter(g -> g.visibleTo(user))
                        .map(g -> item.path()))
                .map(path -> "redirect:" + path)
                .orElse("redirect:/");
    }

    /** 이 앱 안의 경로만 받는다. */
    private boolean isSafe(String url) {
        return url != null && url.startsWith("/") && !url.startsWith("//") && !url.contains("\\");
    }
}

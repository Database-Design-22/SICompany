package com.databasedesign.SiCompany.common;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 아직 만들지 않은 메뉴의 "준비 중" 화면.
 * 메뉴의 대상(개발직원, 경영관리팀, 경영진)이 아니면 오류 화면으로 보낸다.
 * 기능을 만들면 아래 목록에서 그 주소를 지운다.
 */
@Controller
@RequiredArgsConstructor
public class PlaceholderController {

    private final CurrentUserService currentUserService;

    @GetMapping({
            // 공통
            "/me", "/certificates",
            // 내 업무
            "/evaluations/customer",
            // 인력 관리
            "/replacements", "/designations", "/staffing", "/projects", "/certificates/issue",
            // 경영 현황
            "/dashboard", "/employees", "/adjustments/run", "/incentives",
            // 로그인 전
            "/signup"
    })
    public String placeholder(HttpServletRequest request, Model model) {
        String path = request.getRequestURI();
        if (path.equals("/signup")) {
            model.addAttribute("groupTitle", "로그인 전");
            model.addAttribute("item", new Menus.MenuItem("회원가입", "/signup",
                    "합격자 명부와 대조해 가입하고, 사번을 자동으로 받는다. 로그인ID 중복을 확인한다."));
            return "common/placeholder";
        }
        Menus.MenuGroup group = Menus.findGroupByPath(path).orElseThrow();
        group.audience().check(currentUserService.getCurrentUser(request.getSession(false)));
        model.addAttribute("groupTitle", group.title());
        model.addAttribute("item", Menus.findItemByPath(path).orElseThrow());
        return "common/placeholder";
    }
}

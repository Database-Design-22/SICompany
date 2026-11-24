package com.databasedesign.SiCompany.common;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("현재 사용자 드롭다운: 재직 직원 목록", CurrentUserRepository.FIND_ACTIVE_EMPLOYEES),
                new ExecutedSql("현재 사용자 조회 (세션의 직원ID)", CurrentUserRepository.FIND_ACTIVE_EMPLOYEE_BY_ID)));
        return "home";
    }
}

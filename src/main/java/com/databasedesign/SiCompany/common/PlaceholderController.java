package com.databasedesign.SiCompany.common;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 아직 만들지 않은 메뉴의 임시 화면.
 * 기능 폴더에서 같은 주소의 컨트롤러를 만들면 여기서 그 매핑을 지운다.
 */
@Controller
public class PlaceholderController {

    // 시나리오 1
    @GetMapping("/memos")
    public String memos(Model model) {
        return placeholder(model, "시나리오 1", "평가메모");
    }

    @GetMapping("/evaluations")
    public String evaluations(Model model) {
        return placeholder(model, "시나리오 1", "평가 작성과 제출");
    }

    @GetMapping("/adjustments")
    public String adjustments(CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        return placeholder(model, "시나리오 1", "보정점수 조회");
    }

    // 시나리오 2
    @GetMapping("/replacements")
    public String replacements(CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        return placeholder(model, "시나리오 2", "교체 요청과 처리");
    }

    @GetMapping("/designations")
    public String designations(CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        return placeholder(model, "시나리오 2", "발주처 지정인력");
    }

    @GetMapping("/designations/candidates")
    public String candidates(CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        return placeholder(model, "시나리오 2", "배정 후보 목록");
    }

    private String placeholder(Model model, String scenario, String title) {
        model.addAttribute("scenario", scenario);
        model.addAttribute("title", title);
        return "common/placeholder";
    }
}

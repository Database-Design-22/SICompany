package com.databasedesign.SiCompany.signup;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.CurrentUserService;
import com.databasedesign.SiCompany.common.ExecutedSql;
import com.databasedesign.SiCompany.signup.SignupDtos.SignupInput;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * 합격자 명부(경영관리팀)와 회원가입(로그인 전)
 */
@Controller
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final CurrentUserService currentUserService;

    /** 회원가입 화면 (현재 사용자를 고르지 않아도 연다) */
    @GetMapping("/signup")
    public String signupPage(Model model) {
        model.addAttribute("educations", SignupService.EDUCATIONS);
        model.addAttribute("skills", signupService.findSkills());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("로그인ID 중복 확인", SignupRepository.COUNT_LOGIN_ID),
                new ExecutedSql("기술 목록", SignupRepository.FIND_SKILLS),
                new ExecutedSql("가입 ①: 이미 가입한 주민등록번호인지", SignupRepository.COUNT_EMP_BY_RRN),
                new ExecutedSql("가입 ②: 합격자 대조 (이름, 주민등록번호, 직군)", SignupRepository.FIND_MATCHING_PASSER),
                new ExecutedSql("가입 ③: 직원ID 일련번호 (입사연도별)", SignupRepository.NEXT_EMP_SEQ),
                new ExecutedSql("가입 ④: 직원 생성", SignupRepository.INSERT_EMP),
                new ExecutedSql("가입 ⑤: 개발직원 생성", SignupRepository.INSERT_DEV_EMP),
                new ExecutedSql("가입 ⑤: 비개발직원 생성", SignupRepository.INSERT_NDEV_EMP),
                new ExecutedSql("가입 ⑥: 보유기술 (개발직원)", SignupRepository.INSERT_HOLD_SKILL),
                new ExecutedSql("가입 ⑦: 합격자 행 삭제", SignupRepository.DELETE_PASSER)));
        return "signup/signup";
    }

    /** 로그인ID 중복 확인 (화면에서 바로 물어본다) */
    @GetMapping("/signup/check-login")
    @ResponseBody
    public String checkLogin(@RequestParam(required = false) String loginId) {
        String problem = signupService.checkLoginId(loginId);
        return problem == null ? "OK:사용할 수 있는 로그인ID입니다." : "NG:" + problem;
    }

    @PostMapping("/signup")
    public String signup(@RequestParam(required = false) String empNm,
                         @RequestParam(required = false) String engNm,
                         @RequestParam(required = false) String rrn,
                         @RequestParam(required = false) String lastEdu,
                         @RequestParam(required = false) String loginId,
                         @RequestParam(required = false) String password,
                         @RequestParam(required = false) String telno,
                         @RequestParam(required = false) String addr,
                         @RequestParam(required = false) String hireDt,
                         @RequestParam(required = false) String empDiv,
                         @RequestParam(required = false) List<Integer> skillIds,
                         HttpSession session, RedirectAttributes redirect) {
        String empId = signupService.signup(new SignupInput(empNm, engNm, rrn, lastEdu, loginId, password,
                telno, addr, hireDt, empDiv, skillIds));
        currentUserService.select(empId, session);
        redirect.addFlashAttribute("notice", "가입했습니다. 직원ID는 " + empId + "이고, 현재 사용자로 골랐습니다.");
        return "redirect:/me";
    }

    /** 합격자 명부 (경영관리팀) */
    @GetMapping("/passers")
    public String passers(CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("passers", signupService.findPassers());
        model.addAttribute("depts", signupService.findDepts());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("합격자 명부", SignupRepository.FIND_PASSERS),
                new ExecutedSql("부서 목록", SignupRepository.FIND_DEPTS),
                new ExecutedSql("등록 ①: 직원에 같은 주민등록번호가 있는지", SignupRepository.COUNT_EMP_BY_RRN),
                new ExecutedSql("등록 ②: 명부에 같은 주민등록번호가 있는지", SignupRepository.COUNT_PASSER_BY_RRN),
                new ExecutedSql("등록 ③: 합격자 등록 (주민등록번호는 해시)", SignupRepository.INSERT_PASSER),
                new ExecutedSql("미가입 합격자 삭제", SignupRepository.DELETE_PASSER)));
        return "signup/passers";
    }

    @PostMapping("/passers")
    public String registerPasser(@RequestParam(required = false) String passerNm,
                                 @RequestParam(required = false) String rrn,
                                 @RequestParam(required = false) String jobgrp,
                                 @RequestParam(required = false) String deptId,
                                 CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        signupService.registerPasser(passerNm, rrn, jobgrp, deptId);
        redirect.addFlashAttribute("notice", passerNm + "을(를) 합격자 명부에 등록했습니다. 이제 회원가입할 수 있습니다.");
        return "redirect:/passers";
    }

    @PostMapping("/passers/{passerId}/delete")
    public String deletePasser(@PathVariable int passerId, CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        signupService.deletePasser(passerId);
        redirect.addFlashAttribute("notice", "합격자 " + passerId + "번을 명부에서 지웠습니다.");
        return "redirect:/passers";
    }
}

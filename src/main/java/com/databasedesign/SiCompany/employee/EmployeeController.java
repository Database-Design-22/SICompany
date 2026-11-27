package com.databasedesign.SiCompany.employee;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;
import com.databasedesign.SiCompany.employee.EmployeeDtos.SearchCond;

import lombok.RequiredArgsConstructor;

/**
 * 직원 검색·특정 시점 참여 조회(경영진)와 내 정보(모든 직원)
 */
@Controller
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /** 직원 검색 */
    @GetMapping("/employees")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String div,
                         @RequestParam(required = false) String grd,
                         @RequestParam(required = false) Integer skill,
                         @RequestParam(required = false) String stat,
                         CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        SearchCond cond = new SearchCond(blank(q), blank(div), blank(grd), skill, stat == null ? "재직" : blank(stat));
        model.addAttribute("cond", cond);
        model.addAttribute("rows", employeeService.search(cond));
        model.addAttribute("skills", employeeService.findAllSkills());
        model.addAttribute("grades", employeeService.findGrades());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("직원 검색 (조건이 비면 보지 않음)", EmployeeRepository.SEARCH_EMPLOYEES),
                new ExecutedSql("검색 조건: 기술 목록", EmployeeRepository.FIND_ALL_SKILLS),
                new ExecutedSql("검색 조건: 기술등급 목록", EmployeeRepository.FIND_GRADES)));
        return "employee/search";
    }

    /** 직원 한 명: 정보, 보유기술, 투입 이력 */
    @GetMapping("/employees/{empId}")
    public String detail(@PathVariable String empId, CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        model.addAttribute("emp", employeeService.getEmployee(empId));
        model.addAttribute("skills", employeeService.findHoldSkills(empId));
        model.addAttribute("career", employeeService.findCareer(empId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("직원 한 명 (주민등록번호 제외)", EmployeeRepository.FIND_EMPLOYEE),
                new ExecutedSql("보유기술", EmployeeRepository.FIND_HOLD_SKILLS),
                new ExecutedSql("투입 이력", EmployeeRepository.FIND_CAREER)));
        return "employee/detail";
    }

    /** 특정 시점 참여 이력: 그 날짜에 누가 어느 프로젝트에 어떤 직무로 있었는지 */
    @GetMapping("/employees/as-of")
    public String asOf(@RequestParam(required = false) String date, CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        LocalDate asOf = (date == null || date.isBlank()) ? LocalDate.now() : employeeService.parseDate(date);
        model.addAttribute("date", asOf);
        model.addAttribute("rows", employeeService.findAsOf(asOf));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("그 날짜에 투입 중이던 직원", EmployeeRepository.FIND_AS_OF)));
        return "employee/as-of";
    }

    /** 내 정보 */
    @GetMapping("/me")
    public String me(CurrentUser user, Model model) {
        model.addAttribute("emp", employeeService.getEmployee(user.empId()));
        model.addAttribute("skills", employeeService.findHoldSkills(user.empId()));
        model.addAttribute("career", employeeService.findCareer(user.empId()));
        model.addAttribute("allSkills", employeeService.findAllSkills());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("내 정보 (주민등록번호 제외)", EmployeeRepository.FIND_EMPLOYEE),
                new ExecutedSql("보유기술", EmployeeRepository.FIND_HOLD_SKILLS),
                new ExecutedSql("내 경력 (투입 이력)", EmployeeRepository.FIND_CAREER),
                new ExecutedSql("수정 ①: 전화번호·주소", EmployeeRepository.UPDATE_CONTACT),
                new ExecutedSql("수정 ②: 비밀번호 (해시로 저장)", EmployeeRepository.UPDATE_PASSWORD),
                new ExecutedSql("보유기술 등록 ①: 이미 있는지", EmployeeRepository.COUNT_HOLD_SKILL),
                new ExecutedSql("보유기술 등록 ②", EmployeeRepository.INSERT_HOLD_SKILL),
                new ExecutedSql("보유기술 삭제", EmployeeRepository.DELETE_HOLD_SKILL)));
        return "employee/me";
    }

    @PostMapping("/me/contact")
    public String updateContact(@RequestParam(required = false) String telno,
                                @RequestParam(required = false) String addr,
                                CurrentUser user, RedirectAttributes redirect) {
        employeeService.updateContact(user, telno, addr);
        redirect.addFlashAttribute("notice", "전화번호와 주소를 고쳤습니다.");
        return "redirect:/me";
    }

    @PostMapping("/me/password")
    public String updatePassword(@RequestParam(required = false) String password,
                                 @RequestParam(required = false) String confirm,
                                 CurrentUser user, RedirectAttributes redirect) {
        employeeService.updatePassword(user, password, confirm);
        redirect.addFlashAttribute("notice", "비밀번호를 바꿨습니다.");
        return "redirect:/me";
    }

    @PostMapping("/me/skills")
    public String addSkill(@RequestParam(required = false) Integer skillId, CurrentUser user, RedirectAttributes redirect) {
        String skillNm = employeeService.addSkill(user, skillId);
        redirect.addFlashAttribute("notice", "보유기술에 " + skillNm + "을(를) 추가했습니다.");
        return "redirect:/me";
    }

    @PostMapping("/me/skills/{skillId}/delete")
    public String deleteSkill(@PathVariable int skillId, CurrentUser user, RedirectAttributes redirect) {
        employeeService.deleteSkill(user, skillId);
        redirect.addFlashAttribute("notice", "보유기술에서 뺐습니다.");
        return "redirect:/me";
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}

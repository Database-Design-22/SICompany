package com.databasedesign.SiCompany.staff;

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
import com.databasedesign.SiCompany.staff.StaffDtos.RetireResult;
import com.databasedesign.SiCompany.staff.StaffDtos.StaffDetail;

import lombok.RequiredArgsConstructor;

/**
 * 직원 관리 (경영관리팀만): 직원 목록 → 직원 한 명(정보 수정, 기술등급, 부서, 퇴사 처리), 개인정보 파기 실행
 */
@Controller
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping("/staff")
    public String list(@RequestParam(required = false) String stat,
                       @RequestParam(required = false) String q,
                       CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        String emplStat = stat == null ? "재직" : (stat.isBlank() ? null : stat);
        String keyword = q == null || q.isBlank() ? null : q.strip();
        model.addAttribute("stat", emplStat == null ? "" : emplStat);
        model.addAttribute("q", keyword);
        model.addAttribute("rows", staffService.findStaff(emplStat, keyword));
        model.addAttribute("piiTargetCnt", staffService.countPiiTargets());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("직원 목록", StaffRepository.FIND_STAFF),
                new ExecutedSql("개인정보 파기 ①: 대상 수 (퇴사 3년 지남)", StaffRepository.COUNT_PII_TARGETS),
                new ExecutedSql("개인정보 파기 ②: 경력증명서 기재 값 비우기", StaffRepository.CLEAR_CERT_STMTS),
                new ExecutedSql("개인정보 파기 ③: 개인정보 비우기, 파기일자", StaffRepository.DESTROY_PII)));
        return "staff/list";
    }

    @GetMapping("/staff/{empId}")
    public String detail(@PathVariable String empId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        StaffDetail staff = staffService.getStaff(empId);
        model.addAttribute("staff", staff);
        model.addAttribute("impact", staffService.findRetireImpact(empId));
        model.addAttribute("grades", staffService.findGrades());
        model.addAttribute("depts", staffService.findDepts());
        model.addAttribute("educations", StaffService.EDUCATIONS);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("isSelf", empId.equals(user.empId()));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("직원 한 명", StaffRepository.FIND_STAFF_DETAIL),
                new ExecutedSql("퇴사 처리 ①: 함께 바뀌는 것 미리 세기", StaffRepository.FIND_RETIRE_IMPACT),
                new ExecutedSql("퇴사 처리 ②: 재직상태 '퇴사'", StaffRepository.RETIRE_EMP),
                new ExecutedSql("퇴사 처리 ③: 열린 투입 닫기 (RET)", StaffRepository.CLOSE_OPEN_HISTS),
                new ExecutedSql("퇴사 처리 ④: 평가자로서 작성중인 평가", StaffRepository.FIND_DRAFT_EVALS),
                new ExecutedSql("퇴사 처리 ⑤-1: 점수 삭제", StaffRepository.DELETE_EVAL_SCORES),
                new ExecutedSql("퇴사 처리 ⑤-2: 내용 삭제", StaffRepository.DELETE_EVAL_CONTENTS),
                new ExecutedSql("퇴사 처리 ⑤-3: PM평가 삭제", StaffRepository.DELETE_PM_EVAL),
                new ExecutedSql("퇴사 처리 ⑤-3: 동료평가 삭제", StaffRepository.DELETE_PEER_EVAL),
                new ExecutedSql("퇴사 처리 ⑤-4: 평가 삭제", StaffRepository.DELETE_EVAL),
                new ExecutedSql("퇴사 처리 ⑥: 그 직원이 쓴 평가메모 삭제", StaffRepository.DELETE_MEMOS_BY_WRITER),
                new ExecutedSql("직원 정보 수정", StaffRepository.UPDATE_BASIC),
                new ExecutedSql("기술등급 부여", StaffRepository.UPDATE_GRADE),
                new ExecutedSql("부서 변경", StaffRepository.UPDATE_DEPT)));
        return "staff/detail";
    }

    @PostMapping("/staff/{empId}/retire")
    public String retire(@PathVariable String empId, @RequestParam(required = false) String rtrDt,
                         CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        RetireResult r = staffService.retire(user, empId, rtrDt);
        redirect.addFlashAttribute("notice", empId + "을(를) " + rtrDt + " 날짜로 퇴사 처리했습니다. 열린 투입 "
                + r.closedHistCnt() + "건을 '퇴사'로 닫고, 작성중이던 평가 " + r.deletedEvalCnt() + "건과 평가메모 "
                + r.deletedMemoCnt() + "건을 지웠습니다.");
        return "redirect:/staff/" + empId;
    }

    @PostMapping("/staff/{empId}/basic")
    public String updateBasic(@PathVariable String empId,
                              @RequestParam(required = false) String empNm,
                              @RequestParam(required = false) String engNm,
                              @RequestParam(required = false) String lastEdu,
                              CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        staffService.updateBasic(empId, empNm, engNm, lastEdu);
        redirect.addFlashAttribute("notice", "직원 정보를 고쳤습니다.");
        return "redirect:/staff/" + empId;
    }

    @PostMapping("/staff/{empId}/grade")
    public String updateGrade(@PathVariable String empId, @RequestParam(required = false) String grdId,
                              CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        staffService.updateGrade(empId, grdId);
        redirect.addFlashAttribute("notice", "기술등급을 바꿨습니다.");
        return "redirect:/staff/" + empId;
    }

    @PostMapping("/staff/{empId}/dept")
    public String updateDept(@PathVariable String empId, @RequestParam(required = false) String deptId,
                             CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        staffService.updateDept(empId, deptId);
        redirect.addFlashAttribute("notice", "부서를 옮겼습니다.");
        return "redirect:/staff/" + empId;
    }

    @PostMapping("/staff/destroy-pii")
    public String destroyPii(CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        int count = staffService.destroyPii();
        redirect.addFlashAttribute("notice", "퇴사 3년이 지난 직원 " + count + "명의 개인정보를 파기했습니다.");
        return "redirect:/staff?stat=";
    }
}

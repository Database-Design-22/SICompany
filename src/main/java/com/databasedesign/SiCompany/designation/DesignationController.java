package com.databasedesign.SiCompany.designation;

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
import com.databasedesign.SiCompany.designation.DesignationDtos.ReplRequestForExclusion;

import lombok.RequiredArgsConstructor;

/**
 * 발주처 지정인력 (시나리오 2, 경영관리팀만)
 * 수용된 교체 요청 → 제외 등록 / 지정인력 목록, 지명 등록, 해제
 */
@Controller
@RequiredArgsConstructor
public class DesignationController {

    private final DesignationService designationService;

    /** 지정인력 목록과 지명 등록 */
    @GetMapping("/designations")
    public String list(@RequestParam(required = false) Integer clientId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("clients", designationService.findClients());
        model.addAttribute("clientId", clientId);
        model.addAttribute("designations", designationService.findDesignations(clientId));
        model.addAttribute("employees", designationService.findDevEmployees());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("지정인력 목록 (유효 여부 계산)", DesignationRepository.FIND_DESIGNATIONS),
                new ExecutedSql("발주처 목록", DesignationRepository.FIND_CLIENTS),
                new ExecutedSql("지명 대상: 재직 개발직원", DesignationRepository.FIND_DEV_EMPLOYEES),
                new ExecutedSql("지명할 때 ①: 유효 기간이 겹치는 지정", DesignationRepository.FIND_OVERLAPS),
                new ExecutedSql("지명할 때 ②: 지정 등록", DesignationRepository.INSERT_DESIGNATION),
                new ExecutedSql("해제할 때", DesignationRepository.RELEASE_DESIGNATION)));
        return "designation/list";
    }

    @PostMapping("/designations/nominate")
    public String nominate(@RequestParam(required = false) Integer clientId,
                           @RequestParam(required = false) String empId,
                           @RequestParam(required = false) String desigRsn,
                           CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        int desigId = designationService.registerNomination(clientId, empId, desigRsn);
        redirect.addFlashAttribute("notice", "지명 " + desigId + "번을 등록했습니다.");
        redirect.addFlashAttribute("newDesigId", desigId);
        return "redirect:/designations?clientId=" + clientId;
    }

    @PostMapping("/designations/{desigId}/release")
    public String release(@PathVariable int desigId,
                          @RequestParam(required = false) Integer clientId,
                          CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        designationService.release(desigId);
        redirect.addFlashAttribute("notice", "지정 " + desigId + "번을 오늘 날짜로 해제했습니다. 행은 남아 있습니다.");
        redirect.addFlashAttribute("newDesigId", desigId);
        return clientId == null ? "redirect:/designations" : "redirect:/designations?clientId=" + clientId;
    }

    /** 수용된 교체 요청에서 제외 등록 */
    @GetMapping("/designations/exclude")
    public String excludeForm(@RequestParam int replReqId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        ReplRequestForExclusion request = designationService.getRequest(replReqId);
        model.addAttribute("request", request);
        model.addAttribute("existingDesigId", designationService.findExclusionOf(replReqId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("교체 요청 한 건 (발주처, 대상 직원)", DesignationRepository.FIND_REQUEST_FOR_EXCLUSION),
                new ExecutedSql("이 요청으로 이미 등록한 제외", DesignationRepository.FIND_DESIG_BY_REQUEST),
                new ExecutedSql("등록할 때 ①: 유효 기간이 겹치는 지정", DesignationRepository.FIND_OVERLAPS),
                new ExecutedSql("등록할 때 ②: 지정 등록 (제외)", DesignationRepository.INSERT_DESIGNATION)));
        return "designation/exclude";
    }

    @PostMapping("/designations/exclude")
    public String exclude(@RequestParam int replReqId,
                          @RequestParam(required = false) String desigRsn,
                          CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        int desigId = designationService.registerExclusion(replReqId, desigRsn);
        ReplRequestForExclusion request = designationService.getRequest(replReqId);
        redirect.addFlashAttribute("notice", "교체 요청 " + replReqId + "번을 근거로 "
                + request.clientNm() + "에 " + request.empNm() + "을(를) 제외 " + desigId + "번으로 등록했습니다.");
        redirect.addFlashAttribute("newDesigId", desigId);
        return "redirect:/designations?clientId=" + request.clientId();
    }
}

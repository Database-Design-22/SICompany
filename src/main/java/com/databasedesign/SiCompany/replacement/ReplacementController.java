package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;

import lombok.RequiredArgsConstructor;

/**
 * 교체 요청 (시나리오 2, 경영관리팀만)
 * ① 교체 요청 목록 → ② 요청 등록 (발주처 담당자의 요청을 경영관리팀이 대신 등록)
 */
@Controller
@RequiredArgsConstructor
public class ReplacementController {

    private final ReplacementService replacementService;

    /** ① 교체 요청 목록 */
    @GetMapping("/replacements")
    public String list(@RequestParam(required = false) String stat, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("requests", replacementService.findRequests(stat));
        model.addAttribute("stats", ReplacementService.PROC_STATS);
        model.addAttribute("stat", stat == null ? "" : stat);
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("교체 요청 목록", ReplacementRepository.FIND_REQUESTS)));
        return "replacement/list";
    }

    /** ② 요청 등록: 프로젝트를 고르면 투입 중 인원과 발주처 담당자를 보여 준다. */
    @GetMapping("/replacements/new")
    public String newForm(@RequestParam(required = false) String prjId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("projects", replacementService.findActiveProjects());
        model.addAttribute("today", LocalDate.now());
        List<ExecutedSql> sqls = new ArrayList<>(List.of(
                new ExecutedSql("진행/지연 프로젝트", ReplacementRepository.FIND_ACTIVE_PROJECTS)));
        if (prjId != null && !prjId.isBlank()) {
            model.addAttribute("project", replacementService.getActiveProject(prjId));
            model.addAttribute("hists", replacementService.findReplaceableHists(prjId));
            model.addAttribute("managers", replacementService.findClientManagers(prjId));
            sqls.addAll(List.of(
                    new ExecutedSql("교체 대상 후보: 열린 투입 이력 (PM 제외)", ReplacementRepository.FIND_REPLACEABLE_HISTS),
                    new ExecutedSql("발주처 담당자", ReplacementRepository.FIND_CLIENT_MANAGERS),
                    new ExecutedSql("등록할 때 ①: 대상 이력 확인", ReplacementRepository.FIND_TARGET_HIST),
                    new ExecutedSql("등록할 때 ②: 담당자가 발주처 소속인지", ReplacementRepository.COUNT_CLIENT_MANAGER),
                    new ExecutedSql("등록할 때 ③: 이미 접수된 요청이 있는지", ReplacementRepository.FIND_OPEN_REQUEST_ID),
                    new ExecutedSql("등록할 때 ④: 교체 요청 등록", ReplacementRepository.INSERT_REQUEST)));
        }
        model.addAttribute("executedSqls", sqls);
        return "replacement/new";
    }

    @PostMapping("/replacements")
    public String register(@RequestParam(required = false) Integer prjHistId,
                           @RequestParam(required = false) Integer mgrId,
                           @RequestParam(required = false) String reqDt,
                           @RequestParam(required = false) String reqRsn,
                           CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        int replReqId = replacementService.register(prjHistId, mgrId, reqDt, reqRsn);
        redirect.addFlashAttribute("notice", "교체 요청 " + replReqId + "번을 '접수' 상태로 등록했습니다.");
        redirect.addFlashAttribute("newReqId", replReqId);
        return "redirect:/replacements";
    }
}

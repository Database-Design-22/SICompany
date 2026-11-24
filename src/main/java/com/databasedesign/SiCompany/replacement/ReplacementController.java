package com.databasedesign.SiCompany.replacement;

import java.time.LocalDate;
import java.util.ArrayList;
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
import com.databasedesign.SiCompany.replacement.ReplacementDtos.AcceptResult;
import com.databasedesign.SiCompany.replacement.ReplacementDtos.RequestDetail;

import lombok.RequiredArgsConstructor;

/**
 * 교체 요청 (시나리오 2, 경영관리팀만)
 * ① 교체 요청 목록 → ② 요청 등록 (발주처 담당자의 요청을 경영관리팀이 대신 등록)
 * → ③ 처리 (수용: 처리일자와 후임 선택 / 철회)
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
                new ExecutedSql("교체 요청 목록", ReplacementRepository.FIND_REQUESTS),
                new ExecutedSql("철회할 때", ReplacementRepository.WITHDRAW_REQUEST)));
        return "replacement/list";
    }

    /** 수용 화면: 처리일자와 후임 선택 */
    @GetMapping("/replacements/{replReqId}/accept")
    public String acceptForm(@PathVariable int replReqId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        RequestDetail request = replacementService.getRequest(replReqId);
        model.addAttribute("request", request);
        model.addAttribute("candidates", request.isReceived()
                ? replacementService.findSuccessorCandidates(request) : List.of());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("요청 한 건", ReplacementRepository.FIND_REQUEST_DETAIL),
                new ExecutedSql("후임 후보 (지금 투입 중이 아닌 재직 개발직원)", ReplacementRepository.FIND_SUCCESSOR_CANDIDATES),
                new ExecutedSql("수용 ①: 요청 상태 '수용', 처리일자", ReplacementRepository.ACCEPT_REQUEST),
                new ExecutedSql("수용 ②: 대상 이력 종료 (철수사유 CUST)", ReplacementRepository.CLOSE_TARGET_HIST),
                new ExecutedSql("수용 ③-1: 후임 참여가 있는지", ReplacementRepository.COUNT_PTCP),
                new ExecutedSql("수용 ③-2: 없으면 대상과 같은 직무로 참여 생성", ReplacementRepository.INSERT_PTCP),
                new ExecutedSql("수용 ④: 후임 이력 (처리일자 다음 날부터)", ReplacementRepository.INSERT_SUCCESSOR_HIST),
                new ExecutedSql("수용 ⑤: 후임이력ID 연결", ReplacementRepository.LINK_SUCCESSOR_HIST)));
        return "replacement/accept";
    }

    @PostMapping("/replacements/{replReqId}/accept")
    public String accept(@PathVariable int replReqId,
                         @RequestParam(required = false) String succEmpId,
                         CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        AcceptResult result = replacementService.accept(replReqId, succEmpId);
        String message = "교체 요청 " + replReqId + "번을 오늘 날짜로 수용했습니다. 대상 투입을 '고객 교체 요청'으로 종료했습니다.";
        if (result.succHistId() != null) {
            message += " 후임 " + result.succEmpId() + "의 투입 이력 " + result.succHistId() + "번을 열었습니다"
                    + (result.ptcpCreated() ? " (프로젝트참여 새로 생성)." : " (기존 프로젝트참여 사용, 재투입).");
        }
        redirect.addFlashAttribute("notice", message);
        redirect.addFlashAttribute("newReqId", replReqId);
        // 수용 다음 단계(발주처 제외 등록)로 바로 이어갈 수 있게 목록 위에 버튼을 띄운다.
        redirect.addFlashAttribute("excludeReqId", replReqId);
        return "redirect:/replacements";
    }

    @PostMapping("/replacements/{replReqId}/withdraw")
    public String withdraw(@PathVariable int replReqId, CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        replacementService.withdraw(replReqId);
        redirect.addFlashAttribute("notice", "교체 요청 " + replReqId + "번을 철회했습니다.");
        redirect.addFlashAttribute("newReqId", replReqId);
        return "redirect:/replacements";
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

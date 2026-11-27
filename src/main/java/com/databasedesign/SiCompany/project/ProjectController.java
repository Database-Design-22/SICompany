package com.databasedesign.SiCompany.project;

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
import com.databasedesign.SiCompany.project.ProjectDtos.CloseResult;

import lombok.RequiredArgsConstructor;

/**
 * 프로젝트 관리 (경영관리팀만)
 * 프로젝트 목록 → 상세(팀원·투입 이력, 평가 수) → 종료(평가 행 생성) / 중단(평가메모 삭제)
 */
@Controller
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/projects")
    public String list(@RequestParam(required = false) String stat, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        String prjStat = projectService.normalizeStat(stat);
        model.addAttribute("stats", ProjectService.STATS);
        model.addAttribute("stat", prjStat == null ? "" : prjStat);
        model.addAttribute("projects", projectService.findProjects(prjStat));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("프로젝트 목록 (PM, 팀원 수, 열린 투입 수)", ProjectRepository.FIND_PROJECTS)));
        return "project/list";
    }

    @GetMapping("/projects/{prjId}")
    public String detail(@PathVariable String prjId, CurrentUser user, Model model) {
        user.require(CurrentUser.MGT);
        model.addAttribute("project", projectService.getProject(prjId));
        model.addAttribute("hists", projectService.findMemberHists(prjId));
        model.addAttribute("evalCounts", projectService.findEvalCounts(prjId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("프로젝트 한 건", ProjectRepository.FIND_PROJECT),
                new ExecutedSql("팀원과 투입 이력", ProjectRepository.FIND_MEMBER_HISTS),
                new ExecutedSql("이 프로젝트의 평가 수", ProjectRepository.FIND_EVAL_COUNTS),
                new ExecutedSql("종료·중단 검사 ①: 마지막 투입 날짜", ProjectRepository.FIND_LAST_HIST_DATE),
                new ExecutedSql("종료·중단 검사 ②: 처리하지 않은 교체 요청", ProjectRepository.COUNT_OPEN_REPL_REQUESTS),
                new ExecutedSql("종료 ①: 프로젝트 '종료'", ProjectRepository.END_PROJECT),
                new ExecutedSql("종료 ②: 열린 투입 이력 닫기 (PLAN)", ProjectRepository.CLOSE_OPEN_HISTS),
                new ExecutedSql("종료 ③: 평가 대상 쌍 (PM평가, 동료평가)", ProjectRepository.FIND_EVAL_PAIRS),
                new ExecutedSql("종료 ④-1: 평가 생성 '작성중'", ProjectRepository.INSERT_EVAL),
                new ExecutedSql("종료 ④-2: PM평가 생성", ProjectRepository.INSERT_PM_EVAL),
                new ExecutedSql("종료 ④-2: 동료평가 생성", ProjectRepository.INSERT_PEER_EVAL),
                new ExecutedSql("중단 ①: 프로젝트 '중단'", ProjectRepository.STOP_PROJECT),
                new ExecutedSql("중단 ②: 열린 투입 이력 닫기 (STOP)", ProjectRepository.CLOSE_OPEN_HISTS),
                new ExecutedSql("중단 ③: 평가메모 삭제", ProjectRepository.DELETE_PROJECT_MEMOS)));
        return "project/detail";
    }

    @PostMapping("/projects/{prjId}/end")
    public String end(@PathVariable String prjId, CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        CloseResult r = projectService.end(prjId);
        redirect.addFlashAttribute("notice", prjId + "을(를) " + r.endDt() + " 날짜로 종료했습니다."
                + " 열린 투입 " + r.closedHistCnt() + "건을 '계획된 종료'로 닫고,"
                + " PM평가 " + r.pmEvalCnt() + "건, 동료평가 " + r.peerEvalCnt() + "건을 '작성중'으로 만들었습니다.");
        return "redirect:/projects/" + prjId;
    }

    @PostMapping("/projects/{prjId}/stop")
    public String stop(@PathVariable String prjId, CurrentUser user, RedirectAttributes redirect) {
        user.require(CurrentUser.MGT);
        CloseResult r = projectService.stop(prjId);
        redirect.addFlashAttribute("notice", prjId + "을(를) " + r.endDt() + " 날짜로 중단했습니다."
                + " 열린 투입 " + r.closedHistCnt() + "건을 '프로젝트 중단'으로 닫고,"
                + " 평가메모 " + r.deletedMemoCnt() + "건을 삭제했습니다. 평가는 만들지 않습니다.");
        return "redirect:/projects/" + prjId;
    }
}

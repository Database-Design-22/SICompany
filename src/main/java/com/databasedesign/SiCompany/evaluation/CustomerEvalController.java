package com.databasedesign.SiCompany.evaluation;

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
import com.databasedesign.SiCompany.evaluation.CustomerEvalDtos.PmProject;

import lombok.RequiredArgsConstructor;

/**
 * 고객평가 입력 (개발직원 중 종료 프로젝트의 PM)
 * 프로젝트 선택 → 피평가자별 의뢰 → 회수일자 → 평가 화면(/evaluations/{id})에서 입력·제출
 */
@Controller
@RequiredArgsConstructor
public class CustomerEvalController {

    private final CustomerEvalService customerEvalService;

    @GetMapping("/evaluations/customer")
    public String page(@RequestParam(required = false) String prjId, CurrentUser user, Model model) {
        user.requireDev();
        List<PmProject> projects = customerEvalService.findMyPmProjects(user);
        model.addAttribute("projects", projects);
        model.addAttribute("today", java.time.LocalDate.now());
        List<ExecutedSql> sqls = new ArrayList<>(List.of(
                new ExecutedSql("내가 PM인 종료 프로젝트", CustomerEvalRepository.FIND_MY_PM_PROJECTS)));

        String selected = (prjId == null || prjId.isBlank())
                ? (projects.isEmpty() ? null : projects.get(0).prjId())
                : prjId;
        if (selected != null) {
            PmProject project = customerEvalService.getMyPmProject(user, selected);
            model.addAttribute("project", project);
            model.addAttribute("targets", customerEvalService.findTargets(selected));
            model.addAttribute("managers", customerEvalService.findManagers(project.clientId()));
            sqls.addAll(List.of(
                    new ExecutedSql("고객평가 대상과 진행 상태", CustomerEvalRepository.FIND_TARGETS),
                    new ExecutedSql("발주처 담당자", CustomerEvalRepository.FIND_MANAGERS),
                    new ExecutedSql("의뢰 ①: 이미 의뢰했는지", CustomerEvalRepository.COUNT_CUST_EVAL),
                    new ExecutedSql("의뢰 ②: 평가 생성 (고객, 작성중)", CustomerEvalRepository.INSERT_EVAL),
                    new ExecutedSql("의뢰 ③: 고객평가 생성 (의뢰일자 오늘)", CustomerEvalRepository.INSERT_CUST_EVAL),
                    new ExecutedSql("회수 ①: 고객평가 한 건", CustomerEvalRepository.FIND_CUST_EVAL),
                    new ExecutedSql("회수 ②: 회수일자 기록", CustomerEvalRepository.UPDATE_COLL_DT)));
        }
        model.addAttribute("executedSqls", sqls);
        return "evaluation/customer";
    }

    @PostMapping("/evaluations/customer/request")
    public String request(@RequestParam String prjId,
                          @RequestParam(required = false) List<String> empIds,
                          @RequestParam(required = false) Integer mgrId,
                          CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        int count = customerEvalService.request(user, prjId, empIds, mgrId);
        redirect.addFlashAttribute("notice", "고객평가 " + count + "건을 의뢰했습니다. 평가서를 받으면 회수일자를 넣고 내용을 입력하세요.");
        return "redirect:/evaluations/customer?prjId=" + prjId;
    }

    @PostMapping("/evaluations/customer/{evalId}/collect")
    public String collect(@PathVariable int evalId,
                          @RequestParam(required = false) String collDt,
                          CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        String prjId = customerEvalService.collect(user, evalId, collDt);
        redirect.addFlashAttribute("notice", "평가 " + evalId + "번의 회수일자를 " + collDt + "로 기록했습니다.");
        return "redirect:/evaluations/customer?prjId=" + prjId;
    }
}

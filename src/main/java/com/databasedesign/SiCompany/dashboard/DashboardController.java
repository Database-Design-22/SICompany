package com.databasedesign.SiCompany.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;
import com.databasedesign.SiCompany.dashboard.DashboardDtos.CurrentMember;

import lombok.RequiredArgsConstructor;

/**
 * 경영 현황 (경영진만)
 * - 프로젝트 현황: 수행 중인 프로젝트 수, 프로젝트별 투입 인원, 직원별 직무와 투입 기간 (단위프로세스 34)
 * - 인센티브 자료: 개발직원별 참여·직무·투입 기간, 평가 원점수·보정점수, 교체 요청 이력 (단위프로세스 60)
 * 조회만 하므로 Service 없이 Repository를 바로 쓴다.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardRepository dashboardRepository;

    @GetMapping("/dashboard")
    public String dashboard(CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        Map<String, List<CurrentMember>> members = dashboardRepository.findCurrentMembers().stream()
                .collect(Collectors.groupingBy(CurrentMember::prjId));
        model.addAttribute("summary", dashboardRepository.findSummary());
        model.addAttribute("projects", dashboardRepository.findActiveProjects());
        model.addAttribute("members", members);
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("요약 (상태별 프로젝트 수, 개발직원 투입 현황)", DashboardRepository.FIND_SUMMARY),
                new ExecutedSql("수행 중인 프로젝트 (투입 인원, 일정 경과)", DashboardRepository.FIND_ACTIVE_PROJECTS),
                new ExecutedSql("지금 투입 중인 직원 (직무, 투입 기간)", DashboardRepository.FIND_CURRENT_MEMBERS)));
        return "dashboard/dashboard";
    }

    @GetMapping("/incentives")
    public String incentives(@RequestParam(required = false) Integer year, CurrentUser user, Model model) {
        user.require(CurrentUser.EXE);
        List<Integer> years = dashboardRepository.findIncentiveYears();
        int selectedYear = year != null ? year : LocalDate.now().getYear();
        model.addAttribute("years", years);
        model.addAttribute("year", selectedYear);
        model.addAttribute("roundExists", dashboardRepository.existsRound(selectedYear));
        model.addAttribute("cnfmDt", dashboardRepository.findRoundCnfmDt(selectedYear).orElse(null));
        model.addAttribute("rows", dashboardRepository.findIncentiveRows(selectedYear));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("인센티브 기초자료 (개발직원별, 하나의 SELECT)", DashboardRepository.FIND_INCENTIVE_ROWS),
                new ExecutedSql("그 연도 보정회차 확정일자", DashboardRepository.FIND_ROUND_CNFM_DT),
                new ExecutedSql("연도 선택 목록", DashboardRepository.FIND_INCENTIVE_YEARS)));
        return "dashboard/incentives";
    }
}

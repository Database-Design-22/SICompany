package com.databasedesign.SiCompany.memo;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.common.ExecutedSql;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoKey;

import lombok.RequiredArgsConstructor;

/**
 * 평가메모 (시나리오 1)
 * 내 프로젝트 → 팀원 조회 → 대상별 메모 작성·수정·삭제
 * 평가메모 메뉴: 새 메모 쓰기와 내가 쓴 메모 전체 목록
 */
@Controller
@RequiredArgsConstructor
public class MemoController {

    private final MemoService memoService;

    /** ① 내 프로젝트 */
    @GetMapping("/my/projects")
    public String myProjects(CurrentUser user, Model model) {
        user.requireDev();
        model.addAttribute("projects", memoService.findMyProjects(user));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("내가 참여한 진행/지연 프로젝트", MemoRepository.FIND_MY_ACTIVE_PROJECTS)));
        return "memo/my-projects";
    }

    /** ② 프로젝트 팀원 (메모를 쓸 수 있는 팀원에게만 [메모] 버튼) */
    @GetMapping("/my/projects/{prjId}")
    public String team(@PathVariable String prjId, CurrentUser user, Model model) {
        user.requireDev();
        model.addAttribute("participation", memoService.getParticipation(user, prjId));
        model.addAttribute("members", memoService.getTeam(user, prjId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("이 프로젝트에서 내 참여", MemoRepository.FIND_PARTICIPATION),
                new ExecutedSql("프로젝트 팀원 전체", MemoRepository.FIND_TEAM_MEMBERS),
                new ExecutedSql("메모 대상 (PM이면 팀원 전원, 아니면 투입 기간이 하루 이상 겹친 직원)",
                        MemoRepository.FIND_MEMO_TARGETS)));
        return "memo/team";
    }

    /** ③ 대상별 내 메모 */
    @GetMapping("/memos/{prjId}/{trgtEmpId}")
    public String memos(@PathVariable String prjId, @PathVariable String trgtEmpId,
                        CurrentUser user, Model model) {
        user.requireDev();
        model.addAttribute("page", memoService.getMemoPage(user, prjId, trgtEmpId));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("이 프로젝트에서 내 참여", MemoRepository.FIND_PARTICIPATION),
                new ExecutedSql("대상 확인 (메모 대상 목록에 있는지)", MemoRepository.FIND_MEMO_TARGETS),
                new ExecutedSql("제출된 평가가 있는지", MemoRepository.COUNT_SUBMITTED_EVAL),
                new ExecutedSql("이 대상에게 내가 쓴 메모", MemoRepository.FIND_MEMOS_BY_TARGET),
                new ExecutedSql("작성할 때", MemoRepository.INSERT_MEMO),
                new ExecutedSql("수정할 때 (먼저 본인 메모인지 조회)", MemoRepository.FIND_OWNED_MEMO),
                new ExecutedSql("수정할 때", MemoRepository.UPDATE_MEMO),
                new ExecutedSql("삭제할 때", MemoRepository.DELETE_MEMO)));
        return "memo/memos";
    }

    /** 평가메모 메뉴: 새 메모 쓰기와 내가 쓴 메모 전체 */
    @GetMapping("/memos")
    public String myMemos(CurrentUser user, Model model) {
        user.requireDev();
        model.addAttribute("targetGroups", memoService.findTargetGroups(user));
        model.addAttribute("memos", memoService.findMyMemos(user));
        model.addAttribute("executedSqls", List.of(
                new ExecutedSql("새 메모 대상 ①: 내가 참여한 진행/지연 프로젝트", MemoRepository.FIND_MY_ACTIVE_PROJECTS),
                new ExecutedSql("새 메모 대상 ②: 프로젝트마다 메모 대상", MemoRepository.FIND_MEMO_TARGETS),
                new ExecutedSql("내가 쓴 메모 전체", MemoRepository.FIND_MY_MEMOS),
                new ExecutedSql("저장할 때", MemoRepository.INSERT_MEMO)));
        return "memo/my-memos";
    }

    /**
     * 메모 작성. 평가메모 메뉴에서 쓰면 returnTo=memos로 그 화면에 돌아가고,
     * 대상별 메모 화면에서 쓰면 그 화면으로 돌아간다.
     */
    @PostMapping("/memos")
    public String create(@RequestParam(required = false) String prjId,
                         @RequestParam(required = false) String trgtEmpId,
                         @RequestParam(required = false) String returnTo,
                         @RequestParam(required = false) String memoCn,
                         CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        if (prjId == null || prjId.isBlank() || trgtEmpId == null || trgtEmpId.isBlank()) {
            throw new BusinessException("메모 대상을 고르세요.");
        }
        memoService.create(user, prjId, trgtEmpId, memoCn);
        redirect.addFlashAttribute("notice", "메모를 저장했습니다.");
        return "memos".equals(returnTo) ? "redirect:/memos" : redirectToMemos(prjId, trgtEmpId);
    }

    @PostMapping("/memos/{memoId}/edit")
    public String update(@PathVariable int memoId, @RequestParam(required = false) String memoCn,
                         CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        MemoKey key = memoService.update(user, memoId, memoCn);
        redirect.addFlashAttribute("notice", "메모를 수정했습니다.");
        return redirectToMemos(key.prjId(), key.trgtEmpId());
    }

    @PostMapping("/memos/{memoId}/delete")
    public String delete(@PathVariable int memoId, CurrentUser user, RedirectAttributes redirect) {
        user.requireDev();
        MemoKey key = memoService.delete(user, memoId);
        redirect.addFlashAttribute("notice", "메모를 삭제했습니다.");
        return redirectToMemos(key.prjId(), key.trgtEmpId());
    }

    private String redirectToMemos(String prjId, String trgtEmpId) {
        return "redirect:" + UriComponentsBuilder.fromPath("/memos/{prjId}/{trgtEmpId}")
                .buildAndExpand(prjId, trgtEmpId)
                .encode()
                .toUriString();
    }
}

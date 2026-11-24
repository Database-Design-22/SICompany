package com.databasedesign.SiCompany.memo;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.databasedesign.SiCompany.common.BusinessException;
import com.databasedesign.SiCompany.common.CurrentUser;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoKey;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoPage;
import com.databasedesign.SiCompany.memo.MemoDtos.MemoTarget;
import com.databasedesign.SiCompany.memo.MemoDtos.MyMemo;
import com.databasedesign.SiCompany.memo.MemoDtos.MyProject;
import com.databasedesign.SiCompany.memo.MemoDtos.Participation;
import com.databasedesign.SiCompany.memo.MemoDtos.TargetGroup;
import com.databasedesign.SiCompany.memo.MemoDtos.TeamMember;

import lombok.RequiredArgsConstructor;

/**
 * 평가메모 업무 규칙 (CLAUDE.md 시나리오 1)
 * - 진행 또는 지연 중인 프로젝트에서만 쓰고 고친다.
 * - 대상: PM은 팀원 전원, 아니면 투입 기간이 하루 이상 겹친 직원. 자기 자신은 안 된다.
 * - 작성자 본인만 조회, 수정, 삭제한다.
 * - 작성자가 평가자이고 대상자가 피평가자인 평가가 이미 '제출'이면 작성과 수정을 막는다.
 */
@Service
@RequiredArgsConstructor
public class MemoService {

    private static final int MAX_LENGTH = 1000;

    private final MemoRepository memoRepository;

    public List<MyProject> findMyProjects(CurrentUser user) {
        return memoRepository.findMyActiveProjects(user.empId());
    }

    public Participation getParticipation(CurrentUser user, String prjId) {
        return memoRepository.findParticipation(user.empId(), prjId)
                .orElseThrow(() -> new BusinessException("참여하지 않은 프로젝트입니다. (" + prjId + ")"));
    }

    public List<MemoTarget> findTargets(CurrentUser user, String prjId) {
        return memoRepository.findMemoTargets(user.empId(), prjId);
    }

    /** ② 팀원 전체. 참여자만 볼 수 있고, 메모 대상 SQL로 [메모] 버튼을 띄울지 정한다. */
    public List<TeamMember> getTeam(CurrentUser user, String prjId) {
        getParticipation(user, prjId);
        Map<String, MemoTarget> targets = findTargets(user, prjId).stream()
                .collect(Collectors.toMap(MemoTarget::empId, Function.identity()));
        return memoRepository.findTeamMembers(prjId).stream()
                .map(row -> {
                    MemoTarget target = targets.get(row.empId());
                    return new TeamMember(row, row.empId().equals(user.empId()),
                            target != null, target == null ? 0 : target.memoCnt());
                })
                .toList();
    }

    /** 평가메모 메뉴의 새 메모 대상: 내 진행/지연 프로젝트마다 메모 대상 SQL을 실행해 묶는다. */
    public List<TargetGroup> findTargetGroups(CurrentUser user) {
        return findMyProjects(user).stream()
                .map(p -> new TargetGroup(p, findTargets(user, p.prjId())))
                .filter(g -> !g.targets().isEmpty())
                .toList();
    }

    public MemoPage getMemoPage(CurrentUser user, String prjId, String trgtEmpId) {
        Participation participation = getParticipation(user, prjId);
        MemoTarget target = getAllowedTarget(user, prjId, trgtEmpId);
        return new MemoPage(
                participation,
                target,
                memoRepository.findMemosByTarget(user.empId(), trgtEmpId, prjId),
                blockedReason(user, participation, trgtEmpId));
    }

    public List<MyMemo> findMyMemos(CurrentUser user) {
        return memoRepository.findMyMemos(user.empId());
    }

    @Transactional
    public void create(CurrentUser user, String prjId, String trgtEmpId, String memoCn) {
        Participation participation = getParticipation(user, prjId);
        getAllowedTarget(user, prjId, trgtEmpId);
        throwIfBlocked(user, participation, trgtEmpId);
        memoRepository.insertMemo(validContent(memoCn), user.empId(), trgtEmpId, prjId);
    }

    /** @return 수정한 메모의 키 (화면을 되돌리는 데 쓴다) */
    @Transactional
    public MemoKey update(CurrentUser user, int memoId, String memoCn) {
        MemoKey key = getOwnedMemo(user, memoId);
        Participation participation = getParticipation(user, key.prjId());
        throwIfBlocked(user, participation, key.trgtEmpId());
        memoRepository.updateMemo(memoId, user.empId(), validContent(memoCn));
        return key;
    }

    /** 삭제는 프로젝트 상태나 평가 제출과 관계없이 본인 메모면 할 수 있다. */
    @Transactional
    public MemoKey delete(CurrentUser user, int memoId) {
        MemoKey key = getOwnedMemo(user, memoId);
        memoRepository.deleteMemo(memoId, user.empId());
        return key;
    }

    /** 화면에 보이는 대상 목록과 같은 SQL로 검사한다. */
    private MemoTarget getAllowedTarget(CurrentUser user, String prjId, String trgtEmpId) {
        if (user.empId().equals(trgtEmpId)) {
            throw new BusinessException("자기 자신에게는 메모를 쓸 수 없습니다.");
        }
        return findTargets(user, prjId).stream()
                .filter(t -> t.empId().equals(trgtEmpId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "이 직원에게는 메모를 쓸 수 없습니다. PM이 아니면 같은 프로젝트에서 투입 기간이 하루 이상 겹친 직원에게만 쓸 수 있습니다."));
    }

    private MemoKey getOwnedMemo(CurrentUser user, int memoId) {
        return memoRepository.findOwnedMemo(memoId, user.empId())
                .orElseThrow(() -> new BusinessException("본인이 쓴 메모만 고치거나 지울 수 있습니다."));
    }

    /** 작성과 수정을 막는 이유. 없으면 null. */
    private String blockedReason(CurrentUser user, Participation participation, String trgtEmpId) {
        if (!participation.isActive()) {
            return "진행 또는 지연 중인 프로젝트에만 메모를 쓰거나 고칠 수 있습니다. (현재 상태: " + participation.prjStat() + ")";
        }
        if (memoRepository.countSubmittedEval(participation.prjId(), trgtEmpId, user.empId()) > 0) {
            return "이미 평가를 제출한 대상이라 메모를 쓰거나 고칠 수 없습니다.";
        }
        return null;
    }

    private void throwIfBlocked(CurrentUser user, Participation participation, String trgtEmpId) {
        String reason = blockedReason(user, participation, trgtEmpId);
        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private String validContent(String memoCn) {
        String content = memoCn == null ? "" : memoCn.strip();
        if (content.isEmpty()) {
            throw new BusinessException("메모 내용을 입력하세요.");
        }
        if (content.length() > MAX_LENGTH) {
            throw new BusinessException("메모는 " + MAX_LENGTH + "자까지 쓸 수 있습니다. (현재 " + content.length() + "자)");
        }
        return content;
    }
}

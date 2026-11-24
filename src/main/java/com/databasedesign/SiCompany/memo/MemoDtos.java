package com.databasedesign.SiCompany.memo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 평가메모 화면에서 쓰는 DTO 모음.
 */
public final class MemoDtos {

    private MemoDtos() {
    }

    /** ① 내 프로젝트 한 줄. myEndDt가 null이면 아직 투입 중이다. */
    public record MyProject(String prjId, String prjNm, String prjStat, String clientNm,
                            String roleId, String roleNm, LocalDate myStrtDt, LocalDate myEndDt) {
    }

    /** 이 프로젝트에서 내 참여 */
    public record Participation(String prjId, String prjNm, String prjStat, String roleId) {
        public boolean isActive() {
            return "진행".equals(prjStat) || "지연".equals(prjStat);
        }

        public boolean isPm() {
            return "PM".equals(roleId);
        }
    }

    /** 메모를 쓸 수 있는 대상 한 명 */
    public record MemoTarget(String empId, String empNm, String roleId, String roleNm, int memoCnt) {
    }

    /** 팀원 조회 SQL의 한 줄. endDt가 null이면 아직 투입 중이다. */
    public record TeamMemberRow(String empId, String empNm, String roleId, String roleNm,
                                LocalDate strtDt, LocalDate endDt) {
    }

    /** ② 팀원 화면의 한 명. me: 나 자신, memoAllowed: 메모 대상인지 */
    public record TeamMember(TeamMemberRow row, boolean me, boolean memoAllowed, int memoCnt) {
    }

    /** 평가메모 메뉴의 새 메모 대상 드롭다운: 프로젝트별로 묶은 대상 */
    public record TargetGroup(MyProject project, List<MemoTarget> targets) {
    }

    /** ③ 대상에게 쓴 메모 한 건 */
    public record Memo(int memoId, String memoCn, LocalDateTime wrtDttm, LocalDateTime modDttm) {
    }

    /** 평가메모 메뉴: 내가 쓴 메모 한 건 */
    public record MyMemo(int memoId, String prjId, String prjNm, String prjStat,
                         String trgtEmpId, String trgtEmpNm, String memoCn,
                         LocalDateTime wrtDttm, LocalDateTime modDttm) {
    }

    /** 수정·삭제 전에 확인하는 메모의 프로젝트와 대상 */
    public record MemoKey(int memoId, String prjId, String trgtEmpId) {
    }

    /** ③ 화면 전체. blockedReason이 있으면 작성과 수정을 막는다. */
    public record MemoPage(Participation participation, MemoTarget target, List<Memo> memos,
                           String blockedReason) {
    }
}

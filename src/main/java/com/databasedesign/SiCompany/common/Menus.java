package com.databasedesign.SiCompany.common;

import java.util.List;
import java.util.Optional;

/**
 * 헤더 메뉴와 홈 화면 카드의 정의.
 * 메뉴 그룹마다 볼 수 있는 사람(대상)이 정해져 있고, 현재 사용자에게 맞는 그룹만 보여 준다.
 */
public final class Menus {

    /** 메뉴 그룹을 볼 수 있는 사람 */
    public enum Audience {
        ALL("모든 직원"),
        DEV("개발직원"),
        MGT("경영관리팀"),
        EXE("경영진");

        private final String label;

        Audience(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public boolean allows(CurrentUser user) {
            return switch (this) {
                case ALL -> true;
                case DEV -> user.isDev();
                case MGT -> user.isMgt();
                case EXE -> user.isExe();
            };
        }

        /** 대상이 아니면 예외를 던진다. */
        public void check(CurrentUser user) {
            switch (this) {
                case ALL -> { }
                case DEV -> user.requireDev();
                case MGT -> user.require(CurrentUser.MGT);
                case EXE -> user.require(CurrentUser.EXE);
            }
        }
    }

    /** icon: Bootstrap Icons 클래스 이름 (예: bi-kanban) */
    public record MenuItem(String title, String path, String description, String icon) {
    }

    public record MenuGroup(String title, Audience audience, List<MenuItem> items) {
        public boolean visibleTo(CurrentUser user) {
            return user != null && audience.allows(user);
        }
    }

    public static final List<MenuGroup> GROUPS = List.of(
            new MenuGroup("공통", Audience.ALL, List.of(
                    new MenuItem("내 정보", "/me", "내 인적 사항과 보유 기술을 확인하고 수정한다.", "bi-person-badge"),
                    new MenuItem("경력증명서 신청", "/certificates", "수록할 프로젝트 참여를 골라 경력증명서를 신청한다.", "bi-file-earmark-text"))),
            new MenuGroup("내 업무", Audience.DEV, List.of(
                    new MenuItem("내 프로젝트", "/my/projects", "참여 중인 프로젝트와 함께 일하는 팀원을 본다.", "bi-kanban"),
                    new MenuItem("평가메모", "/memos", "진행 중인 프로젝트의 팀원에 대해 나만 보는 메모를 남긴다.", "bi-journal-text"),
                    new MenuItem("내 평가", "/evaluations", "내가 작성할 PM·동료 평가를 작성하고 제출한다.", "bi-clipboard-check"),
                    new MenuItem("고객평가 입력", "/evaluations/customer", "PM이 발주처 담당자에게 받은 고객평가를 입력한다.", "bi-chat-square-quote"))),
            new MenuGroup("인력 관리", Audience.MGT, List.of(
                    new MenuItem("교체 요청", "/replacements", "발주처의 인력 교체 요청을 접수하고 수용 또는 철회한다.", "bi-arrow-left-right"),
                    new MenuItem("발주처 지정인력", "/designations", "발주처별 지명·제외 인력을 등록하고 해제한다.", "bi-person-check"),
                    new MenuItem("인력 배정", "/staffing", "프로젝트를 골라 배정 후보를 보고 직원을 투입한다.", "bi-people"),
                    new MenuItem("프로젝트 관리", "/projects", "프로젝트를 등록하고 투입·철수, 종료·중단을 처리한다.", "bi-folder2-open"),
                    new MenuItem("직원 관리", "/staff", "직원 정보와 기술등급, 부서를 고치고 퇴사를 처리한다. 개인정보 파기를 실행한다.", "bi-person-gear"),
                    new MenuItem("합격자 명부", "/passers", "회원가입에 쓰는 합격자를 등록하고, 가입하지 않은 합격자를 지운다.", "bi-person-lines-fill"),
                    new MenuItem("경력증명서 발급", "/certificates/issue", "신청된 경력증명서를 확인하고 발급한다.", "bi-patch-check"))),
            new MenuGroup("경영 현황", Audience.EXE, List.of(
                    new MenuItem("평가 결과·보정", "/adjustments", "평가별 원점수와 보정점수를 보고 보정회차를 확정한다.", "bi-bar-chart-line"),
                    new MenuItem("프로젝트 현황", "/dashboard", "수행 중인 프로젝트와 프로젝트별 투입 인원·직무·기간을 본다.", "bi-speedometer2"),
                    new MenuItem("직원 검색", "/employees", "직원 정보와 투입 이력, 특정 시점의 참여 프로젝트를 찾는다.", "bi-search"),
                    new MenuItem("보정 실행", "/adjustments/run", "연도별 평가자 보정값을 계산한다.", "bi-sliders"),
                    new MenuItem("인센티브 자료", "/incentives", "인센티브 지급을 위한 기초 자료를 뽑는다.", "bi-cash-coin"))));

    private Menus() {
    }

    public static List<MenuGroup> visibleTo(CurrentUser user) {
        return GROUPS.stream().filter(g -> g.visibleTo(user)).toList();
    }

    public static Optional<MenuGroup> findGroupByPath(String path) {
        return GROUPS.stream()
                .filter(g -> g.items().stream().anyMatch(i -> i.path().equals(path)))
                .findFirst();
    }

    /**
     * 주소가 속한 메뉴: 메뉴 주소와 같거나 '메뉴 주소/'로 시작하는 것 중 가장 긴 것.
     * 예) /evaluations/1495 → 내 평가(/evaluations), /adjustments/run → 보정 실행(/adjustments/run)
     */
    public static Optional<MenuItem> findItemContaining(String path) {
        if (path == null) {
            return Optional.empty();
        }
        return GROUPS.stream()
                .flatMap(g -> g.items().stream())
                .filter(i -> path.equals(i.path()) || path.startsWith(i.path() + "/"))
                .max(java.util.Comparator.comparingInt(i -> i.path().length()));
    }

    public static Optional<MenuItem> findItemByPath(String path) {
        return GROUPS.stream()
                .flatMap(g -> g.items().stream())
                .filter(i -> i.path().equals(path))
                .findFirst();
    }
}

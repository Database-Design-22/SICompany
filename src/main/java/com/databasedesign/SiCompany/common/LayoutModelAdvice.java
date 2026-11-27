package com.databasedesign.SiCompany.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 모든 화면의 공통 헤더와 왼쪽 메뉴에 필요한 값을 모델에 넣는다.
 * - userGroups  : 현재 사용자 선택 목록 (권한별로 묶음: 경영진, 경영관리팀, 일반직원)
 * - currentUser : 고른 사용자 (없으면 null)
 * - currentPath : 사용자를 바꾼 뒤 돌아올 경로
 * - menuGroups  : 현재 사용자가 볼 수 있는 메뉴 그룹 (좁은 화면의 드롭다운)
 * - sideMenu    : 왼쪽 메뉴와 홈 카드 (준비 중 여부, 지금 보는 메뉴 표시)
 * - allMenus    : 사용자를 고르기 전 홈에 보여 줄 전체 메뉴
 */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutModelAdvice {

    private final CurrentUserService currentUserService;
    private final MenuStatus menuStatus;

    /** 왼쪽 메뉴 한 칸 */
    public record SideItem(String title, String path, String description, String icon, boolean ready, boolean active) {
    }

    public record SideGroup(String title, String audience, List<SideItem> items) {
    }

    @ModelAttribute
    public void addLayoutAttributes(Model model, HttpServletRequest request) {
        fill(model.asMap(), request);
    }

    /** 오류 화면처럼 @ModelAttribute가 돌지 않는 곳에서도 쓴다. */
    public void fill(Map<String, Object> model, HttpServletRequest request) {
        CurrentUser currentUser = currentUserService.findCurrentUser(request.getSession(false)).orElse(null);
        List<Menus.MenuGroup> visibleGroups = Menus.visibleTo(currentUser);
        String activePath = Menus.findItemContaining(request.getRequestURI()).map(Menus.MenuItem::path).orElse(null);

        model.put("userGroups", currentUserService.findSelectableUsers().stream()
                .collect(Collectors.groupingBy(CurrentUser::authNm, LinkedHashMap::new, Collectors.toList())));
        model.put("currentUser", currentUser);
        // POST 처리 중 오류 화면에서 사용자를 바꾸면 그 POST 주소를 GET으로 다시 열 수 없으므로 홈으로 돌아간다.
        model.put("currentPath", "GET".equalsIgnoreCase(request.getMethod()) ? request.getRequestURI() : "/");
        model.put("menuGroups", visibleGroups);
        model.put("sideMenu", toSideGroups(visibleGroups, activePath));
        model.put("allMenus", toSideGroups(Menus.GROUPS, activePath));
    }

    private List<SideGroup> toSideGroups(List<Menus.MenuGroup> groups, String activePath) {
        return groups.stream()
                .map(g -> new SideGroup(g.title(), g.audience().getLabel(), g.items().stream()
                        .map(i -> new SideItem(i.title(), i.path(), i.description(), i.icon(),
                                menuStatus.isReady(i.path()), i.path().equals(activePath)))
                        .toList()))
                .toList();
    }
}

package com.databasedesign.SiCompany.common;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import lombok.RequiredArgsConstructor;

/**
 * 메뉴마다 기능이 만들어졌는지 알려 준다.
 * 이름이 PlaceholderController인 컨트롤러가 맡은 주소는 "준비 중"이다 (지금은 모든 메뉴를 만들어 그 컨트롤러가 없다).
 * 새 메뉴를 먼저 "준비 중"으로 두려면 그 이름의 컨트롤러에 주소를 매핑하면 되고, 따로 목록을 고칠 필요가 없다.
 */
@Component
@RequiredArgsConstructor
public class MenuStatus {

    private static final String PLACEHOLDER_CONTROLLER = "PlaceholderController";

    private final ApplicationContext applicationContext;

    private volatile Set<String> placeholderPaths;

    public boolean isReady(String path) {
        return !placeholderPaths().contains(path);
    }

    private Set<String> placeholderPaths() {
        if (placeholderPaths == null) {
            Set<String> paths = new HashSet<>();
            RequestMappingHandlerMapping mapping =
                    applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            mapping.getHandlerMethods().forEach((info, method) -> {
                if (PLACEHOLDER_CONTROLLER.equals(method.getBeanType().getSimpleName()) && info.getPathPatternsCondition() != null) {
                    paths.addAll(info.getPathPatternsCondition().getPatternValues());
                }
            });
            placeholderPaths = Set.copyOf(paths);
        }
        return placeholderPaths;
    }
}

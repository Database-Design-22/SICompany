package com.databasedesign.SiCompany.common;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import lombok.RequiredArgsConstructor;

/**
 * 메뉴마다 기능이 만들어졌는지 알려 준다.
 * PlaceholderController가 맡은 주소는 "준비 중"이다. 따로 목록을 두지 않고 실제 주소 매핑에서 읽으므로,
 * 기능을 만들어 PlaceholderController에서 주소를 지우면 메뉴 표시도 저절로 바뀐다.
 */
@Component
@RequiredArgsConstructor
public class MenuStatus {

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
                if (method.getBeanType().equals(PlaceholderController.class) && info.getPathPatternsCondition() != null) {
                    paths.addAll(info.getPathPatternsCondition().getPatternValues());
                }
            });
            placeholderPaths = Set.copyOf(paths);
        }
        return placeholderPaths;
    }
}

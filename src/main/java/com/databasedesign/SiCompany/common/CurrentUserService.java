package com.databasedesign.SiCompany.common;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /** 세션에는 직원ID만 저장한다. */
    public static final String SESSION_KEY = "currentEmpId";

    private final CurrentUserRepository currentUserRepository;

    public List<CurrentUser> findSelectableUsers() {
        return currentUserRepository.findActiveEmployees();
    }

    public void select(String empId, HttpSession session) {
        CurrentUser user = currentUserRepository.findActiveEmployeeById(empId)
                .orElseThrow(() -> new BusinessException("재직 중인 직원만 현재 사용자로 고를 수 있습니다."));
        session.setAttribute(SESSION_KEY, user.empId());
    }

    public void clear(HttpSession session) {
        session.removeAttribute(SESSION_KEY);
    }

    /** 고른 사용자가 없거나 더 이상 재직 중이 아니면 비어 있다. */
    public Optional<CurrentUser> findCurrentUser(HttpSession session) {
        if (session == null || !(session.getAttribute(SESSION_KEY) instanceof String empId)) {
            return Optional.empty();
        }
        Optional<CurrentUser> user = currentUserRepository.findActiveEmployeeById(empId);
        if (user.isEmpty()) {
            session.removeAttribute(SESSION_KEY);
        }
        return user;
    }

    public CurrentUser getCurrentUser(HttpSession session) {
        return findCurrentUser(session)
                .orElseThrow(() -> new BusinessException("먼저 화면 위쪽에서 현재 사용자를 선택하세요."));
    }
}

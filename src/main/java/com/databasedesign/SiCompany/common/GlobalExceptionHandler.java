package com.databasedesign.SiCompany.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 예외를 받아 공통 오류 화면(common/error)에 한국어 메시지를 보여 준다.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LayoutModelAdvice layoutModelAdvice;

    /** 업무 규칙 위반, 권한 없음, 사용자 미선택 */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusiness(BusinessException e, HttpServletRequest request) {
        return errorView(HttpStatus.BAD_REQUEST, e.getMessage(), null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        return errorView(HttpStatus.NOT_FOUND, "요청한 화면을 찾을 수 없습니다.", null, request);
    }

    /** 예상하지 못한 오류 (DB 오류 포함) */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리 중 오류: {}", request.getRequestURI(), e);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.",
                e.getClass().getSimpleName() + ": " + e.getMessage(), request);
    }

    private ModelAndView errorView(HttpStatus status, String message, String detail, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("common/error");
        mav.setStatus(status);
        mav.addObject("message", message);
        mav.addObject("detail", detail);
        try {
            layoutModelAdvice.fill(mav.getModel(), request);
        } catch (Exception layoutError) {
            // DB에 연결되지 않을 때도 오류 화면은 보여야 한다.
            log.warn("오류 화면의 헤더 정보를 채우지 못했습니다: {}", layoutError.getMessage());
        }
        return mav;
    }
}

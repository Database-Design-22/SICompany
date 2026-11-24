package com.databasedesign.SiCompany.common;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /** 요청 값 이름을 화면에 보여 줄 한국어 이름으로 바꾼다. */
    private static final Map<String, String> PARAM_LABELS = Map.ofEntries(
            Map.entry("memoId", "메모 번호"),
            Map.entry("evalId", "평가 번호"),
            Map.entry("action", "처리 구분"),
            Map.entry("year", "연도"),
            Map.entry("prjId", "프로젝트"),
            Map.entry("prjHistId", "교체 대상"),
            Map.entry("mgrId", "요청 담당자"),
            Map.entry("replReqId", "교체 요청 번호"),
            Map.entry("clientId", "발주처"),
            Map.entry("desigId", "지정 번호"),
            Map.entry("empId", "직원"),
            Map.entry("trgtEmpId", "메모 대상"));

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

    /** 숫자 자리에 글자가 오는 등 요청 값의 형식이 틀렸을 때 (예: /evaluations/abc) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return errorView(HttpStatus.BAD_REQUEST,
                label(e.getName()) + " 값이 올바르지 않습니다. (" + e.getValue() + ")", null, request);
    }

    /** 꼭 필요한 요청 값이 빠졌을 때 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ModelAndView handleMissingParameter(MissingServletRequestParameterException e, HttpServletRequest request) {
        return errorView(HttpStatus.BAD_REQUEST,
                label(e.getParameterName()) + " 값이 빠졌습니다. 화면에서 다시 골라 진행하세요.", null, request);
    }

    /** 버튼으로만 처리하는 주소(POST)를 주소창에서 직접 열었을 때 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return errorView(HttpStatus.METHOD_NOT_ALLOWED,
                "잘못된 접근입니다. 화면의 버튼으로 진행하세요.", null, request);
    }

    /** Service 검사를 통과한 뒤 DB 제약(CHECK, 외래키, 기본키)에 걸렸을 때 (동시에 처리한 경우 등) */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("DB 제약 위반: {} - {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return errorView(HttpStatus.CONFLICT,
                "데이터 규칙(제약조건)에 어긋나 저장하지 않았습니다. 화면을 새로 고친 뒤 다시 시도하세요.",
                e.getMostSpecificCause().getMessage(), request);
    }

    /** 예상하지 못한 오류 */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("처리 중 오류: {}", request.getRequestURI(), e);
        return errorView(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다.",
                e.getClass().getSimpleName() + ": " + e.getMessage(), request);
    }

    private String label(String paramName) {
        return PARAM_LABELS.getOrDefault(paramName, paramName);
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

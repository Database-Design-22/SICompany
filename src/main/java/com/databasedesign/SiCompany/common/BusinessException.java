package com.databasedesign.SiCompany.common;

/**
 * 업무 규칙 위반을 알리는 예외. 메시지는 화면에 그대로 보여 줄 한국어 문장으로 적는다.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

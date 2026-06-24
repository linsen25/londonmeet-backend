package com.londonmeet.common.exception;

/**
 * Business logic exception.
 */
public class BusinessException extends BaseException {

    public BusinessException() {
    }

    public BusinessException(String message) {
        super(message);
    }
}
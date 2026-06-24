package com.londonmeet.common.exception;

/**
 * Base runtime exception for the project.
 */
public class BaseException extends RuntimeException {

    public BaseException() {
    }

    public BaseException(String message) {
        super(message);
    }
}
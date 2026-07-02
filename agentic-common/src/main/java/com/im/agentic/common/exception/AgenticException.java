package com.im.agentic.common.exception;

import lombok.Getter;

@Getter
public class AgenticException extends RuntimeException {
    private final ErrorCode errorCode;

    public AgenticException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

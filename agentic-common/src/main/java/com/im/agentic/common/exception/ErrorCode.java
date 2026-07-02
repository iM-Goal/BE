package com.im.agentic.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    GOAL_NOT_FOUND(HttpStatus.NOT_FOUND, "Goal not found"),
    SLOT_ALREADY_OCCUPIED(HttpStatus.CONFLICT, "The saving slot is already occupied by an active goal"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized access"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid input data"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email already exists"),
    AGENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 에이전트 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."),
    AGENT_PARSE_FAILED(HttpStatus.BAD_GATEWAY, "AI 분석에 실패했습니다. 입력값을 확인하시거나 직접 입력(MANUAL)을 이용해주세요."),
    
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "미션을 찾을 수 없습니다"),
    MISSION_NOT_PROPOSED(HttpStatus.BAD_REQUEST, "수락 대기 중인 미션이 아닙니다"),
    MISSION_NOT_OWNED(HttpStatus.UNAUTHORIZED, "본인의 미션이 아닙니다"),
    DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND, "보증금을 찾을 수 없습니다"),
    DEPOSIT_NOT_LOCKED(HttpStatus.BAD_REQUEST, "잠금 상태의 보증금이 아닙니다"),
    WRONG_CONFIRM_CODE(HttpStatus.BAD_REQUEST, "확인 코드가 맞지 않습니다"),
    CONFIRM_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "확인 코드가 만료되었습니다"),
    NO_OVERAGE(HttpStatus.BAD_REQUEST, "가용 금액을 초과하지 않았습니다");

    private final HttpStatus httpStatus;
    private final String message;
}

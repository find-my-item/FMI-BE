package com.fmi.domain.place.exception;

import com.fmi.global.apiPayload.code.BaseErrorCode;
import com.fmi.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PlaceErrorStatus implements BaseErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "PLACE400-INVALID_REQUEST", "장소 요청 정보가 올바르지 않습니다."),
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "PLACE400-INVALID_SCHEDULE", "장소 운영 일정이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE404-NOT_FOUND", "장소를 찾을 수 없습니다."),
    ALREADY_DELETED(HttpStatus.CONFLICT, "PLACE409-ALREADY_DELETED", "이미 삭제된 장소입니다."),
    ALREADY_ACTIVE(HttpStatus.CONFLICT, "PLACE409-ALREADY_ACTIVE", "이미 활성 상태인 장소입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonDTO getReason() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}

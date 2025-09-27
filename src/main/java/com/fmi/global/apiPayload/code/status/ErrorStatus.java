package com.fmi.global.apiPayload.code.status;

import com.fmi.global.apiPayload.code.BaseErrorCode;
import com.fmi.global.apiPayload.code.ErrorReasonDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {
    // 가장 일반적인 응답
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400","잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401","인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // 인증/회원 관련
    _INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401-INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    _EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH409-EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    _WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "AUTH400-WEAK_PASSWORD", "비밀번호 규칙을 만족하지 않습니다. 대소문자/특수문자 포함 8자 이상이어야 합니다."),
    _RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AUTH400-RESET_TOKEN_INVALID", "유효하지 않은 재설정 토큰입니다."),
    _RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH400-RESET_TOKEN_EXPIRED", "재설정 토큰이 만료되었습니다."),

    // s3 관련 응답
    _NOT_EXIST_FILE (HttpStatus.NOT_FOUND, "FILE404-NOT_FOUND", "존재하지 않는 파일입니다."),
    _NOT_EXIST_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "FILE400-EXT_MISSING", "확장자가 존재하지 않습니다."),
    _INVALID_FILE_EXTENSION(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE415-EXT_UNSUPPORTED", "허용되지 않는 확장자입니다."),
    _INVALID_URL_FORMAT(HttpStatus.BAD_REQUEST, "FILE400-URL_INVALID", "잘못된 URL 형식입니다."),
    _IO_EXCEPTION_UPLOAD_FILE(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500-UPLOAD_IO", "업로드 중 오류가 발생했습니다."),
    _IO_EXCEPTION_DELETE_FILE(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500-DELETE_IO", "파일을 삭제할 수 없습니다.");

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

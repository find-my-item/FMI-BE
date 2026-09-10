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
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // 인증/회원 관련
    _INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401-INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
    _EMAIL_DUPLICATED(HttpStatus.CONFLICT, "AUTH409-EMAIL_DUPLICATED", "이미 사용 중인 이메일입니다."),
    _EMAIL_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "AUTH400-EMAIL_INVALID", "올바른 이메일 형식이 아닙니다."),
    _EMAIL_RECENTLY_DELETED(
            HttpStatus.CONFLICT, "AUTH409-EMAIL_RECENTLY_DELETED", "최근 탈퇴한 이메일입니다. 탈퇴 후 7일 이내 재가입할 수 없습니다."),
    _EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "AUTH400-EMAIL_NOT_VERIFIED", "이메일 인증이 완료되지 않았습니다. 이메일 인증을 먼저 완료해주세요."),
    _EMAIL_VERIFY_FAILED(HttpStatus.BAD_REQUEST, "AUTH400-EMAIL_VERIFY_FAILED", "인증 코드가 만료되었거나 일치하지 않습니다."),
    _EMAIL_SEND_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500-EMAIL_SEND_FAILED", "이메일 발송에 실패했습니다. 잠시 후 다시 시도해주세요."),
    _REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH401-INVALID_REFRESH", "리프레시 토큰이 없습니다."),
    _INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401-INVALID_REFRESH", "리프레시 토큰이 유효하지 않습니다."),
    _NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "AUTH409-NICKNAME_DUPLICATED", "이미 사용 중인 닉네임입니다."),
    _INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "AUTH400-INVALID_NICKNAME", "닉네임은 공백일 수 없습니다."),
    _WEAK_PASSWORD(
            HttpStatus.BAD_REQUEST, "AUTH400-WEAK_PASSWORD", "비밀번호 규칙을 만족하지 않습니다. 8~16자, 대/소문자·숫자·특수문자를 포함해야 합니다."),
    _RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AUTH400-RESET_TOKEN_INVALID", "유효하지 않은 재설정 토큰입니다."),
    _RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH400-RESET_TOKEN_EXPIRED", "재설정 토큰이 만료되었습니다."),
    _INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH400-INVALID_TOKEN", "토큰이 유효하지 않습니다."),
    _TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH400-NOT_FOUND", "Authorization 헤더가 없거나 형식이 잘못되었습니다."),
    _KAKAO_TOKEN_EXCHANGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500-KAKAO_TOKEN_FAILED", "카카오 토큰 교환에 실패했습니다."),
    _KAKAO_CODE_INVALID(
            HttpStatus.BAD_REQUEST, "AUTH400-KAKAO_CODE_INVALID", "카카오 인증 코드가 유효하지 않거나 이미 사용되었습니다. 다시 로그인해주세요."),
    _KAKAO_USERINFO_FETCH_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500-KAKAO_USERINFO_FAILED", "카카오 사용자 정보 조회에 실패했습니다."),
    _APPLE_CODE_INVALID(
            HttpStatus.BAD_REQUEST, "AUTH400-APPLE_CODE_INVALID", "Apple 인증 코드가 유효하지 않거나 이미 사용되었습니다. 다시 로그인해주세요."),
    _APPLE_TOKEN_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "AUTH500-APPLE_TOKEN_FAILED", "Apple 토큰 교환에 실패했습니다. 잠시 후 다시 시도해주세요."),
    _APPLE_CLIENT_SECRET_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "AUTH500-APPLE_CLIENT_SECRET_GENERATION_FAILED",
            "Apple client secret 생성에 실패했습니다. 잠시 후 다시 시도해주세요."),
    _APPLE_ID_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH401-APPLE_ID_TOKEN_INVALID", "Apple ID token이 유효하지 않습니다."),
    _SOCIAL_ACCOUNT(HttpStatus.BAD_REQUEST, "AUTH400-SOCIAL_ACCOUNT", "소셜 로그인 계정입니다."),

    // s3 관련 응답
    _NOT_EXIST_FILE(HttpStatus.NOT_FOUND, "FILE404-NOT_FOUND", "존재하지 않는 파일입니다."),
    _NOT_EXIST_FILE_EXTENSION(HttpStatus.BAD_REQUEST, "FILE400-EXT_MISSING", "확장자가 존재하지 않습니다."),
    _INVALID_FILE_EXTENSION(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "FILE415-EXT_UNSUPPORTED", "허용되지 않는 확장자입니다."),
    _INVALID_URL_FORMAT(HttpStatus.BAD_REQUEST, "FILE400-URL_INVALID", "잘못된 URL 형식입니다."),
    _IO_EXCEPTION_UPLOAD_FILE(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500-UPLOAD_IO", "업로드 중 오류가 발생했습니다."),
    _IO_EXCEPTION_DELETE_FILE(HttpStatus.INTERNAL_SERVER_ERROR, "FILE500-DELETE_IO", "파일을 삭제할 수 없습니다."),

    // 회원 관련 응답
    _USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404-NOT_FOUND", "존재하지 않는 회원입니다."),
    _USER_BLOCK_SELF(HttpStatus.BAD_REQUEST, "USER400-BLOCK_SELF", "자기 자신은 차단할 수 없습니다."),
    _CANNOT_BLOCK_ADMIN(HttpStatus.FORBIDDEN, "USER403-CANNOT_BLOCK_ADMIN", "관리자 계정은 차단할 수 없습니다."),
    _USER_ALREADY_BLOCKED(HttpStatus.CONFLICT, "USER409-ALREADY_BLOCKED", "이미 차단한 사용자입니다."),
    _USER_NOT_BLOCKED(HttpStatus.NOT_FOUND, "USER404-NOT_BLOCKED", "차단되지 않은 사용자입니다."),
    _CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "USER400-PASSWORD_INCORRECT", "현재 비밀번호가 일치하지 않습니다."),
    _PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "USER400-PASSWORD_MISMATCH", "새 비밀번호와 확인이 일치하지 않습니다."),
    _PHONE_DUPLICATED(HttpStatus.CONFLICT, "USER409-PHONE_DUPLICATED", "이미 사용 중인 전화번호입니다."),
    _PREFERRED_LANGUAGE_REQUIRED(HttpStatus.BAD_REQUEST, "USER400-PREFERRED_LANGUAGE_REQUIRED", "선호 언어는 필수입니다."),
    _USER_PAGE_TYPE_INVALID(
            HttpStatus.BAD_REQUEST, "USER400-PAGE_TYPE_INVALID", "type 파라미터는 posts, comments, favorites 중 하나여야 합니다."),

    // 게시글 관련 응답
    _POST_NOT_FOUND(HttpStatus.NOT_FOUND, "POST404-NOT_FOUND", "존재하지 않는 게시글입니다."),
    _POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "POST403-ACCESS_DENIED", "해당 글에 접근 권한이 없습니다"),
    _POST_FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "POST-FAVORITE-NOT_FOUND", "해당 게시글에 대한 즐겨찾기를 하지 않았습니다."),
    _POST_RADIUS_NOT_MATCH(HttpStatus.BAD_REQUEST, "POST-RADIUS-NOT_MATCH", "유효하지 않는 Radius 값입니다."),
    _POST_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "POST_IMAGE-NOT_FOUND", "해당하는 게시글 이미지가 없습니다."),

    _TEMP_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "TEMP-POST404-NOT_FOUND", "임시저장글이 존재하지 않습니다."),
    _POST_UPDATE_RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS, "POST429-UPDATE_RATE_LIMITED", "게시글 수정이 1분에 5번을 초과했습니다. 잠시 후 다시 시도해주세요."),

    // 번역 관련 응답
    _TRANSLATION_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "TRANSLATION429-LIMIT_EXCEEDED", "번역 횟수를 모두 사용했습니다."),
    _TRANSLATION_IN_PROGRESS(HttpStatus.CONFLICT, "TRANSLATION409-IN_PROGRESS", "번역이 진행 중입니다. 잠시 후 다시 시도해주세요."),
    _TRANSLATION_REQUEST_CONFLICT(HttpStatus.CONFLICT, "TRANSLATION409-REQUEST_CONFLICT", "다른 번역 요청에 사용된 요청 ID입니다."),
    _TRANSLATION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "TRANSLATION503-UNAVAILABLE", "번역 상태를 확인할 수 없습니다. 같은 요청 ID로 다시 시도해주세요."),
    _TRANSLATION_TEXT_REQUIRED(HttpStatus.BAD_REQUEST, "TRANSLATION400-TEXT_REQUIRED", "본문이 있는 텍스트 메시지만 번역할 수 있습니다."),
    _TRANSLATION_API_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "TRANSLATION500-API_ERROR", "번역 요청에 실패했습니다. 잠시 후 다시 시도해주세요."),

    // 댓글 관련 응답
    _COMMENT_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT404_PARENT-NOT_FOUND", "존재하지 않는 부모 댓글입니다."),
    _COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT404-NOT_FOUND", "존재하지 않는 댓글입니다."),
    _COMMENT_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "COMMENT400_DEPTH-EXCEEDED", "대댓글은 3단계까지만 작성할 수 있습니다."),
    _COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "COMMENT403-ACCESS_DENIED", "댓글에 접근 권한이 없습니다"),
    _COMMENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "COMMENT400-ALREADY_DELETED", "이미 삭제된 댓글입니다."),
    _COMMENT_LIKE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT404-LIKE_NOT_FOUND", "존재하지 않는 댓글 좋아요입니다."),

    // 목록 관련 응답
    _INVALID_CURSOR(HttpStatus.BAD_REQUEST, "LIST400-INVALID_CURSOR", "유효하지 않은 커서입니다. cursor 값을 확인해주세요."),

    // 채팅방 관련 응답
    _CHATROOM_NOT_ALLOWED(HttpStatus.FORBIDDEN, "CHATROOM403-NOT_ALLOWED", "자신의 글에는 채팅할 수 없습니다."),
    _CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATROOM404-NOT_FOUND", "존재하지 않는 채팅방입니다."),
    _PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "CHATROOM404-PARTICIPANT_NOT_FOUND", "참여 중인 채팅방이 아닙니다."),
    _CHATROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHATROOM403-ACCESS_DENIED", "해당 채팅방에 접근 권한이 없습니다."),
    _CHATROOM_INVALID_STATE(HttpStatus.INTERNAL_SERVER_ERROR, "CHATROOM500-INVALID_STATE", "채팅방 데이터 상태가 올바르지 않습니다."),

    // 채팅 관련 응답
    _IMAGE_NOT_PROVIDED(HttpStatus.BAD_REQUEST, "IMAGE400-NOT_PROVIDED", "전송할 이미지가 없습니다."),
    _MESSAGE_NOT_ALLOWED(HttpStatus.FORBIDDEN, "MESSAGE403-NOT_ALLOWED", "채팅 내역을 조회할 권한이 없습니다."),
    _MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MESSAGE404-NOT_FOUND", "존재하지 않는 채팅 메시지입니다."),

    // 공지사항 관련 응답
    _NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE404-NOT_FOUND", "존재하지 않는 공지사항입니다."),

    // 문의 관련 응답
    _INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY404-NOT_FOUND", "존재하지 않는 문의입니다."),
    _INQUIRY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "INQUIRY403-ACCESS_DENIED", "해당 문의를 조회할 권한이 없습니다."),
    _INQUIRY_GUEST_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "INQUIRY400-GUEST_EMAIL_REQUIRED", "비회원 문의는 이메일이 필수입니다."),
    _INQUIRY_GUEST_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "INQUIRY429-RATE_LIMIT", "잠시 후 다시 시도해주세요."),
    _INQUIRY_IP_BLOCKED(HttpStatus.FORBIDDEN, "INQUIRY403-IP_BLOCKED", "차단된 IP입니다."),
    _INQUIRY_IP_NOT_FOUND(HttpStatus.BAD_REQUEST, "INQUIRY400-IP_NOT_FOUND", "문의에 IP 정보가 없습니다."),
    _IP_ALREADY_BLOCKED(HttpStatus.CONFLICT, "INQUIRY409-IP_ALREADY_BLOCKED", "이미 차단된 IP입니다."),

    // 신고 관련 응답
    _REPORT_ALREADY_EXISTS(HttpStatus.CONFLICT, "REPORT409-ALREADY_EXISTS", "이미 신고한 대상입니다."),
    _REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT404-NOT_FOUND", "존재하지 않는 신고입니다."),
    _REPORT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "REPORT403-ACCESS_DENIED", "해당 신고를 조회할 권한이 없습니다."),
    _REPORT_ADMIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "REPORT403-ADMIN_NOT_ALLOWED", "관리자는 신고할 수 없습니다."),

    // 알림 관련 응답
    _NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION404-NOT_FOUND", "존재하지 않는 알림입니다."),
    _NOTIFICATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "NOTIFICATION403-ACCESS_DENIED", "해당 알림에 접근할 권한이 없습니다."),

    _MAP_LEVEL_INVALID(HttpStatus.BAD_REQUEST, "MAP400-LEVEL_INVALID", "유효하지 않은 지도 레벨입니다.");

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

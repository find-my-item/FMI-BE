package com.fmi.domain.chatmessage.web.dto;

import com.fmi.domain.Enum.LanguageCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class ChatTranslationResponse {

    @Schema(description = "채팅 메시지 번역 결과")
    public record TranslationResponseDTO(
            @Schema(description = "번역한 메시지 ID", example = "3")
            Long messageId,

            @Schema(description = "요청에 사용한 요청 식별자") UUID requestId,
            @Schema(description = "요청에 사용한 채팅방 입장 식별자") UUID roomVisitId,

            @Schema(description = "번역 대상 언어. 조회자의 선호 언어이며, 미설정이면 KO")
            LanguageCode targetLanguage,

            @Schema(description = "번역된 본문", example = "Have you seen my wallet")
            String translatedText,

            @Schema(description = "이번 요청 반영 후의 오늘 사용량") UsageResponseDTO usage) {}

    @Schema(description = "계정 단위 하루 번역 사용량. 매일 자정(Asia/Seoul)에 초기화됩니다.")
    public record UsageResponseDTO(
            @Schema(description = "번역 사용량 기준 날짜(Asia/Seoul)", example = "2026-09-09")
            LocalDate usageDate,

            @Schema(description = "오늘 사용한 번역 횟수", example = "3")
            int usedCount,

            @Schema(description = "하루 최대 번역 횟수", example = "20")
            int limit,

            @Schema(description = "사용량이 초기화되는 시각", example = "2026-09-09T15:00:00Z")
            Instant resetAt,

            @Schema(description = "지금 번역을 요청할 수 있는지 여부. 진행 중인 요청까지 고려합니다.", example = "true")
            boolean canTranslate) {}
}

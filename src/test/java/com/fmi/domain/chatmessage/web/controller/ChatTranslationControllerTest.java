package com.fmi.domain.chatmessage.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.service.ChatTranslationService;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.TranslationResponseDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.UsageResponseDTO;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.ExceptionAdvice;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatTranslationControllerTest {

    private static final Long ROOM_ID = 2L;
    private static final Long MESSAGE_ID = 3L;
    private static final String EMAIL = "user@test.com";
    private static final String TRANSLATE_URL = "/chats/2/messages/3/translations";
    private static final String USAGE_URL = "/users/me/chat-translation-usage";

    private final ChatTranslationService chatTranslationService = mock(ChatTranslationService.class);
    private final UUID requestId = UUID.randomUUID();
    private final UUID roomVisitId = UUID.randomUUID();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        new User(EMAIL, "password", List.of()), null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatTranslationController(chatTranslationService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new ExceptionAdvice())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String requestBody() {
        return "{\"requestId\":\"" + requestId + "\",\"roomVisitId\":\"" + roomVisitId + "\"}";
    }

    private UsageResponseDTO usage(int usedCount, boolean canTranslate) {
        return new UsageResponseDTO(
                LocalDate.of(2026, 9, 9), usedCount, 20, Instant.parse("2026-09-09T15:00:00Z"), canTranslate);
    }

    @Nested
    @DisplayName("POST /chats/{roomId}/messages/{messageId}/translations")
    class Translate {

        @Test
        @DisplayName("번역 결과와 사용량을 기존 공통 응답 규격으로 반환한다")
        void returnsTranslationAndUsageInCommonEnvelope() throws Exception {
            when(chatTranslationService.translate(eq(ROOM_ID), eq(MESSAGE_ID), eq(EMAIL), any()))
                    .thenReturn(new TranslationResponseDTO(
                            MESSAGE_ID, requestId, roomVisitId, LanguageCode.EN, "translated", usage(1, true)));

            mockMvc.perform(post(TRANSLATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.result.messageId").value(MESSAGE_ID))
                    .andExpect(jsonPath("$.result.targetLanguage").value("EN"))
                    .andExpect(jsonPath("$.result.translatedText").value("translated"))
                    .andExpect(jsonPath("$.result.roomVisitId").value(roomVisitId.toString()))
                    .andExpect(jsonPath("$.result.usage.usedCount").value(1));

            verify(chatTranslationService)
                    .translate(ROOM_ID, MESSAGE_ID, EMAIL, new TranslateRequestDTO(requestId, roomVisitId));
        }

        @Test
        @DisplayName("requestId와 roomVisitId가 모두 없으면 400을 반환하고 서비스를 호출하지 않는다")
        void requiresBothRequestIdentifiers() throws Exception {
            mockMvc.perform(post(TRANSLATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(chatTranslationService);
        }

        @Test
        @DisplayName("UUID 형식이 아닌 식별자를 보내면 400을 반환하고 서비스를 호출하지 않는다")
        void rejectsMalformedRequestIdentifier() throws Exception {
            mockMvc.perform(post(TRANSLATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"requestId\":\"invalid\",\"roomVisitId\":\"" + roomVisitId + "\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(chatTranslationService);
        }

        @Test
        @DisplayName("한도를 모두 쓰면 429와 함께 초기화 시각을 result에 담아 반환한다")
        void returnsResetTimeWhenQuotaExhausted() throws Exception {
            when(chatTranslationService.translate(any(), any(), any(), any()))
                    .thenThrow(new GeneralException(
                            ErrorStatus._TRANSLATION_LIMIT_EXCEEDED,
                            Map.of("usedCount", 20, "limit", 20, "nextAvailableAt", "2026-09-09T15:00:00Z")));

            mockMvc.perform(post(TRANSLATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody()))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("TRANSLATION429-LIMIT_EXCEEDED"))
                    .andExpect(jsonPath("$.result.nextAvailableAt").value("2026-09-09T15:00:00Z"));
        }

        @Test
        @DisplayName("번역이 진행 중이면 409(TRANSLATION409-IN_PROGRESS)를 반환한다")
        void returnsConflictWhileTranslationIsInProgress() throws Exception {
            when(chatTranslationService.translate(any(), any(), any(), any()))
                    .thenThrow(new GeneralException(ErrorStatus._TRANSLATION_IN_PROGRESS));

            mockMvc.perform(post(TRANSLATE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TRANSLATION409-IN_PROGRESS"));
        }
    }

    @Nested
    @DisplayName("GET /users/me/chat-translation-usage")
    class GetUsage {

        @Test
        @DisplayName("인증된 계정의 오늘 사용량을 반환한다")
        void returnsTodayUsageOfAuthenticatedAccount() throws Exception {
            when(chatTranslationService.usage(EMAIL)).thenReturn(usage(0, true));

            mockMvc.perform(get(USAGE_URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.result.usedCount").value(0))
                    .andExpect(jsonPath("$.result.limit").value(20))
                    .andExpect(jsonPath("$.result.canTranslate").value(true));

            verify(chatTranslationService).usage(EMAIL);
        }
    }
}

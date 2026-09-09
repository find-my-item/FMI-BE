package com.fmi.domain.chatmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.service.ChatTranslationSourceService.Source;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Outcome;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Status;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Ticket;
import com.fmi.domain.chatmessage.service.RedisTranslationQuotaStore.Usage;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.TranslationResponseDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.UsageResponseDTO;
import com.fmi.domain.user.data.User;
import com.fmi.external.translation.client.TranslationClient;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.service.UserQueryService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatTranslationServiceTest {

    @Mock
    private ChatTranslationSourceService chatTranslationSourceService;

    @Mock
    private RedisTranslationQuotaStore translationQuotaStore;

    @Mock
    private TranslationClient translationClient;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private ChatTranslationService chatTranslationService;

    private static final Long ROOM_ID = 2L;
    private static final Long MESSAGE_ID = 3L;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";
    private static final String ORIGINAL_TEXT = "지갑 보셨나요";
    private static final String TRANSLATED_TEXT = "Have you seen my wallet";
    private static final Instant RESET_AT = Instant.parse("2026-09-09T15:00:00Z");

    private final TranslateRequestDTO request = new TranslateRequestDTO(UUID.randomUUID(), UUID.randomUUID());
    private final Source source = new Source(USER_ID, ORIGINAL_TEXT, LanguageCode.EN);
    private final Usage usage =
            new Usage(LocalDate.of(2026, 9, 9), 19, RedisTranslationQuotaStore.LIMIT, RESET_AT, true);

    private final UsageResponseDTO usageResponse = new UsageResponseDTO(
            usage.usageDate(), usage.usedCount(), usage.limit(), usage.resetAt(), usage.canTranslate());

    private Ticket givenReservation(Status status) {
        given(chatTranslationSourceService.find(ROOM_ID, MESSAGE_ID, EMAIL)).willReturn(source);

        Ticket ticket = new Ticket(
                USER_ID,
                MESSAGE_ID,
                request.requestId(),
                "token",
                usage.usageDate(),
                "fingerprint",
                LanguageCode.EN,
                new Outcome(status, usage, LanguageCode.EN, TRANSLATED_TEXT));
        given(translationQuotaStore.reserve(source, ROOM_ID, MESSAGE_ID, request))
                .willReturn(ticket);
        return ticket;
    }

    private void assertFailsWith(ErrorStatus expected) {
        assertThatThrownBy(() -> chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request))
                .isInstanceOfSatisfying(
                        GeneralException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("translate")
    class Translate {

        @Test
        @DisplayName("예약 → 번역 → 확정 순서로 진행하고, 성공하면 보상하지 않는다")
        void commitsQuotaOnlyAfterTranslationSucceeds() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationQuotaStore.usageAfterCompletion(USER_ID, usage)).willReturn(usage);
            given(translationClient.translate(ORIGINAL_TEXT, LanguageCode.EN)).willReturn(TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willReturn(new Outcome(Status.SUCCEEDED, usage, LanguageCode.EN, TRANSLATED_TEXT));

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.messageId()).isEqualTo(MESSAGE_ID);
            assertThat(response.requestId()).isEqualTo(request.requestId());
            assertThat(response.roomVisitId()).isEqualTo(request.roomVisitId());
            assertThat(response.targetLanguage()).isEqualTo(LanguageCode.EN);
            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            assertThat(response.usage()).isEqualTo(usageResponse);

            InOrder order = inOrder(translationQuotaStore, translationClient);
            order.verify(translationQuotaStore).reserve(source, ROOM_ID, MESSAGE_ID, request);
            order.verify(translationClient).translate(ORIGINAL_TEXT, LanguageCode.EN);
            order.verify(translationQuotaStore).complete(ticket, TRANSLATED_TEXT);
            order.verify(translationQuotaStore).usageAfterCompletion(USER_ID, usage);
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("자정을 넘겨 최초 번역이 완료되면 예약일에 확정하고 오늘 사용량을 응답한다")
        void firstCompletionAfterMidnightReturnsCurrentDayUsage() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(ORIGINAL_TEXT, LanguageCode.EN)).willReturn(TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willReturn(new Outcome(Status.SUCCEEDED, usage, LanguageCode.EN, TRANSLATED_TEXT));
            LocalDate today = usage.usageDate().plusDays(1);
            Instant nextReset = Instant.parse("2026-09-10T15:00:00Z");
            given(translationQuotaStore.usageAfterCompletion(USER_ID, usage))
                    .willReturn(new Usage(today, 0, 20, nextReset, true));

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            assertThat(response.usage()).isEqualTo(new UsageResponseDTO(today, 0, 20, nextReset, true));
            InOrder order = inOrder(translationQuotaStore);
            order.verify(translationQuotaStore).complete(ticket, TRANSLATED_TEXT);
            order.verify(translationQuotaStore).usageAfterCompletion(USER_ID, usage);
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("확정 후 사용량 조회 실패는 503을 반환하고 같은 요청 재시도는 재번역이나 재차감 없이 복구한다")
        void usageFailureAfterCompletionCanBeReplayedWithoutChargingAgain() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(ORIGINAL_TEXT, LanguageCode.EN)).willReturn(TRANSLATED_TEXT);
            Outcome succeeded = new Outcome(Status.SUCCEEDED, usage, LanguageCode.EN, TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT)).willReturn(succeeded);
            given(translationQuotaStore.usageAfterCompletion(USER_ID, usage))
                    .willThrow(new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE));

            assertFailsWith(ErrorStatus._TRANSLATION_UNAVAILABLE);

            Ticket replay = new Ticket(
                    USER_ID,
                    MESSAGE_ID,
                    request.requestId(),
                    "replay-token",
                    ticket.day(),
                    ticket.fingerprint(),
                    LanguageCode.EN,
                    succeeded);
            given(translationQuotaStore.reserve(source, ROOM_ID, MESSAGE_ID, request))
                    .willReturn(replay);
            given(translationQuotaStore.usage(USER_ID)).willReturn(usage);

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            assertThat(response.usage()).isEqualTo(usageResponse);
            verify(translationClient).translate(ORIGINAL_TEXT, LanguageCode.EN);
            verify(translationQuotaStore).complete(any(), any());
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("번역 API가 실패하면 예약을 반환하고 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
        void releasesReservationWhenTranslationApiFails() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(any(), any()))
                    .willThrow(new GeneralException(ErrorStatus._TRANSLATION_API_ERROR));

            assertFailsWith(ErrorStatus._TRANSLATION_API_ERROR);

            verify(translationQuotaStore).fail(ticket);
            verify(translationQuotaStore, never()).complete(any(), any());
        }

        @Test
        @DisplayName("번역 결과가 공백이면 예약을 반환하고 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
        void releasesReservationWhenTranslationIsBlank() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(any(), any())).willReturn(" ");

            assertFailsWith(ErrorStatus._TRANSLATION_API_ERROR);

            verify(translationQuotaStore).fail(ticket);
            verify(translationQuotaStore, never()).complete(any(), any());
        }

        @Test
        @DisplayName("보상(예약 반환)마저 실패해도 번역 실패 오류를 그대로 응답한다")
        void keepsTranslationFailureWhenCompensationAlsoFails() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(any(), any())).willThrow(new IllegalStateException());
            willThrow(new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE))
                    .given(translationQuotaStore)
                    .fail(ticket);

            assertFailsWith(ErrorStatus._TRANSLATION_API_ERROR);
        }

        @Test
        @DisplayName("확정 응답이 모호하면(Redis 장애) 이미 차감됐을 수 있으므로 보상하지 않는다")
        void neverCompensatesWhenCompletionIsAmbiguous() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(any(), any())).willReturn(TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willThrow(new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE));

            assertFailsWith(ErrorStatus._TRANSLATION_UNAVAILABLE);

            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("확정 시점에 예약이 만료돼 FAILED가 돌아오면 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
        void throwsWhenReservationExpiredBeforeCompletion() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationClient.translate(any(), any())).willReturn(TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willReturn(new Outcome(Status.FAILED, usage, LanguageCode.EN, ""));

            assertFailsWith(ErrorStatus._TRANSLATION_API_ERROR);

            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("같은 requestId로 재요청하면 저장된 번역문과 최신 사용량을 추가 차감 없이 반환한다")
        void replayReturnsStoredResultWithoutTranslatingAgain() {
            givenReservation(Status.SUCCEEDED);
            Usage currentUsage = new Usage(usage.usageDate(), 20, 20, RESET_AT, false);
            given(translationQuotaStore.usage(USER_ID)).willReturn(currentUsage);

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            assertThat(response.usage()).isEqualTo(new UsageResponseDTO(usage.usageDate(), 20, 20, RESET_AT, false));
            verifyNoInteractions(translationClient);
            verify(translationQuotaStore, never()).complete(any(), any());
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("날짜가 바뀐 후 재요청하면 기존 번역문과 오늘의 사용량 및 초기화 시각을 반환한다")
        void replayReturnsCurrentDayUsage() {
            givenReservation(Status.SUCCEEDED);
            LocalDate today = usage.usageDate().plusDays(1);
            Instant nextReset = Instant.parse("2026-09-10T15:00:00Z");
            given(translationQuotaStore.usage(USER_ID)).willReturn(new Usage(today, 0, 20, nextReset, true));

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            assertThat(response.usage()).isEqualTo(new UsageResponseDTO(today, 0, 20, nextReset, true));
            verifyNoInteractions(translationClient);
            verify(translationQuotaStore, never()).complete(any(), any());
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("재요청의 최신 사용량 조회 실패 시 과거 값 대신 503 오류를 전달한다")
        void replayFailsWhenCurrentUsageIsUnavailable() {
            givenReservation(Status.SUCCEEDED);
            given(translationQuotaStore.usage(USER_ID))
                    .willThrow(new GeneralException(ErrorStatus._TRANSLATION_UNAVAILABLE));

            assertFailsWith(ErrorStatus._TRANSLATION_UNAVAILABLE);

            verifyNoInteractions(translationClient);
            verify(translationQuotaStore, never()).complete(any(), any());
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("이미 번역해 둔 메시지는 DeepL을 부르지 않고 보관된 번역으로 확정한다")
        void reusesCachedTranslationInsteadOfCallingDeepL() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationQuotaStore.usageAfterCompletion(USER_ID, usage)).willReturn(usage);
            given(translationQuotaStore.findCached(ticket)).willReturn(Optional.of(TRANSLATED_TEXT));
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willReturn(new Outcome(Status.SUCCEEDED, usage, LanguageCode.EN, TRANSLATED_TEXT));

            TranslationResponseDTO response = chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            assertThat(response.translatedText()).isEqualTo(TRANSLATED_TEXT);
            verifyNoInteractions(translationClient);
            // 정책상 재입장 후 번역은 차감 대상이므로 예약은 그대로 확정한다.
            verify(translationQuotaStore).complete(ticket, TRANSLATED_TEXT);
            verify(translationQuotaStore, never()).fail(any());
        }

        @Test
        @DisplayName("보관된 번역이 없으면 DeepL을 호출한다")
        void callsDeepLWhenNothingIsCached() {
            Ticket ticket = givenReservation(Status.RESERVED);
            given(translationQuotaStore.usageAfterCompletion(USER_ID, usage)).willReturn(usage);
            given(translationQuotaStore.findCached(ticket)).willReturn(Optional.empty());
            given(translationClient.translate(ORIGINAL_TEXT, LanguageCode.EN)).willReturn(TRANSLATED_TEXT);
            given(translationQuotaStore.complete(ticket, TRANSLATED_TEXT))
                    .willReturn(new Outcome(Status.SUCCEEDED, usage, LanguageCode.EN, TRANSLATED_TEXT));

            chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request);

            verify(translationClient).translate(ORIGINAL_TEXT, LanguageCode.EN);
        }

        @Test
        @DisplayName("일일 한도를 모두 쓰면 사용량·한도·초기화 시각을 담아 GeneralException(TRANSLATION429-LIMIT_EXCEEDED)을 던진다")
        void throwsWithResetTimeWhenDailyLimitExhausted() {
            givenReservation(Status.LIMIT);

            assertThatThrownBy(() -> chatTranslationService.translate(ROOM_ID, MESSAGE_ID, EMAIL, request))
                    .isInstanceOfSatisfying(GeneralException.class, e -> {
                        assertThat(e.getCode()).isEqualTo(ErrorStatus._TRANSLATION_LIMIT_EXCEEDED);
                        assertThat(e.getErrorArgs())
                                .containsEntry("usedCount", usage.usedCount())
                                .containsEntry("limit", usage.limit())
                                .containsEntry("nextAvailableAt", usage.resetAt());
                    });

            verifyNoInteractions(translationClient);
        }

        @Test
        @DisplayName("진행 중인 요청 때문에 여력이 없으면 한도 초과가 아닌 GeneralException(TRANSLATION409-IN_PROGRESS)을 던진다")
        void distinguishesPendingCapacityFromDailyLimit() {
            givenReservation(Status.IN_PROGRESS);

            assertFailsWith(ErrorStatus._TRANSLATION_IN_PROGRESS);

            verifyNoInteractions(translationClient);
        }

        @Test
        @DisplayName("이미 실패로 끝난 requestId를 재사용하면 재번역하지 않고 GeneralException(TRANSLATION500-API_ERROR)을 던진다")
        void neverRetranslatesRequestIdThatAlreadyFailed() {
            givenReservation(Status.FAILED);

            assertFailsWith(ErrorStatus._TRANSLATION_API_ERROR);

            verifyNoInteractions(translationClient);
            verify(translationQuotaStore, never()).complete(any(), any());
        }

        @Test
        @DisplayName("다른 요청에 이미 쓰인 requestId면 GeneralException(TRANSLATION409-REQUEST_CONFLICT)을 던진다")
        void throwsWhenRequestIdWasUsedForAnotherRequest() {
            givenReservation(Status.CONFLICT);

            assertFailsWith(ErrorStatus._TRANSLATION_REQUEST_CONFLICT);

            verifyNoInteractions(translationClient);
        }

        @Test
        @DisplayName("조회 권한이 없는 메시지는 예약도 번역도 하지 않는다")
        void neverReservesForMessageWithoutPermission() {
            given(chatTranslationSourceService.find(ROOM_ID, MESSAGE_ID, EMAIL))
                    .willThrow(new GeneralException(ErrorStatus._MESSAGE_NOT_ALLOWED));

            assertFailsWith(ErrorStatus._MESSAGE_NOT_ALLOWED);

            verifyNoInteractions(translationQuotaStore, translationClient);
        }
    }

    @Nested
    @DisplayName("usage")
    class GetUsage {

        @Test
        @DisplayName("이메일로 조회한 회원의 계정 단위 사용량을 반환한다")
        void returnsAccountUsageOfAuthenticatedUser() {
            given(userQueryService.findUser(EMAIL))
                    .willReturn(User.builder().id(USER_ID).email(EMAIL).build());
            given(translationQuotaStore.usage(USER_ID)).willReturn(usage);

            UsageResponseDTO result = chatTranslationService.usage(EMAIL);

            assertThat(result).isEqualTo(usageResponse);
            verifyNoInteractions(translationClient, chatTranslationSourceService);
        }
    }
}

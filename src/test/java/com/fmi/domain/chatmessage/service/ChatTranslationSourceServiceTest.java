package com.fmi.domain.chatmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.data.ChatMessage;
import com.fmi.domain.chatmessage.data.enums.MessageType;
import com.fmi.domain.chatmessage.repository.ChatMessageRepository;
import com.fmi.domain.chatmessage.service.ChatTranslationSourceService.Source;
import com.fmi.domain.chatroom.data.ChatRoom;
import com.fmi.domain.chatroom.data.ChatRoomParticipant;
import com.fmi.domain.chatroom.repository.ChatRoomParticipantRepository;
import com.fmi.domain.user.data.User;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.service.UserQueryService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatTranslationSourceServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private ChatTranslationSourceService chatTranslationSourceService;

    private static final Long ROOM_ID = 2L;
    private static final Long MESSAGE_ID = 3L;
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";
    private static final String ORIGINAL_TEXT = "지갑 보셨나요";

    private void givenViewer(LanguageCode preferredLanguage) {
        given(userQueryService.findUser(EMAIL))
                .willReturn(User.builder()
                        .id(USER_ID)
                        .email(EMAIL)
                        .preferredLanguage(preferredLanguage)
                        .build());
    }

    private void givenParticipant(Long visibleFromMessageId) {
        given(chatRoomParticipantRepository.findByChatRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                .willReturn(Optional.of(ChatRoomParticipant.builder()
                        .visibleFromMessageId(visibleFromMessageId)
                        .build()));
    }

    private void givenMessage(Long roomId, MessageType messageType, String content) {
        given(chatMessageRepository.findById(MESSAGE_ID))
                .willReturn(Optional.of(ChatMessage.builder()
                        .id(MESSAGE_ID)
                        .chatRoom(ChatRoom.builder().id(roomId).build())
                        .messageType(messageType)
                        .content(content)
                        .build()));
    }

    private void assertFailsWith(ErrorStatus expected) {
        assertThatThrownBy(() -> chatTranslationSourceService.find(ROOM_ID, MESSAGE_ID, EMAIL))
                .isInstanceOfSatisfying(
                        GeneralException.class, e -> assertThat(e.getCode()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("조회자의 선호 언어와 메시지 원문을 번역 소스로 반환한다")
        void resolvesPreferredLanguageAndOriginalText() {
            givenViewer(LanguageCode.EN);
            givenParticipant(null);
            givenMessage(ROOM_ID, MessageType.TEXT, ORIGINAL_TEXT);

            Source source = chatTranslationSourceService.find(ROOM_ID, MESSAGE_ID, EMAIL);

            assertThat(source).isEqualTo(new Source(USER_ID, ORIGINAL_TEXT, LanguageCode.EN));
        }

        @Test
        @DisplayName("선호 언어가 설정되지 않은 회원은 기본 언어(KO)로 번역한다")
        void usesDefaultLanguageWhenPreferredLanguageIsNull() {
            givenViewer(null);
            givenParticipant(null);
            givenMessage(ROOM_ID, MessageType.TEXT, ORIGINAL_TEXT);

            Source source = chatTranslationSourceService.find(ROOM_ID, MESSAGE_ID, EMAIL);

            assertThat(source.language()).isEqualTo(LanguageCode.KO);
        }

        @Test
        @DisplayName("채팅방 참여자가 아니면 메시지를 읽기 전에 GeneralException(MESSAGE-NOT_ALLOWED)을 던진다")
        void rejectsNonParticipantBeforeReadingMessage() {
            givenViewer(LanguageCode.EN);
            given(chatRoomParticipantRepository.findByChatRoom_IdAndUser_Id(ROOM_ID, USER_ID))
                    .willReturn(Optional.empty());

            assertFailsWith(ErrorStatus._MESSAGE_NOT_ALLOWED);

            verifyNoInteractions(chatMessageRepository);
        }

        @Test
        @DisplayName("다른 채팅방의 메시지 ID를 넘기면 GeneralException(MESSAGE-NOT_ALLOWED)을 던진다")
        void rejectsMessageBelongingToAnotherRoom() {
            givenViewer(LanguageCode.EN);
            givenParticipant(null);
            givenMessage(4L, MessageType.TEXT, ORIGINAL_TEXT);

            assertFailsWith(ErrorStatus._MESSAGE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("나가기 이후 숨겨진 이력은 경계값(visibleFromMessageId와 같은 ID)까지 조회를 거부한다")
        void rejectsHiddenHistoryIncludingCutoff() {
            givenViewer(LanguageCode.EN);
            givenParticipant(MESSAGE_ID);
            givenMessage(ROOM_ID, MessageType.TEXT, ORIGINAL_TEXT);

            assertFailsWith(ErrorStatus._MESSAGE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("존재하지 않는 메시지면 GeneralException(MESSAGE-NOT_FOUND)을 던진다")
        void throwsWhenMessageNotFound() {
            givenViewer(LanguageCode.EN);
            givenParticipant(null);
            given(chatMessageRepository.findById(MESSAGE_ID)).willReturn(Optional.empty());

            assertFailsWith(ErrorStatus._MESSAGE_NOT_FOUND);
        }

        @Test
        @DisplayName("이미지 메시지는 GeneralException(TRANSLATION400-TEXT_REQUIRED)을 던진다")
        void rejectsImageMessage() {
            givenViewer(LanguageCode.EN);
            givenParticipant(null);
            givenMessage(ROOM_ID, MessageType.IMAGE, null);

            assertFailsWith(ErrorStatus._TRANSLATION_TEXT_REQUIRED);
        }

        @Test
        @DisplayName("본문이 공백뿐인 텍스트 메시지는 GeneralException(TRANSLATION400-TEXT_REQUIRED)을 던진다")
        void rejectsBlankText() {
            givenViewer(LanguageCode.EN);
            givenParticipant(null);
            givenMessage(ROOM_ID, MessageType.TEXT, " ");

            assertFailsWith(ErrorStatus._TRANSLATION_TEXT_REQUIRED);
        }
    }
}

package com.fmi.domain.chatmessage.service;

import com.fmi.domain.Enum.LanguageCode;
import com.fmi.domain.chatmessage.data.ChatMessage;
import com.fmi.domain.chatmessage.data.enums.MessageType;
import com.fmi.domain.chatmessage.repository.ChatMessageRepository;
import com.fmi.domain.chatroom.data.ChatRoomParticipant;
import com.fmi.domain.chatroom.repository.ChatRoomParticipantRepository;
import com.fmi.domain.user.data.User;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatTranslationSourceService {

    private static final LanguageCode DEFAULT_LANGUAGE = LanguageCode.KO;

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final UserQueryService userQueryService;

    @Transactional(readOnly = true)
    public Source find(Long roomId, Long messageId, String email) {
        User viewer = userQueryService.findUser(email);
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findByChatRoom_IdAndUser_Id(roomId, viewer.getId())
                .orElseThrow(() -> new GeneralException(ErrorStatus._MESSAGE_NOT_ALLOWED));
        ChatMessage message = chatMessageRepository
                .findById(messageId)
                .orElseThrow(() -> new GeneralException(ErrorStatus._MESSAGE_NOT_FOUND));

        if (!message.getChatRoom().getId().equals(roomId) || isHiddenHistory(messageId, participant)) {
            throw new GeneralException(ErrorStatus._MESSAGE_NOT_ALLOWED);
        }
        if (message.getMessageType() != MessageType.TEXT
                || message.getContent() == null
                || message.getContent().isBlank()) {
            throw new GeneralException(ErrorStatus._TRANSLATION_TEXT_REQUIRED);
        }

        return new Source(viewer.getId(), message.getContent(), resolveLanguage(viewer.getPreferredLanguage()));
    }

    private boolean isHiddenHistory(Long messageId, ChatRoomParticipant participant) {
        Long visibleFrom = participant.getVisibleFromMessageId();
        return visibleFrom != null && messageId <= visibleFrom;
    }

    private LanguageCode resolveLanguage(LanguageCode preferredLanguage) {
        return preferredLanguage == null ? DEFAULT_LANGUAGE : preferredLanguage;
    }

    public record Source(Long userId, String text, LanguageCode language) {}
}

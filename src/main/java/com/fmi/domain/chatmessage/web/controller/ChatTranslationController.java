package com.fmi.domain.chatmessage.web.controller;

import com.fmi.domain.chatmessage.service.ChatTranslationService;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.TranslationResponseDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.UsageResponseDTO;
import com.fmi.domain.chatmessage.web.swagger.ChatTranslationApi;
import com.fmi.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChatTranslationController implements ChatTranslationApi {

    private final ChatTranslationService chatTranslationService;

    @Override
    @PostMapping("/chats/{roomId}/messages/{messageId}/translations")
    public ApiResponse<TranslationResponseDTO> translate(
            @PathVariable Long roomId,
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody TranslateRequestDTO request) {

        String email = userDetails.getUsername();

        return ApiResponse.onSuccess(chatTranslationService.translate(roomId, messageId, email, request));
    }

    @Override
    @GetMapping("/users/me/chat-translation-usage")
    public ApiResponse<UsageResponseDTO> usage(@AuthenticationPrincipal UserDetails userDetails) {

        String email = userDetails.getUsername();

        return ApiResponse.onSuccess(chatTranslationService.usage(email));
    }
}

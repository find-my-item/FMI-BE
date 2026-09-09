package com.fmi.domain.chatmessage.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class ChatTranslationRequest {

    @Schema(description = "채팅 메시지 번역 요청")
    public record TranslateRequestDTO(
            @Schema(
                    description = "요청 식별자. 응답을 받지 못해 재시도할 때만 같은 값을 재사용합니다. "
                            + "직전 요청이 성공했다면 재번역 없이 같은 결과를 돌려주며 횟수도 다시 차감하지 않습니다. "
                            + "번역에 실패한 뒤 사용자가 다시 시도할 때는 새 값을 발급해야 합니다.",
                    example = "3f1a2b4c-5d6e-4f70-8a91-b2c3d4e5f601")
            @NotNull UUID requestId,

            @Schema(
                    description = "채팅방 입장 식별자. 입장할 때마다 새로 발급하며, 같은 requestId를 다른 입장에서 재사용하면 거절됩니다.",
                    example = "9c8b7a65-4321-4dcb-9876-0fedcba98765")
            @NotNull UUID roomVisitId) {}
}

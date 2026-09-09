package com.fmi.domain.chatmessage.web.swagger;

import com.fmi.domain.chatmessage.web.dto.ChatTranslationRequest.TranslateRequestDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.TranslationResponseDTO;
import com.fmi.domain.chatmessage.web.dto.ChatTranslationResponse.UsageResponseDTO;
import com.fmi.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.userdetails.UserDetails;

@Tag(name = "ChatMessage")
public interface ChatTranslationApi {

    @Operation(summary = "채팅 메시지 번역", description = """
                    채팅 메시지 한 건을 조회자의 선호 언어로 번역합니다.
                    선호 언어가 설정되어 있지 않은 사용자는 한국어(KO)로 취급하며, 본문이 있는 텍스트 메시지만 번역할 수 있습니다.

                    - 번역 횟수는 채팅방이 아니라 계정 단위로 하루 20회까지이며, 매일 자정(Asia/Seoul)에 초기화됩니다.
                    - 횟수는 번역에 성공한 경우에만 차감됩니다.
                      채팅방을 나갔다 다시 들어와 같은 메시지를 번역하면 새 요청이므로 횟수는 다시 차감됩니다.
                    - requestId는 요청마다 새로 발급합니다.
                      응답을 받지 못해 재시도하는 경우에는 같은 값을 그대로 보냅니다.
                      직전 요청이 성공했다면 저장된 번역문을 돌려주며 횟수도 다시 차감하지 않습니다.
                    - 최초 성공 및 재시도 응답의 사용량은 응답 생성 시 조회한 현재 날짜 기준입니다.
                      자정을 넘겨 완료된 요청도 횟수는 예약한 날짜에 차감됩니다.
                      성공 확정 후 사용량 조회에 실패하면 503을 반환하며, 같은 requestId로 재시도해야 합니다.
                    - 번역에 실패한 뒤 사용자가 다시 시도할 때는 새 requestId를 발급해야 합니다.
                      실패한 requestId를 그대로 재사용하면 재번역하지 않고 계속 500을 반환합니다.
                    - roomVisitId는 채팅방에 입장할 때마다 새로 발급합니다.
                      같은 requestId를 다른 메시지·입장·대상 언어에 재사용하면 409로 거절합니다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "채팅 메시지 번역 성공",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TranslationResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "TRANSLATION400-TEXT_REQUIRED: 본문이 있는 텍스트 메시지만 번역할 수 있습니다",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "MESSAGE-NOT_ALLOWED: 메시지를 조회할 권한이 없습니다",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "MESSAGE-NOT_FOUND: 존재하지 않는 메시지입니다",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = """
                        TRANSLATION409-IN_PROGRESS: 번역이 진행 중입니다. 잠시 후 다시 시도해주세요
                        TRANSLATION409-REQUEST_CONFLICT: 다른 번역 요청에 사용된 요청 ID입니다""",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "TRANSLATION429-LIMIT_EXCEEDED: 번역 횟수를 모두 사용했습니다. "
                        + "result에 usedCount, limit, nextAvailableAt(초기화 시각)을 함께 내려줍니다",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "TRANSLATION500-API_ERROR: 번역 요청에 실패했습니다. " + "다시 시도할 때는 새 requestId를 발급해야 합니다",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "TRANSLATION503-UNAVAILABLE: 번역 상태를 확인할 수 없습니다. 같은 요청 ID로 다시 시도해주세요",
                content = @Content)
    })
    @Parameters({
        @Parameter(name = "roomId", description = "번역할 메시지가 속한 채팅방의 ID", required = true),
        @Parameter(name = "messageId", description = "번역할 메시지의 ID", required = true)
    })
    ApiResponse<TranslationResponseDTO> translate(
            Long roomId, Long messageId, UserDetails userDetails, TranslateRequestDTO request);

    @Operation(
            summary = "오늘의 채팅 번역 사용량 조회",
            description = "로그인한 계정이 오늘 사용한 번역 횟수를 조회합니다. 사용량은 매일 자정(Asia/Seoul)에 초기화됩니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "번역 사용량 조회 성공",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UsageResponseDTO.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "TRANSLATION503-UNAVAILABLE: 번역 상태를 확인할 수 없습니다",
                content = @Content)
    })
    ApiResponse<UsageResponseDTO> usage(UserDetails userDetails);
}

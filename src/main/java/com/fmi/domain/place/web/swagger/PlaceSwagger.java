package com.fmi.domain.place.web.swagger;

import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.web.dto.response.HomePlaceResponse;
import com.fmi.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Place", description = "장소 탐색 API")
public interface PlaceSwagger {

    @Operation(summary = "홈 성수 콘텐츠 조회", description = "노출 가능한 최신 장소를 최대 5개 조회합니다.")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "홈 장소 조회 성공"))
    ApiResponse<HomePlaceResponse> getHomePlaces(
            @RequestParam(required = false) PlaceType type, @AuthenticationPrincipal UserDetails userDetails);
}

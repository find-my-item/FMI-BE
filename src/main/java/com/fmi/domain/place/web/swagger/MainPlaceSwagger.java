package com.fmi.domain.place.web.swagger;

import com.fmi.domain.place.web.dto.request.PlaceMapSearchRequest;
import com.fmi.domain.place.web.dto.response.PlaceMapResponse;
import com.fmi.domain.place.web.dto.response.PlaceSummaryResponse;
import com.fmi.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Main Place", description = "지도 기반 장소 탐색 API")
public interface MainPlaceSwagger {

    @Operation(summary = "지도 기반 장소 조회", description = "지도 범위 안의 장소 마커와 장소 목록을 함께 조회합니다.")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "지도 장소 조회 성공"))
    ApiResponse<PlaceMapResponse> searchLocation(
            @Valid @ModelAttribute PlaceMapSearchRequest request, @AuthenticationPrincipal UserDetails userDetails);

    @Operation(summary = "장소 동네 정보 조회", description = "선택한 장소의 동네 정보를 조회합니다.")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 동네 정보 조회 성공"))
    ApiResponse<PlaceSummaryResponse> getSummary(
            @PathVariable Long placeId, @AuthenticationPrincipal UserDetails userDetails);
}

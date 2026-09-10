package com.fmi.domain.place.web.swagger;

import com.fmi.domain.place.web.dto.request.FavoritePlaceRequest;
import com.fmi.domain.place.web.dto.response.FavoritePlacePageResponse;
import com.fmi.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ModelAttribute;

@Tag(name = "My Favorite Place", description = "가보고 싶은 장소 API")
public interface MyFavoritePlaceSwagger {

    @Operation(summary = "가보고 싶은 장소 목록 조회", description = "현재 노출할 수 있는 저장 장소를 최신 저장순으로 조회합니다.")
    @ApiResponses(
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 장소 조회 성공"))
    ApiResponse<FavoritePlacePageResponse> getFavorites(
            @Valid @ModelAttribute FavoritePlaceRequest request, @AuthenticationPrincipal UserDetails userDetails);
}

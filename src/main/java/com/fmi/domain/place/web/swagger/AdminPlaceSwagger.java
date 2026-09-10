package com.fmi.domain.place.web.swagger;

import com.fmi.domain.place.web.dto.request.PlaceUpsertRequest;
import com.fmi.domain.place.web.dto.response.PlaceIdResponse;
import com.fmi.domain.place.web.dto.response.PlaceManagementResponse;
import com.fmi.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin Place", description = "관리자 장소 관리 API")
public interface AdminPlaceSwagger {

    @Operation(summary = "장소 등록", description = "장소 정보, 운영 일정과 썸네일을 등록합니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "장소 등록 성공"))
    ResponseEntity<ApiResponse<PlaceManagementResponse>> create(
            @Valid @RequestPart("request") PlaceUpsertRequest request,
            @RequestPart("thumbnail") MultipartFile thumbnail);

    @Operation(summary = "수정용 장소 조회", description = "장소 수정 화면에 필요한 장소 정보와 운영 일정을 조회합니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 조회 성공"))
    ApiResponse<PlaceManagementResponse> get(@PathVariable Long placeId);

    @Operation(summary = "장소 수정", description = "장소 정보와 운영 일정을 전체 수정합니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 수정 성공"))
    ApiResponse<PlaceManagementResponse> update(
            @PathVariable Long placeId,
            @Valid @RequestPart("request") PlaceUpsertRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail);

    @Operation(summary = "장소 삭제", description = "장소를 삭제 상태로 변경합니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 삭제 성공"))
    ApiResponse<PlaceIdResponse> delete(@PathVariable Long placeId);

    @Operation(summary = "장소 복구", description = "삭제된 장소를 활성 상태로 복구합니다.")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소 복구 성공"))
    ApiResponse<PlaceIdResponse> restore(@PathVariable Long placeId);
}

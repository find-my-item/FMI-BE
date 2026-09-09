package com.fmi.domain.place.web.controller;

import com.fmi.domain.place.service.PlaceService;
import com.fmi.domain.place.web.dto.request.PlaceUpsertRequest;
import com.fmi.domain.place.web.dto.response.PlaceIdResponse;
import com.fmi.domain.place.web.dto.response.PlaceManagementResponse;
import com.fmi.domain.place.web.swagger.AdminPlaceSwagger;
import com.fmi.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/places")
@RequiredArgsConstructor
public class AdminPlaceController implements AdminPlaceSwagger {

    private final PlaceService placeService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<PlaceManagementResponse>> create(
            @Valid @RequestPart("request") PlaceUpsertRequest request,
            @RequestPart("thumbnail") MultipartFile thumbnail) {
        Long placeId = placeService.create(request.toCommand(), thumbnail);
        PlaceManagementResponse response = PlaceManagementResponse.from(placeService.getForManagement(placeId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess(response));
    }

    @Override
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceManagementResponse> get(@PathVariable Long placeId) {
        return ApiResponse.onSuccess(PlaceManagementResponse.from(placeService.getForManagement(placeId)));
    }

    @Override
    @PutMapping("/{placeId}")
    public ApiResponse<PlaceManagementResponse> update(
            @PathVariable Long placeId,
            @Valid @RequestPart("request") PlaceUpsertRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail) {
        placeService.update(placeId, request.toCommand(), thumbnail);
        return ApiResponse.onSuccess(PlaceManagementResponse.from(placeService.getForManagement(placeId)));
    }

    @Override
    @DeleteMapping("/{placeId}")
    public ApiResponse<PlaceIdResponse> delete(@PathVariable Long placeId) {
        placeService.delete(placeId);
        return ApiResponse.onSuccess(new PlaceIdResponse(placeId));
    }

    @Override
    @PatchMapping("/{placeId}/restore")
    public ApiResponse<PlaceIdResponse> restore(@PathVariable Long placeId) {
        placeService.restore(placeId);
        return ApiResponse.onSuccess(new PlaceIdResponse(placeId));
    }
}

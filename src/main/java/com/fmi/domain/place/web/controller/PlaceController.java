package com.fmi.domain.place.web.controller;

import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.service.PlaceFavoriteService;
import com.fmi.domain.place.service.PlaceService;
import com.fmi.domain.place.web.dto.response.HomePlaceResponse;
import com.fmi.domain.place.web.dto.response.PlaceFavoriteResponse;
import com.fmi.domain.place.web.dto.response.PlaceSummaryResponse;
import com.fmi.domain.place.web.swagger.PlaceSwagger;
import com.fmi.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController implements PlaceSwagger {

    private final PlaceService placeService;
    private final PlaceFavoriteService placeFavoriteService;

    @Override
    @GetMapping
    public ApiResponse<HomePlaceResponse> getHomePlaces(
            @RequestParam(required = false) PlaceType type, @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        return ApiResponse.onSuccess(new HomePlaceResponse(placeService.getHomePlaces(type, userEmail).stream()
                .map(PlaceSummaryResponse::from)
                .toList()));
    }

    @Override
    @PostMapping("/{placeId}/favorites")
    public ApiResponse<PlaceFavoriteResponse> saveFavorite(
            @PathVariable Long placeId, @AuthenticationPrincipal UserDetails userDetails) {
        placeFavoriteService.save(placeId, userDetails.getUsername());
        return ApiResponse.onSuccess(new PlaceFavoriteResponse(placeId, true));
    }

    @Override
    @DeleteMapping("/{placeId}/favorites")
    public ApiResponse<PlaceFavoriteResponse> cancelFavorite(
            @PathVariable Long placeId, @AuthenticationPrincipal UserDetails userDetails) {
        placeFavoriteService.cancel(placeId, userDetails.getUsername());
        return ApiResponse.onSuccess(new PlaceFavoriteResponse(placeId, false));
    }
}

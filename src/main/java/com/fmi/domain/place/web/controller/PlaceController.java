package com.fmi.domain.place.web.controller;

import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.service.PlaceService;
import com.fmi.domain.place.web.dto.response.HomePlaceResponse;
import com.fmi.domain.place.web.dto.response.PlaceSummaryResponse;
import com.fmi.domain.place.web.swagger.PlaceSwagger;
import com.fmi.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/places")
@RequiredArgsConstructor
public class PlaceController implements PlaceSwagger {

    private final PlaceService placeService;

    @Override
    @GetMapping
    public ApiResponse<HomePlaceResponse> getHomePlaces(
            @RequestParam(required = false) PlaceType type, @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        return ApiResponse.onSuccess(new HomePlaceResponse(placeService.getHomePlaces(type, userEmail).stream()
                .map(PlaceSummaryResponse::from)
                .toList()));
    }
}

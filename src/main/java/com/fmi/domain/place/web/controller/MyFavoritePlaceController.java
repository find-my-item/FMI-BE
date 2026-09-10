package com.fmi.domain.place.web.controller;

import com.fmi.domain.place.service.PlaceFavoriteService;
import com.fmi.domain.place.web.dto.request.FavoritePlaceRequest;
import com.fmi.domain.place.web.dto.response.FavoritePlacePageResponse;
import com.fmi.domain.place.web.swagger.MyFavoritePlaceSwagger;
import com.fmi.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/favorite-places")
@RequiredArgsConstructor
public class MyFavoritePlaceController implements MyFavoritePlaceSwagger {

    private final PlaceFavoriteService placeFavoriteService;

    @Override
    @GetMapping
    public ApiResponse<FavoritePlacePageResponse> getFavorites(
            @Valid @ModelAttribute FavoritePlaceRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        return ApiResponse.onSuccess(FavoritePlacePageResponse.from(placeFavoriteService.getFavorites(
                userDetails.getUsername(), request.getLastFavoriteUpdatedAt(), request.getSize())));
    }
}

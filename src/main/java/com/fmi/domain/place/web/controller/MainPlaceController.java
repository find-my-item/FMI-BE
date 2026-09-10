package com.fmi.domain.place.web.controller;

import com.fmi.domain.map.service.PostMapService;
import com.fmi.domain.map.web.dto.response.PostMarkerResponse;
import com.fmi.domain.place.data.PlaceMapSearchResult;
import com.fmi.domain.place.data.PlaceSummary;
import com.fmi.domain.place.service.PlaceService;
import com.fmi.domain.place.web.dto.request.NearbyPostMarkerRequest;
import com.fmi.domain.place.web.dto.request.NearbyPostRequest;
import com.fmi.domain.place.web.dto.request.PlaceMapSearchRequest;
import com.fmi.domain.place.web.dto.response.NearbyPostResponse;
import com.fmi.domain.place.web.dto.response.PlaceMapResponse;
import com.fmi.domain.place.web.dto.response.PlaceSummaryResponse;
import com.fmi.domain.place.web.swagger.MainPlaceSwagger;
import com.fmi.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/main/places")
@RequiredArgsConstructor
public class MainPlaceController implements MainPlaceSwagger {

    private final PlaceService placeService;
    private final PostMapService postMapService;

    @Override
    @GetMapping("/search-location")
    public ApiResponse<PlaceMapResponse> searchLocation(
            @Valid @ModelAttribute PlaceMapSearchRequest request, @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        PlaceMapSearchResult result = placeService.getMapPlaces(
                request.getLatitude(), request.getLongitude(), request.getLevel(), request.getType(), userEmail);
        return ApiResponse.onSuccess(PlaceMapResponse.from(result));
    }

    @Override
    @GetMapping("/{placeId}/summary")
    public ApiResponse<PlaceSummaryResponse> getSummary(
            @PathVariable Long placeId, @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        return ApiResponse.onSuccess(PlaceSummaryResponse.from(placeService.getPlaceSummary(placeId, userEmail)));
    }

    @Override
    @GetMapping("/{placeId}/nearby-posts")
    public ApiResponse<NearbyPostResponse> getNearbyPosts(
            @PathVariable Long placeId,
            @Valid @ModelAttribute NearbyPostRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        PlaceSummary place = placeService.getPlaceSummary(placeId, userEmail);
        return ApiResponse.onSuccess(NearbyPostResponse.from(postMapService.getNearbyPosts(
                place.latitude(),
                place.longitude(),
                request.getLevel(),
                request.getPostType(),
                request.getPostStatus(),
                request.getCategory(),
                request.getLastDistance(),
                request.getLastPostId(),
                userDetails)));
    }

    @Override
    @GetMapping("/{placeId}/nearby-post-markers")
    public ApiResponse<List<PostMarkerResponse>> getNearbyPostMarkers(
            @PathVariable Long placeId,
            @Valid @ModelAttribute NearbyPostMarkerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userEmail = userDetails == null ? null : userDetails.getUsername();
        PlaceSummary place = placeService.getPlaceSummary(placeId, userEmail);
        return ApiResponse.onSuccess(postMapService.getNearbyPostMarkers(
                place.latitude(),
                place.longitude(),
                request.getLevel(),
                request.getPostType(),
                request.getPostStatus(),
                request.getCategory(),
                userDetails));
    }
}

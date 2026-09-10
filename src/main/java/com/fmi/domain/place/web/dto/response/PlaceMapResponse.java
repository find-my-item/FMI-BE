package com.fmi.domain.place.web.dto.response;

import com.fmi.domain.place.data.PlaceMapSearchResult;
import com.fmi.domain.place.data.PlaceSummary;
import com.fmi.domain.place.data.enums.PlaceType;
import java.util.List;

public record PlaceMapResponse(List<PlaceMarker> placeMarkers, List<PlaceSummaryResponse> places, int totalCount) {

    public static PlaceMapResponse from(PlaceMapSearchResult result) {
        return new PlaceMapResponse(
                result.places().stream().map(PlaceMarker::from).toList(),
                result.places().stream().map(PlaceSummaryResponse::from).toList(),
                result.totalCount());
    }

    public record PlaceMarker(Long placeId, double latitude, double longitude, PlaceType type, String thumbnailUrl) {
        static PlaceMarker from(PlaceSummary place) {
            return new PlaceMarker(
                    place.placeId(), place.latitude(), place.longitude(), place.type(), place.thumbnailUrl());
        }
    }
}

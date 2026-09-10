package com.fmi.domain.place.web.dto.response;

import com.fmi.domain.place.data.FavoritePlacePage;
import java.time.LocalDateTime;
import java.util.List;

public record FavoritePlacePageResponse(
        List<PlaceSummaryResponse> places, boolean hasNext, LocalDateTime nextFavoriteUpdatedAt) {

    public static FavoritePlacePageResponse from(FavoritePlacePage page) {
        return new FavoritePlacePageResponse(
                page.places().stream().map(PlaceSummaryResponse::from).toList(),
                page.hasNext(),
                page.nextFavoriteUpdatedAt());
    }
}

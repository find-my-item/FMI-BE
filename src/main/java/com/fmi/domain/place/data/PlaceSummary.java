package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDate;

public record PlaceSummary(
        Long placeId,
        String name,
        String address,
        double latitude,
        double longitude,
        String station,
        int stationDistanceMeters,
        PlaceType type,
        String thumbnailUrl,
        LocalDate operationStartDate,
        LocalDate operationEndDate,
        PlaceOperationState operationState,
        boolean favorite) {

    public static PlaceSummary from(Place place, PlaceOperationState operationState, boolean favorite) {
        PlaceOperationPeriod operationPeriod = place.getOperationPeriod();
        return new PlaceSummary(
                place.getId(),
                place.getName(),
                place.getAddress(),
                place.getLatitude(),
                place.getLongitude(),
                place.getStation(),
                place.getStationDistanceMeters(),
                place.getType(),
                place.getThumbnailUrl(),
                operationPeriod == null ? null : operationPeriod.getStartDate(),
                operationPeriod == null ? null : operationPeriod.getEndDate(),
                operationState,
                favorite);
    }
}

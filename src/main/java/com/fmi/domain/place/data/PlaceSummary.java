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
        boolean favorite) {}

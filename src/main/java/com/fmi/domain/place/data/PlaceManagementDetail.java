package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDate;
import java.util.List;

public record PlaceManagementDetail(
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
        List<PlaceDailySchedule> dailySchedules) {}

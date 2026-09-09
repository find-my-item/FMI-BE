package com.fmi.domain.place.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fmi.domain.place.data.PlaceSummary;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceOperationStatus;
import com.fmi.domain.place.data.enums.PlaceType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlaceSummaryResponse(
        Long placeId,
        String name,
        String address,
        double latitude,
        double longitude,
        String station,
        int stationDistanceMeters,
        PlaceType type,
        String thumbnailUrl,
        PlaceOperationStatus operationStatus,
        OperationPeriod operationPeriod,
        @JsonInclude(JsonInclude.Include.ALWAYS) List<TimeRange> todayBusinessHours,
        boolean isFavorite) {

    public static PlaceSummaryResponse from(PlaceSummary summary) {
        OperationPeriod operationPeriod = summary.operationStartDate() == null
                ? null
                : new OperationPeriod(summary.operationStartDate(), summary.operationEndDate());
        List<TimeRange> todayBusinessHours = summary.operationState().todayBusinessHours() == null
                ? null
                : summary.operationState().todayBusinessHours().stream()
                        .map(TimeRange::from)
                        .toList();
        return new PlaceSummaryResponse(
                summary.placeId(),
                summary.name(),
                summary.address(),
                summary.latitude(),
                summary.longitude(),
                summary.station(),
                summary.stationDistanceMeters(),
                summary.type(),
                summary.thumbnailUrl(),
                summary.operationState().status(),
                operationPeriod,
                todayBusinessHours,
                summary.favorite());
    }

    public record TimeRange(PlaceBusinessHourType type, LocalTime startTime, LocalTime endTime) {
        static TimeRange from(PlaceTimeRange range) {
            return new TimeRange(range.getType(), range.getStartTime(), range.getEndTime());
        }
    }

    public record OperationPeriod(LocalDate startDate, LocalDate endDate) {}
}

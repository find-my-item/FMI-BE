package com.fmi.domain.place.web.dto.response;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceManagementDetail;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PlaceManagementResponse(
        Long placeId,
        String name,
        String address,
        double latitude,
        double longitude,
        String station,
        int stationDistanceMeters,
        PlaceType type,
        String thumbnailUrl,
        OperationPeriod operationPeriod,
        List<WeeklySchedule> weeklySchedules) {

    public static PlaceManagementResponse from(PlaceManagementDetail detail) {
        OperationPeriod operationPeriod = detail.operationStartDate() == null
                ? null
                : new OperationPeriod(detail.operationStartDate(), detail.operationEndDate());
        return new PlaceManagementResponse(
                detail.placeId(),
                detail.name(),
                detail.address(),
                detail.latitude(),
                detail.longitude(),
                detail.station(),
                detail.stationDistanceMeters(),
                detail.type(),
                detail.thumbnailUrl(),
                operationPeriod,
                detail.dailySchedules().stream().map(WeeklySchedule::from).toList());
    }

    public record WeeklySchedule(DayOfWeek dayOfWeek, boolean isClosed, List<TimeRange> timeRanges) {
        static WeeklySchedule from(PlaceDailySchedule schedule) {
            return new WeeklySchedule(
                    schedule.dayOfWeek(),
                    schedule.closed(),
                    schedule.timeRanges().stream().map(TimeRange::from).toList());
        }
    }

    public record TimeRange(PlaceBusinessHourType type, LocalTime startTime, LocalTime endTime) {
        static TimeRange from(PlaceTimeRange timeRange) {
            return new TimeRange(timeRange.getType(), timeRange.getStartTime(), timeRange.getEndTime());
        }
    }

    public record OperationPeriod(LocalDate startDate, LocalDate endDate) {}
}

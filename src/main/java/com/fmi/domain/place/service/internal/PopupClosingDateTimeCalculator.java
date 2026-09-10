package com.fmi.domain.place.service.internal;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PopupClosingDateTimeCalculator {

    public LocalDateTime calculate(PlaceOperationPeriod operationPeriod, List<PlaceDailySchedule> dailySchedules) {
        LocalDate lastBusinessDate = findLastBusinessDate(operationPeriod, dailySchedules);
        PlaceDailySchedule schedule = findSchedule(dailySchedules, lastBusinessDate);
        return schedule.timeRanges().stream()
                .filter(range -> range.getType() == PlaceBusinessHourType.BUSINESS)
                .map(range -> range.closingDateTime(lastBusinessDate))
                .max(LocalDateTime::compareTo)
                .orElseThrow(() -> new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE));
    }

    private LocalDate findLastBusinessDate(
            PlaceOperationPeriod operationPeriod, List<PlaceDailySchedule> dailySchedules) {
        LocalDate date = operationPeriod.getEndDate();
        while (!date.isBefore(operationPeriod.getStartDate())) {
            if (!findSchedule(dailySchedules, date).closed()) {
                return date;
            }
            date = date.minusDays(1);
        }
        throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
    }

    private PlaceDailySchedule findSchedule(List<PlaceDailySchedule> dailySchedules, LocalDate date) {
        return dailySchedules.stream()
                .filter(schedule -> schedule.dayOfWeek() == date.getDayOfWeek())
                .findFirst()
                .orElseThrow(() -> new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE));
    }
}

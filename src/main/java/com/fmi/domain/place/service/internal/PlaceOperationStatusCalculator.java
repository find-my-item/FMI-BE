package com.fmi.domain.place.service.internal;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceOperationState;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceOperationStatus;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class PlaceOperationStatusCalculator {

    public PlaceOperationState calculate(
            PlaceType placeType,
            PlaceOperationPeriod operationPeriod,
            List<PlaceDailySchedule> dailySchedules,
            LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        PlaceDailySchedule todaySchedule = dailySchedules.stream()
                .filter(schedule -> schedule.dayOfWeek() == today.getDayOfWeek())
                .findFirst()
                .orElseThrow(() -> new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE));
        if (todaySchedule.closed()) {
            return new PlaceOperationState(PlaceOperationStatus.CLOSED, null);
        }

        PlaceDailySchedule yesterdaySchedule = dailySchedules.stream()
                .filter(schedule -> schedule.dayOfWeek() == today.minusDays(1).getDayOfWeek())
                .findFirst()
                .orElseThrow(() -> new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE));
        List<BusinessHourOccurrence> occurrences = Stream.concat(
                        (yesterdaySchedule.closed()
                                        ? Stream.<PlaceTimeRange>empty()
                                        : yesterdaySchedule.timeRanges().stream())
                                .filter(PlaceTimeRange::projectsToNextDay)
                                .map(range -> new BusinessHourOccurrence(today.minusDays(1), range)),
                        todaySchedule.timeRanges().stream().map(range -> new BusinessHourOccurrence(today, range)))
                .sorted(Comparator.comparing(BusinessHourOccurrence::startDateTime))
                .toList();
        if (placeType == PlaceType.POPUP && today.isBefore(operationPeriod.getStartDate())) {
            return new PlaceOperationState(
                    PlaceOperationStatus.UPCOMING,
                    occurrences.stream().map(BusinessHourOccurrence::range).toList());
        }
        boolean inBusinessHour = occurrences.stream()
                .filter(occurrence -> occurrence.range().getType() == PlaceBusinessHourType.BUSINESS)
                .anyMatch(occurrence -> occurrence.includes(now));
        boolean inBreakTime = occurrences.stream()
                .filter(occurrence -> occurrence.range().getType() == PlaceBusinessHourType.BREAK_TIME)
                .anyMatch(occurrence -> occurrence.includes(now));
        PlaceOperationStatus status = !inBusinessHour
                ? PlaceOperationStatus.UPCOMING
                : inBreakTime ? PlaceOperationStatus.BREAK_TIME : PlaceOperationStatus.OPEN;
        return new PlaceOperationState(
                status, occurrences.stream().map(BusinessHourOccurrence::range).toList());
    }

    private record BusinessHourOccurrence(LocalDate startDate, PlaceTimeRange range) {
        LocalDateTime startDateTime() {
            return startDate.atTime(range.getStartTime());
        }

        boolean includes(LocalDateTime dateTime) {
            return !dateTime.isBefore(startDateTime()) && dateTime.isBefore(range.closingDateTime(startDate));
        }
    }
}

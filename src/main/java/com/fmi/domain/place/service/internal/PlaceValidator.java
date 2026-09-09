package com.fmi.domain.place.service.internal;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaceValidator {

    public void validate(
            PlaceType placeType, PlaceOperationPeriod operationPeriod, List<PlaceDailySchedule> dailySchedules) {
        validateOperationPeriod(placeType, operationPeriod);
        validateWeeklyScheduleComposition(dailySchedules);
        validateTimeRangeFields(dailySchedules);
        validateBusinessHours(dailySchedules);
        validateSameTypeOverlaps(dailySchedules);
        validateBreakTimes(dailySchedules);
        validateProjectedOverlaps(dailySchedules);
        validateNotAllClosed(dailySchedules);
    }

    private void validateOperationPeriod(PlaceType placeType, PlaceOperationPeriod operationPeriod) {
        boolean validPopupPeriod = placeType == PlaceType.POPUP
                && operationPeriod != null
                && operationPeriod.getStartDate() != null
                && operationPeriod.getEndDate() != null
                && !operationPeriod.getStartDate().isAfter(operationPeriod.getEndDate());
        boolean validPermanentPlace = placeType != PlaceType.POPUP && operationPeriod == null;
        if (!validPopupPeriod && !validPermanentPlace) {
            throw new GeneralException(PlaceErrorStatus.INVALID_REQUEST);
        }
    }

    private void validateWeeklyScheduleComposition(List<PlaceDailySchedule> dailySchedules) {
        if (dailySchedules == null || dailySchedules.size() != DayOfWeek.values().length) {
            throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
        }

        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (PlaceDailySchedule schedule : dailySchedules) {
            if (schedule == null || schedule.dayOfWeek() == null || !days.add(schedule.dayOfWeek())) {
                throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }
    }

    private void validateTimeRangeFields(List<PlaceDailySchedule> dailySchedules) {
        boolean invalid = dailySchedules.stream()
                .flatMap(schedule -> schedule.timeRanges().stream())
                .anyMatch(range -> range == null
                        || range.getType() == null
                        || range.getStartTime() == null
                        || range.getEndTime() == null);
        if (invalid) {
            throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
        }
    }

    private void validateBusinessHours(List<PlaceDailySchedule> dailySchedules) {
        boolean missing = dailySchedules.stream().anyMatch(schedule -> schedule.timeRanges().stream()
                .noneMatch(range -> range.getType() == PlaceBusinessHourType.BUSINESS));
        if (missing) {
            throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
        }
    }

    private void validateSameTypeOverlaps(List<PlaceDailySchedule> dailySchedules) {
        for (PlaceDailySchedule schedule : dailySchedules) {
            for (PlaceBusinessHourType type : PlaceBusinessHourType.values()) {
                List<PlaceTimeRange> ranges = rangesOfType(schedule, type);
                for (int left = 0; left < ranges.size(); left++) {
                    for (int right = left + 1; right < ranges.size(); right++) {
                        if (ranges.get(left).overlaps(ranges.get(right))) {
                            throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
                        }
                    }
                }
            }
        }
    }

    private void validateBreakTimes(List<PlaceDailySchedule> dailySchedules) {
        for (PlaceDailySchedule schedule : dailySchedules) {
            List<PlaceTimeRange> businesses = rangesOfType(schedule, PlaceBusinessHourType.BUSINESS);
            for (PlaceTimeRange breakTime : rangesOfType(schedule, PlaceBusinessHourType.BREAK_TIME)) {
                if (businesses.stream().noneMatch(business -> business.contains(breakTime))) {
                    throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
                }
            }
        }
    }

    private void validateProjectedOverlaps(List<PlaceDailySchedule> dailySchedules) {
        for (PlaceDailySchedule schedule : dailySchedules) {
            PlaceDailySchedule nextSchedule =
                    findSchedule(dailySchedules, schedule.dayOfWeek().plus(1));
            for (PlaceBusinessHourType type : PlaceBusinessHourType.values()) {
                List<PlaceTimeRange> nextDayRanges = rangesOfType(nextSchedule, type);
                for (PlaceTimeRange range : rangesOfType(schedule, type)) {
                    if (nextDayRanges.stream().anyMatch(range::projectedOverlap)) {
                        throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
                    }
                }
            }
        }
    }

    private void validateNotAllClosed(List<PlaceDailySchedule> dailySchedules) {
        if (dailySchedules.stream().allMatch(PlaceDailySchedule::closed)) {
            throw new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE);
        }
    }

    private PlaceDailySchedule findSchedule(List<PlaceDailySchedule> dailySchedules, DayOfWeek dayOfWeek) {
        return dailySchedules.stream()
                .filter(schedule -> schedule.dayOfWeek() == dayOfWeek)
                .findFirst()
                .orElseThrow(() -> new GeneralException(PlaceErrorStatus.INVALID_SCHEDULE));
    }

    private List<PlaceTimeRange> rangesOfType(PlaceDailySchedule schedule, PlaceBusinessHourType type) {
        return schedule.timeRanges().stream()
                .filter(range -> range.getType() == type)
                .toList();
    }
}

package com.fmi.domain.place.service.internal;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.PlaceBusinessHour;
import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceTimeRange;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaceBusinessHourUpdater {

    public void update(Place place, List<PlaceDailySchedule> dailySchedules, LocalDateTime deletedAt) {
        for (PlaceDailySchedule schedule : dailySchedules) {
            for (PlaceTimeRange range : schedule.timeRanges()) {
                updateRequestedHour(place, schedule, range);
            }
        }

        place.getBusinessHours().stream()
                .filter(PlaceBusinessHour::isActive)
                .filter(businessHour -> !contains(dailySchedules, businessHour))
                .forEach(businessHour -> businessHour.delete(deletedAt));
    }

    private void updateRequestedHour(Place place, PlaceDailySchedule schedule, PlaceTimeRange range) {
        PlaceBusinessHour existing = place.getBusinessHours().stream()
                .filter(businessHour -> matches(businessHour, schedule, range))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            place.addBusinessHour(PlaceBusinessHour.builder()
                    .dayOfWeek(schedule.dayOfWeek())
                    .closed(schedule.closed())
                    .timeRange(range)
                    .build());
            return;
        }

        existing.restore();
        existing.reviseClosed(schedule.closed());
    }

    private boolean contains(List<PlaceDailySchedule> dailySchedules, PlaceBusinessHour businessHour) {
        return dailySchedules.stream().anyMatch(schedule -> schedule.timeRanges().stream()
                .anyMatch(range -> matches(businessHour, schedule, range)));
    }

    private boolean matches(PlaceBusinessHour businessHour, PlaceDailySchedule schedule, PlaceTimeRange range) {
        return businessHour.hasSameRange(schedule.dayOfWeek(), range);
    }
}

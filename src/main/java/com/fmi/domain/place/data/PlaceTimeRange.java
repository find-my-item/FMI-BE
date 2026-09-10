package com.fmi.domain.place.data;

import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceTimeRange {
    private static final int MINUTES_PER_DAY = 24 * 60;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PlaceBusinessHourType type;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public PlaceTimeRange(PlaceBusinessHourType type, LocalTime startTime, LocalTime endTime) {
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean overlaps(PlaceTimeRange other) {
        return normalizedStartMinute() < other.normalizedEndMinute()
                && other.normalizedStartMinute() < normalizedEndMinute();
    }

    public boolean contains(PlaceTimeRange other) {
        return normalizedStartMinute() <= other.normalizedStartMinute()
                && other.normalizedEndMinute() <= normalizedEndMinute();
    }

    public boolean projectsToNextDay() {
        return normalizedEndMinute() > MINUTES_PER_DAY;
    }

    public boolean projectedOverlap(PlaceTimeRange nextDayRange) {
        return projectsToNextDay() && nextDayRange.normalizedStartMinute() < normalizedEndMinute() - MINUTES_PER_DAY;
    }

    public LocalDateTime closingDateTime(LocalDate startDate) {
        LocalDate closingDate = endTime.isAfter(startTime) ? startDate : startDate.plusDays(1);
        return closingDate.atTime(endTime);
    }

    private int normalizedStartMinute() {
        return startTime.getHour() * 60 + startTime.getMinute();
    }

    private int normalizedEndMinute() {
        int start = normalizedStartMinute();
        int end = endTime.getHour() * 60 + endTime.getMinute();
        return end <= start ? end + MINUTES_PER_DAY : end;
    }
}

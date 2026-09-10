package com.fmi.domain.place.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("장소 영업시간")
class PlaceBusinessHourTest {
    @Test
    @DisplayName("영업시간의 휴무 상태를 변경한다")
    void changesClosedState() {
        PlaceBusinessHour hour = PlaceBusinessHour.builder()
                .dayOfWeek(DayOfWeek.MONDAY)
                .closed(false)
                .timeRange(new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.NOON, LocalTime.of(18, 0)))
                .build();
        hour.reviseClosed(true);
        assertThat(hour.isClosed()).isTrue();
    }
}

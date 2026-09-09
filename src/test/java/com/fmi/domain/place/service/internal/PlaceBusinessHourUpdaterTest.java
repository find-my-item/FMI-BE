package com.fmi.domain.place.service.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.PlaceBusinessHour;
import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceBusinessHourUpdater")
class PlaceBusinessHourUpdaterTest {
    private final PlaceBusinessHourUpdater updater = new PlaceBusinessHourUpdater();

    @Nested
    @DisplayName("영업시간을 갱신할 때")
    class DescribeUpdate {
        @Test
        @DisplayName("동일 행은 유지하고 누락 행은 삭제하며 삭제 행은 복구하고 새 구간은 생성한다")
        void itUpdatesEveryBusinessHourState() {
            // given
            Place place = Place.builder()
                    .name("Cafe")
                    .address("Address")
                    .latitude(37.5)
                    .longitude(127.0)
                    .station("Seongsu")
                    .stationDistanceMeters(100)
                    .type(PlaceType.CAFE)
                    .thumbnailUrl("thumb")
                    .build();
            PlaceBusinessHour maintained = PlaceBusinessHour.builder()
                    .dayOfWeek(DayOfWeek.MONDAY)
                    .closed(false)
                    .timeRange(new PlaceTimeRange(
                            PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))
                    .build();
            PlaceBusinessHour removed = PlaceBusinessHour.builder()
                    .dayOfWeek(DayOfWeek.TUESDAY)
                    .closed(false)
                    .timeRange(new PlaceTimeRange(
                            PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))
                    .build();
            PlaceBusinessHour restored = PlaceBusinessHour.builder()
                    .dayOfWeek(DayOfWeek.WEDNESDAY)
                    .closed(false)
                    .timeRange(new PlaceTimeRange(
                            PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))
                    .build();
            restored.delete(LocalDateTime.of(2026, 9, 1, 12, 0));
            place.addBusinessHour(maintained);
            place.addBusinessHour(removed);
            place.addBusinessHour(restored);
            List<PlaceDailySchedule> requested = List.of(
                    new PlaceDailySchedule(
                            DayOfWeek.MONDAY,
                            true,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))),
                    new PlaceDailySchedule(
                            DayOfWeek.WEDNESDAY,
                            true,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))),
                    new PlaceDailySchedule(
                            DayOfWeek.THURSDAY,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(11, 0), LocalTime.of(21, 0)))));
            LocalDateTime deletedAt = LocalDateTime.of(2026, 9, 10, 12, 0);

            // when
            updater.update(place, requested, deletedAt);

            // then
            assertThat(maintained.isActive()).isTrue();
            assertThat(maintained.isClosed()).isTrue();
            assertThat(removed.isDeleted()).isTrue();
            assertThat(removed.getDeletedAt()).isEqualTo(deletedAt);
            assertThat(restored.isActive()).isTrue();
            assertThat(restored.getDeletedAt()).isNull();
            assertThat(restored.isClosed()).isTrue();
            assertThat(place.getBusinessHours()).hasSize(4);
            assertThat(place.getBusinessHours()).anySatisfy(hour -> {
                assertThat(hour.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
                assertThat(hour.getTimeRange().getStartTime()).isEqualTo(LocalTime.of(11, 0));
                assertThat(hour.getPlace()).isSameAs(place);
                assertThat(hour.isActive()).isTrue();
            });
        }
    }
}

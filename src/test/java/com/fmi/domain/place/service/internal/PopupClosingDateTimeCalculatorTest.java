package com.fmi.domain.place.service.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PopupClosingDateTimeCalculator")
class PopupClosingDateTimeCalculatorTest {
    private final PopupClosingDateTimeCalculator calculator = new PopupClosingDateTimeCalculator();

    @Nested
    @DisplayName("노출 마감을 계산할 때")
    class DescribeCalculate {
        @Nested
        @DisplayName("종료일에 여러 BUSINESS 구간이 있으면")
        class ContextWithMultipleBusinessRanges {
            @Test
            @DisplayName("가장 늦은 마감 시각을 반환한다")
            void itReturnsLatestClosingTime() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 14))
                        .endDate(LocalDate.of(2026, 9, 20))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.SUNDAY
                                        ? List.of(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 0)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(15, 0),
                                                        LocalTime.of(22, 0)))
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                LocalDateTime exposureEnd = calculator.calculate(period, dailySchedules);

                // then
                assertThat(exposureEnd).isEqualTo(LocalDateTime.of(2026, 9, 20, 22, 0));
            }
        }

        @Nested
        @DisplayName("종료일의 마지막 영업이 자정을 넘으면")
        class ContextWithOvernightClosing {
            @Test
            @DisplayName("익일 실제 마감 시각을 반환한다")
            void itReturnsNextDayClosingTime() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 14))
                        .endDate(LocalDate.of(2026, 9, 20))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS,
                                        day == DayOfWeek.SUNDAY ? LocalTime.of(18, 0) : LocalTime.of(10, 0),
                                        day == DayOfWeek.SUNDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                        .toList();

                // when
                LocalDateTime exposureEnd = calculator.calculate(period, dailySchedules);

                // then
                assertThat(exposureEnd).isEqualTo(LocalDateTime.of(2026, 9, 21, 3, 0));
            }
        }

        @Nested
        @DisplayName("종료일에 24시간 영업하면")
        class ContextWithTwentyFourHourBusiness {
            @Test
            @DisplayName("익일 같은 시각을 마감으로 반환한다")
            void itReturnsSameTimeOnNextDay() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 14))
                        .endDate(LocalDate.of(2026, 9, 20))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS,
                                        LocalTime.of(10, 0),
                                        day == DayOfWeek.SUNDAY ? LocalTime.of(10, 0) : LocalTime.of(22, 0)))))
                        .toList();

                // when
                LocalDateTime exposureEnd = calculator.calculate(period, dailySchedules);

                // then
                assertThat(exposureEnd).isEqualTo(LocalDateTime.of(2026, 9, 21, 10, 0));
            }
        }

        @Nested
        @DisplayName("종료일이 정기 휴무이면")
        class ContextWithClosedEndDate {
            @Test
            @DisplayName("운영 기간 안의 마지막 비휴무일 마감을 반환한다")
            void itReturnsLastOpenDateClosingTime() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 14))
                        .endDate(LocalDate.of(2026, 9, 20))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                day == DayOfWeek.SUNDAY,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS,
                                        day == DayOfWeek.SATURDAY ? LocalTime.of(18, 0) : LocalTime.of(10, 0),
                                        day == DayOfWeek.SATURDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                        .toList();

                // when
                LocalDateTime exposureEnd = calculator.calculate(period, dailySchedules);

                // then
                assertThat(exposureEnd).isEqualTo(LocalDateTime.of(2026, 9, 20, 3, 0));
            }
        }
    }
}

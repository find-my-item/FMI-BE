package com.fmi.domain.place.service.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceOperationState;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceOperationStatus;
import com.fmi.domain.place.data.enums.PlaceType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceOperationStatusCalculator")
class PlaceOperationStatusCalculatorTest {

    private final PlaceOperationStatusCalculator calculator = new PlaceOperationStatusCalculator();

    @Nested
    @DisplayName("운영 상태를 계산할 때")
    class Calculate {

        @Test
        @DisplayName("정기 휴무일이면 전날 영업이 이어져도 휴무와 null 운영시간을 반환한다")
        void closedDayTakesPriorityOverYesterdayBusinessHours() {
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            day == DayOfWeek.SATURDAY,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS,
                                    day == DayOfWeek.FRIDAY ? LocalTime.of(18, 0) : LocalTime.of(10, 0),
                                    day == DayOfWeek.FRIDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                    .toList();

            PlaceOperationState result =
                    calculator.calculate(PlaceType.CAFE, null, schedules, LocalDateTime.of(2026, 9, 12, 2, 0));

            assertThat(result.status()).isEqualTo(PlaceOperationStatus.CLOSED);
            assertThat(result.todayBusinessHours()).isNull();
        }

        @Test
        @DisplayName("브레이크 타임 시작은 포함하고 종료는 제외한다")
        void usesHalfOpenBreakTimeRange() {
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(
                                    new PlaceTimeRange(
                                            PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)),
                                    new PlaceTimeRange(
                                            PlaceBusinessHourType.BREAK_TIME,
                                            LocalTime.of(15, 0),
                                            LocalTime.of(17, 0)))))
                    .toList();

            PlaceOperationState atBreakStart =
                    calculator.calculate(PlaceType.RESTAURANT, null, schedules, LocalDateTime.of(2026, 9, 10, 15, 0));
            PlaceOperationState atBreakEnd =
                    calculator.calculate(PlaceType.RESTAURANT, null, schedules, LocalDateTime.of(2026, 9, 10, 17, 0));

            assertThat(atBreakStart.status()).isEqualTo(PlaceOperationStatus.BREAK_TIME);
            assertThat(atBreakEnd.status()).isEqualTo(PlaceOperationStatus.OPEN);
        }

        @Test
        @DisplayName("전날 시작한 자정 이후 영업과 운영시간을 함께 반환한다")
        void includesOvernightRangesFromYesterday() {
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS,
                                    day == DayOfWeek.WEDNESDAY ? LocalTime.of(18, 0) : LocalTime.of(10, 0),
                                    day == DayOfWeek.WEDNESDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                    .toList();

            PlaceOperationState result =
                    calculator.calculate(PlaceType.CAFE, null, schedules, LocalDateTime.of(2026, 9, 10, 2, 59));

            assertThat(result.status()).isEqualTo(PlaceOperationStatus.OPEN);
            assertThat(result.todayBusinessHours()).hasSize(2);
            assertThat(result.todayBusinessHours().get(0).getStartTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("팝업 운영 시작일 전이면 영업시간 안이어도 오픈 예정이다")
        void popupBeforeStartDateIsUpcoming() {
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                    .startDate(LocalDate.of(2026, 9, 11))
                    .endDate(LocalDate.of(2026, 9, 20))
                    .build();

            PlaceOperationState result =
                    calculator.calculate(PlaceType.POPUP, period, schedules, LocalDateTime.of(2026, 9, 10, 12, 0));

            assertThat(result.status()).isEqualTo(PlaceOperationStatus.UPCOMING);
        }
    }
}

package com.fmi.domain.place.service.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceOperationPeriod;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceValidator")
class PlaceValidatorTest {
    private final PlaceValidator validator = new PlaceValidator();

    @Nested
    @DisplayName("장소 유형을 검증할 때")
    class DescribeValidateType {
        @Nested
        @DisplayName("기존 유형과 요청 유형이 다르면")
        class ContextWithDifferentType {
            @Test
            @DisplayName("INVALID_REQUEST 오류를 던진다")
            void itThrowsInvalidRequest() {
                // given
                PlaceType currentType = PlaceType.CAFE;
                PlaceType requestedType = PlaceType.POPUP;

                // when
                // then
                assertThatThrownBy(() -> validator.validateType(currentType, requestedType))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_REQUEST);
            }
        }
    }

    @Nested
    @DisplayName("운영 기간을 검증할 때")
    class DescribeValidateOperationPeriod {
        @Nested
        @DisplayName("POPUP의 시작일과 종료일이 같으면")
        class ContextWithSameStartAndEndDate {
            @Test
            @DisplayName("운영 기간을 허용한다")
            void itAcceptsOperationPeriod() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 10))
                        .endDate(LocalDate.of(2026, 9, 10))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatCode(() -> validator.validate(PlaceType.POPUP, period, dailySchedules))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("POPUP의 시작일이 종료일보다 늦으면")
        class ContextWithReversedPeriod {
            @Test
            @DisplayName("INVALID_REQUEST 오류를 던진다")
            void itThrowsInvalidRequest() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 11))
                        .endDate(LocalDate.of(2026, 9, 10))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.POPUP, period, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_REQUEST);
            }
        }

        @Nested
        @DisplayName("카페에 운영 기간이 있으면")
        class ContextWithCafeOperationPeriod {
            @Test
            @DisplayName("INVALID_REQUEST 오류를 던진다")
            void itThrowsInvalidRequest() {
                // given
                PlaceOperationPeriod period = PlaceOperationPeriod.builder()
                        .startDate(LocalDate.of(2026, 9, 10))
                        .endDate(LocalDate.of(2026, 9, 20))
                        .build();
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, period, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_REQUEST);
            }
        }
    }

    @Nested
    @DisplayName("주간 구성을 검증할 때")
    class DescribeValidateWeeklyComposition {
        @Nested
        @DisplayName("요일 하나가 누락되면")
        class ContextWithMissingDay {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .filter(day -> day != DayOfWeek.SUNDAY)
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("같은 요일이 중복되면")
        class ContextWithDuplicateDay {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day == DayOfWeek.SUNDAY ? DayOfWeek.MONDAY : day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("한 요일에 BUSINESS 구간이 없으면")
        class ContextWithoutBusinessRange {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        day == DayOfWeek.WEDNESDAY
                                                ? PlaceBusinessHourType.BREAK_TIME
                                                : PlaceBusinessHourType.BUSINESS,
                                        LocalTime.of(10, 0),
                                        LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("시간 구간에 null이 있으면")
        class ContextWithNullTimeRange {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.WEDNESDAY
                                        ? Arrays.asList(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(22, 0)),
                                                null)
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("모든 요일이 정기 휴무이면")
        class ContextWithEveryDayClosed {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                true,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }
    }

    @Nested
    @DisplayName("시간 구간을 검증할 때")
    class DescribeValidateTimeRanges {
        @Nested
        @DisplayName("같은 유형 구간의 경계가 맞닿으면")
        class ContextWithAdjacentRanges {
            @Test
            @DisplayName("겹치지 않은 일정으로 허용한다")
            void itAcceptsSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.MONDAY
                                        ? List.of(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 0)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(14, 0),
                                                        LocalTime.of(22, 0)))
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatCode(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("같은 유형 구간이 한 분 겹치면")
        class ContextWithOneMinuteOverlap {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.MONDAY
                                        ? List.of(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 1)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(14, 0),
                                                        LocalTime.of(22, 0)))
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("브레이크 타임이 BUSINESS 경계와 같으면")
        class ContextWithBreakOnBusinessBoundaries {
            @Test
            @DisplayName("완전히 포함된 일정으로 허용한다")
            void itAcceptsSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.MONDAY
                                        ? List.of(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 0)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BREAK_TIME,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 0)))
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatCode(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("브레이크 타임이 두 BUSINESS 구간에 걸치면")
        class ContextWithBreakAcrossBusinessRanges {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                day == DayOfWeek.MONDAY
                                        ? List.of(
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(10, 0),
                                                        LocalTime.of(14, 0)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BUSINESS,
                                                        LocalTime.of(15, 0),
                                                        LocalTime.of(22, 0)),
                                                new PlaceTimeRange(
                                                        PlaceBusinessHourType.BREAK_TIME,
                                                        LocalTime.of(13, 0),
                                                        LocalTime.of(16, 0)))
                                        : List.of(new PlaceTimeRange(
                                                PlaceBusinessHourType.BUSINESS,
                                                LocalTime.of(10, 0),
                                                LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("익일 투영 종료와 다음 요일 시작이 같으면")
        class ContextWithAdjacentProjectedRange {
            @Test
            @DisplayName("겹치지 않은 일정으로 허용한다")
            void itAcceptsSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS,
                                        day == DayOfWeek.MONDAY ? LocalTime.of(18, 0) : LocalTime.of(3, 0),
                                        day == DayOfWeek.MONDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatCode(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .doesNotThrowAnyException();
            }
        }

        @Nested
        @DisplayName("일요일 익일 투영과 월요일 구간이 한 분 겹치면")
        class ContextWithProjectedOverlapAcrossWeek {
            @Test
            @DisplayName("INVALID_SCHEDULE 오류를 던진다")
            void itThrowsInvalidSchedule() {
                // given
                List<PlaceDailySchedule> dailySchedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS,
                                        day == DayOfWeek.SUNDAY ? LocalTime.of(18, 0) : LocalTime.of(2, 59),
                                        day == DayOfWeek.SUNDAY ? LocalTime.of(3, 0) : LocalTime.of(22, 0)))))
                        .toList();

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }

        @Nested
        @DisplayName("월요일에 오전 10시부터 24시간 영업하면")
        class ContextWithTwentyFourHourRange {
            @Test
            @DisplayName("화요일 오전 9시 59분 시작 구간을 INVALID_SCHEDULE로 거부한다")
            void itRejectsOverlappingNextDayRange() {
                // given
                List<PlaceDailySchedule> dailySchedules = new ArrayList<>();
                for (DayOfWeek day : DayOfWeek.values()) {
                    LocalTime start = day == DayOfWeek.MONDAY
                            ? LocalTime.of(10, 0)
                            : day == DayOfWeek.TUESDAY ? LocalTime.of(9, 59) : LocalTime.of(10, 0);
                    LocalTime end = day == DayOfWeek.MONDAY ? LocalTime.of(10, 0) : LocalTime.of(22, 0);
                    dailySchedules.add(new PlaceDailySchedule(
                            day, false, List.of(new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, start, end))));
                }

                // when
                // then
                assertThatThrownBy(() -> validator.validate(PlaceType.CAFE, null, dailySchedules))
                        .isInstanceOf(GeneralException.class)
                        .extracting(exception -> ((GeneralException) exception).getCode())
                        .isEqualTo(PlaceErrorStatus.INVALID_SCHEDULE);
            }
        }
    }
}

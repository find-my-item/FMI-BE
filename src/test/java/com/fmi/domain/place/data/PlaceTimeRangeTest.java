package com.fmi.domain.place.data;

import static org.assertj.core.api.Assertions.assertThat;

import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("PlaceTimeRange")
class PlaceTimeRangeTest {
    @Nested
    @DisplayName("값을 비교할 때")
    class DescribeEquals {
        @Test
        @DisplayName("유형과 시작·종료 시각이 같으면 동등하다")
        void itIsEqualWithSameFields() {
            // given
            PlaceTimeRange first =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0));
            PlaceTimeRange second =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0));

            // when
            boolean equal = first.equals(second);

            // then
            assertThat(equal).isTrue();
        }
    }

    @Nested
    @DisplayName("겹침을 판단할 때")
    class DescribeOverlaps {
        @Test
        @DisplayName("앞 구간의 종료와 뒤 구간의 시작이 같으면 겹치지 않는다")
        void itDoesNotOverlapAtExclusiveEnd() {
            // given
            PlaceTimeRange first =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(14, 0));
            PlaceTimeRange second =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(14, 0), LocalTime.of(22, 0));

            // when
            boolean overlaps = first.overlaps(second);

            // then
            assertThat(overlaps).isFalse();
        }
    }

    @Nested
    @DisplayName("포함을 판단할 때")
    class DescribeContains {
        @Test
        @DisplayName("내부 구간의 시작과 종료가 외부 구간 경계와 같으면 포함한다")
        void itContainsRangeOnBoundaries() {
            // given
            PlaceTimeRange business =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(18, 0), LocalTime.of(3, 0));
            PlaceTimeRange breakTime =
                    new PlaceTimeRange(PlaceBusinessHourType.BREAK_TIME, LocalTime.of(18, 0), LocalTime.of(3, 0));

            // when
            boolean contains = business.contains(breakTime);

            // then
            assertThat(contains).isTrue();
        }
    }

    @Nested
    @DisplayName("마감 일시를 계산할 때")
    class DescribeClosingDateTime {
        @Test
        @DisplayName("시작과 종료 시각이 같은 24시간 구간이면 익일 같은 시각을 반환한다")
        void itReturnsSameTimeOnNextDay() {
            // given
            PlaceTimeRange range =
                    new PlaceTimeRange(PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(10, 0));

            // when
            LocalDateTime closingDateTime = range.closingDateTime(LocalDate.of(2026, 9, 20));

            // then
            assertThat(closingDateTime).isEqualTo(LocalDateTime.of(2026, 9, 21, 10, 0));
        }
    }
}

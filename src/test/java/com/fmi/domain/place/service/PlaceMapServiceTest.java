package com.fmi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceMapSearchResult;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.PlaceUpsertCommand;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.domain.place.repository.PlaceBusinessHourRepository;
import com.fmi.domain.place.repository.PlaceFavoriteRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.global.dto.UploadedImage;
import com.fmi.global.service.S3Service;
import com.fmi.support.IntegrationTestSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@DisplayName("PlaceService 지도 장소 조회")
class PlaceMapServiceTest extends IntegrationTestSupport {

    @Autowired
    private PlaceService placeService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceBusinessHourRepository placeBusinessHourRepository;

    @Autowired
    private PlaceFavoriteRepository placeFavoriteRepository;

    @Autowired
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        placeFavoriteRepository.deleteAll();
        placeBusinessHourRepository.deleteAll();
        placeRepository.deleteAll();
        reset(s3Service);
        when(s3Service.uploadWithThumbnail(anyList()))
                .thenReturn(List.of(new UploadedImage("original-url", "thumbnail-url")));
    }

    @Nested
    @DisplayName("지도 범위의 장소를 조회할 때")
    class DescribeGetMapPlaces {

        @Nested
        @DisplayName("범위 안에 장소가 10개보다 많으면")
        class ContextWithMoreThanTenPlaces {

            @Test
            @DisplayName("중심에서 가까운 순서로 10개를 반환하고 범위 내 전체 개수를 유지한다")
            void itReturnsNearestTenPlacesAndTotalCount() {
                // given
                List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();
                for (int index = 0; index < 11; index++) {
                    placeService.create(
                            new PlaceUpsertCommand(
                                    "지도 카페 " + index,
                                    "서울 성동구",
                                    37.5421 + index * 0.00001,
                                    127.0549,
                                    "성수역",
                                    100,
                                    PlaceType.CAFE,
                                    null,
                                    null,
                                    schedules),
                            new MockMultipartFile("thumbnail", "map-" + index + ".png", "image/png", new byte[] {1}));
                }
                placeService.create(
                        new PlaceUpsertCommand(
                                "범위 밖 카페",
                                "서울 성동구",
                                37.55,
                                127.0549,
                                "성수역",
                                100,
                                PlaceType.CAFE,
                                null,
                                null,
                                schedules),
                        new MockMultipartFile("thumbnail", "outside.png", "image/png", new byte[] {1}));

                // when
                PlaceMapSearchResult result = placeService.getMapPlaces(37.5421, 127.0549, 1, PlaceType.CAFE, null);

                // then
                assertThat(result.totalCount()).isEqualTo(11);
                assertThat(result.places()).hasSize(10);
                assertThat(result.places().get(0).name()).isEqualTo("지도 카페 0");
                assertThat(result.places()).extracting(place -> place.name()).doesNotContain("범위 밖 카페", "지도 카페 10");
            }
        }
    }

    @Nested
    @DisplayName("장소 동네 정보를 조회할 때")
    class DescribeGetPlaceSummary {

        @Nested
        @DisplayName("팝업 운영이 종료되었으면")
        class ContextWithExpiredPopup {

            @Test
            @DisplayName("PLACE404-NOT_FOUND를 반환한다")
            void itReturnsPlaceNotFound() {
                // given
                List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();
                Long placeId = placeService.create(
                        new PlaceUpsertCommand(
                                "종료된 팝업",
                                "서울 성동구",
                                37.5421,
                                127.0549,
                                "성수역",
                                100,
                                PlaceType.POPUP,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 9),
                                schedules),
                        new MockMultipartFile("thumbnail", "popup.png", "image/png", new byte[] {1}));

                // when & then
                assertThatThrownBy(() -> placeService.getPlaceSummary(placeId, null))
                        .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                                .isEqualTo(PlaceErrorStatus.NOT_FOUND));
            }
        }
    }
}

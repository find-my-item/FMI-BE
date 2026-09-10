package com.fmi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Role;
import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceFavorite;
import com.fmi.domain.place.data.PlaceSummary;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.PlaceUpsertCommand;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceOperationStatus;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.repository.PlaceBusinessHourRepository;
import com.fmi.domain.place.repository.PlaceFavoriteRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
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
@DisplayName("PlaceService 홈 장소 조회")
class PlaceHomeServiceTest extends IntegrationTestSupport {

    @Autowired
    private PlaceService placeService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceBusinessHourRepository placeBusinessHourRepository;

    @Autowired
    private PlaceFavoriteRepository placeFavoriteRepository;

    @Autowired
    private UserRepository userRepository;

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
    @DisplayName("홈 장소를 조회할 때")
    class DescribeGetHomePlaces {

        @Nested
        @DisplayName("노출 가능한 장소가 5개보다 많으면")
        class ContextWithMoreThanFiveVisiblePlaces {

            @Test
            @DisplayName("종료된 팝업은 제외하고 최신 장소를 5개까지 유형별로 반환한다")
            void itReturnsLatestFiveVisiblePlacesByType() {
                // given
                List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();
                for (int index = 1; index <= 6; index++) {
                    placeService.create(
                            new PlaceUpsertCommand(
                                    "카페 " + index,
                                    "서울 성동구",
                                    37.54,
                                    127.05,
                                    "성수역",
                                    100,
                                    PlaceType.CAFE,
                                    null,
                                    null,
                                    schedules),
                            new MockMultipartFile("thumbnail", "cafe-" + index + ".png", "image/png", new byte[] {1}));
                }
                Long expiredPopupId = placeService.create(
                        new PlaceUpsertCommand(
                                "종료 팝업",
                                "서울 성동구",
                                37.54,
                                127.05,
                                "성수역",
                                100,
                                PlaceType.POPUP,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 9),
                                schedules),
                        new MockMultipartFile("thumbnail", "expired.png", "image/png", new byte[] {1}));

                // when
                List<PlaceSummary> result = placeService.getHomePlaces(null, null);

                // then
                assertThat(result).hasSize(5);
                assertThat(result)
                        .extracting(PlaceSummary::name)
                        .containsExactly("카페 6", "카페 5", "카페 4", "카페 3", "카페 2");
                assertThat(result).extracting(PlaceSummary::placeId).doesNotContain(expiredPopupId);
                assertThat(result).allMatch(place -> place.operationState().status() == PlaceOperationStatus.OPEN);
                assertThat(result).allMatch(place -> !place.favorite());
            }
        }

        @Nested
        @DisplayName("로그인 사용자가 장소를 저장했으면")
        class ContextWithFavoritePlace {

            @Test
            @DisplayName("저장 여부를 true로 반환한다")
            void itReturnsFavoriteAsTrue() {
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
                                "저장한 카페", "서울 성동구", 37.54, 127.05, "성수역", 100, PlaceType.CAFE, null, null, schedules),
                        new MockMultipartFile("thumbnail", "favorite.png", "image/png", new byte[] {1}));
                User user = userRepository.save(User.builder()
                        .nickname("장소사용자")
                        .email("place-user@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                placeFavoriteRepository.save(PlaceFavorite.builder()
                        .userId(user.getId())
                        .placeId(placeId)
                        .build());

                // when
                List<PlaceSummary> result = placeService.getHomePlaces(null, user.getEmail());

                // then
                assertThat(result).singleElement().satisfies(place -> assertThat(place.favorite())
                        .isTrue());
            }
        }
    }
}

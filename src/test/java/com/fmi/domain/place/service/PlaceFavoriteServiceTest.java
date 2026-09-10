package com.fmi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fmi.domain.Enum.Role;
import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.PlaceBusinessHour;
import com.fmi.domain.place.data.PlaceFavorite;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.domain.place.repository.PlaceFavoriteRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.support.IntegrationTestSupport;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("PlaceFavoriteService")
class PlaceFavoriteServiceTest extends IntegrationTestSupport {

    @Autowired
    private PlaceFavoriteService placeFavoriteService;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceFavoriteRepository placeFavoriteRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        placeFavoriteRepository.deleteAll();
        placeRepository.deleteAll();
    }

    @Nested
    @DisplayName("가보고 싶은 장소를 저장할 때")
    class DescribeSave {

        @Nested
        @DisplayName("같은 장소를 반복해서 저장하면")
        class ContextWithRepeatedRequest {

            @Test
            @DisplayName("하나의 저장 상태를 유지하고 변경 시각을 갱신하지 않는다")
            void itKeepsOneFavoriteWithoutUpdatingTimestamp() {
                // given
                User user = userRepository.save(User.builder()
                        .nickname("즐겨찾기반복사용자")
                        .email("favorite-repeat@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                Place place = Place.builder()
                        .name("반복 저장 카페")
                        .address("서울 성동구")
                        .latitude(37.5421)
                        .longitude(127.0549)
                        .station("성수역")
                        .stationDistanceMeters(100)
                        .type(PlaceType.CAFE)
                        .thumbnailUrl("thumbnail-url")
                        .build();
                Arrays.stream(DayOfWeek.values())
                        .forEach(dayOfWeek -> place.addBusinessHour(PlaceBusinessHour.builder()
                                .dayOfWeek(dayOfWeek)
                                .closed(false)
                                .timeRange(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))
                                .build()));
                placeRepository.saveAndFlush(place);

                // when
                placeFavoriteService.save(place.getId(), user.getEmail());
                PlaceFavorite first = placeFavoriteRepository
                        .findByUserIdAndPlaceId(user.getId(), place.getId())
                        .orElseThrow();
                LocalDateTime firstUpdatedAt = first.getUpdatedAt();
                placeFavoriteService.save(place.getId(), user.getEmail());
                PlaceFavorite repeated = placeFavoriteRepository
                        .findByUserIdAndPlaceId(user.getId(), place.getId())
                        .orElseThrow();

                // then
                assertThat(placeFavoriteRepository.findAll())
                        .filteredOn(favorite -> favorite.getUserId().equals(user.getId())
                                && favorite.getPlaceId().equals(place.getId()))
                        .hasSize(1);
                assertThat(repeated.isFavorite()).isTrue();
                assertThat(repeated.getUpdatedAt()).isEqualTo(firstUpdatedAt);
            }
        }

        @Nested
        @DisplayName("두 요청이 같은 장소를 처음 저장하면")
        class ContextWithConcurrentFirstRequests {

            @Test
            @DisplayName("경쟁 오류 없이 하나의 저장 관계만 생성한다")
            void itCreatesOneFavoriteWithoutConflict() throws Exception {
                // given
                User user = userRepository.save(User.builder()
                        .nickname("즐겨찾기동시사용자")
                        .email("favorite-concurrent@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                Place place = Place.builder()
                        .name("동시 저장 카페")
                        .address("서울 성동구")
                        .latitude(37.5421)
                        .longitude(127.0549)
                        .station("성수역")
                        .stationDistanceMeters(100)
                        .type(PlaceType.CAFE)
                        .thumbnailUrl("thumbnail-url")
                        .build();
                Arrays.stream(DayOfWeek.values())
                        .forEach(dayOfWeek -> place.addBusinessHour(PlaceBusinessHour.builder()
                                .dayOfWeek(dayOfWeek)
                                .closed(false)
                                .timeRange(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))
                                .build()));
                placeRepository.saveAndFlush(place);
                CountDownLatch start = new CountDownLatch(1);

                // when
                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                    List<Future<?>> requests = List.of(
                            executor.submit(() -> {
                                start.await();
                                placeFavoriteService.save(place.getId(), user.getEmail());
                                return null;
                            }),
                            executor.submit(() -> {
                                start.await();
                                placeFavoriteService.save(place.getId(), user.getEmail());
                                return null;
                            }));
                    start.countDown();

                    // then
                    assertThatCode(() -> {
                                for (Future<?> request : requests) {
                                    request.get();
                                }
                            })
                            .doesNotThrowAnyException();
                } finally {
                    executor.shutdownNow();
                }
                assertThat(placeFavoriteRepository.findAll())
                        .filteredOn(favorite -> favorite.getUserId().equals(user.getId())
                                && favorite.getPlaceId().equals(place.getId()))
                        .hasSize(1);
            }
        }

        @Nested
        @DisplayName("삭제된 장소를 저장하려고 하면")
        class ContextWithDeletedPlace {

            @Test
            @DisplayName("PLACE404-NOT_FOUND를 반환한다")
            void itRejectsDeletedPlace() {
                // given
                User user = userRepository.save(User.builder()
                        .nickname("삭제장소저장사용자")
                        .email("favorite-deleted@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                Place place = Place.builder()
                        .name("삭제된 카페")
                        .address("서울 성동구")
                        .latitude(37.5421)
                        .longitude(127.0549)
                        .station("성수역")
                        .stationDistanceMeters(100)
                        .type(PlaceType.CAFE)
                        .thumbnailUrl("thumbnail-url")
                        .build();
                place.delete(LocalDateTime.of(2026, 9, 10, 12, 0));
                placeRepository.saveAndFlush(place);

                // when & then
                assertThatThrownBy(() -> placeFavoriteService.save(place.getId(), user.getEmail()))
                        .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                                .isEqualTo(PlaceErrorStatus.NOT_FOUND));
            }
        }
    }

    @Nested
    @DisplayName("가보고 싶은 장소 저장을 취소할 때")
    class DescribeCancel {

        @Nested
        @DisplayName("같은 취소를 반복하면")
        class ContextWithRepeatedRequest {

            @Test
            @DisplayName("취소 상태를 유지하고 두 번째 요청에서는 변경 시각을 갱신하지 않는다")
            void itKeepsCanceledStateWithoutUpdatingTimestampAgain() {
                // given
                User user = userRepository.save(User.builder()
                        .nickname("즐겨찾기취소사용자")
                        .email("favorite-cancel@test.com")
                        .password("password")
                        .role(Role.USER)
                        .build());
                Place place = placeRepository.saveAndFlush(Place.builder()
                        .name("취소 대상 카페")
                        .address("서울 성동구")
                        .latitude(37.5421)
                        .longitude(127.0549)
                        .station("성수역")
                        .stationDistanceMeters(100)
                        .type(PlaceType.CAFE)
                        .thumbnailUrl("thumbnail-url")
                        .build());
                placeFavoriteRepository.saveAndFlush(PlaceFavorite.builder()
                        .userId(user.getId())
                        .placeId(place.getId())
                        .build());

                // when
                placeFavoriteService.cancel(place.getId(), user.getEmail());
                PlaceFavorite canceled = placeFavoriteRepository
                        .findByUserIdAndPlaceId(user.getId(), place.getId())
                        .orElseThrow();
                LocalDateTime canceledAt = canceled.getUpdatedAt();
                placeFavoriteService.cancel(place.getId(), user.getEmail());
                PlaceFavorite repeated = placeFavoriteRepository
                        .findByUserIdAndPlaceId(user.getId(), place.getId())
                        .orElseThrow();

                // then
                assertThat(repeated.isFavorite()).isFalse();
                assertThat(repeated.getUpdatedAt()).isEqualTo(canceledAt);
            }
        }
    }
}

package com.fmi.domain.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.fmi.domain.place.data.Place;
import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.PlaceUpsertCommand;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.exception.PlaceErrorStatus;
import com.fmi.domain.place.repository.PlaceBusinessHourRepository;
import com.fmi.domain.place.repository.PlaceOperationPeriodRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.global.data.EntityStatus;
import com.fmi.global.dto.UploadedImage;
import com.fmi.global.service.S3Service;
import com.fmi.support.IntegrationTestSupport;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("PlaceService")
class PlaceServiceTest extends IntegrationTestSupport {

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private PlaceBusinessHourRepository placeBusinessHourRepository;

    @Autowired
    private PlaceOperationPeriodRepository placeOperationPeriodRepository;

    @Autowired
    private PlaceService placeService;

    @Autowired
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        placeBusinessHourRepository.deleteAll();
        placeRepository.deleteAll();
        reset(s3Service);
        when(s3Service.uploadWithThumbnail(anyList())).thenAnswer(invocation -> {
            List<MultipartFile> files = invocation.getArgument(0);
            String filename = files.get(0).getOriginalFilename();
            return List.of(
                    new UploadedImage("https://storage.test/original-" + filename, "https://storage.test/" + filename));
        });
    }

    @Nested
    @DisplayName("장소를 등록할 때")
    class DescribeCreate {

        @Test
        @DisplayName("장소와 7개 요일 영업시간을 한 transaction으로 저장한다")
        void itSavesPlaceAndWeeklyBusinessHoursInOneTransaction() {
            // given
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand command = new PlaceUpsertCommand(
                    "성수 카페", "서울 성동구", 37.54, 127.05, "성수역", 320, PlaceType.CAFE, null, null, schedules);

            // when
            Long placeId = placeService.create(
                    command, new MockMultipartFile("thumbnail", "thumbnail.png", "image/png", new byte[] {1}));

            // then
            Place place = placeRepository.findById(placeId).orElseThrow();
            assertThat(place.getThumbnailUrl()).isEqualTo("https://storage.test/thumbnail.png");
            assertThat(placeBusinessHourRepository.findAllByPlaceId(placeId)).hasSize(7);
        }

        @Test
        @DisplayName("DB 저장이 실패하면 장소와 영업시간을 롤백한다")
        void itRollsBackPlaceAndBusinessHoursWhenDatabaseSaveFails() {
            // given
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand command = new PlaceUpsertCommand(
                    "장소 이름 길이 제한을 넘기기 위해 의도적으로 충분히 길게 작성한 성수 카페 이름입니다 초과",
                    "서울 성동구",
                    37.54,
                    127.05,
                    "성수역",
                    320,
                    PlaceType.CAFE,
                    null,
                    null,
                    schedules);

            // when & then
            assertThatThrownBy(() -> placeService.create(
                            command, new MockMultipartFile("thumbnail", "thumbnail.png", "image/png", new byte[] {1})))
                    .isInstanceOf(RuntimeException.class);
            assertThat(placeRepository.count()).isZero();
            assertThat(placeBusinessHourRepository.count()).isZero();
        }
    }

    @Nested
    @DisplayName("장소를 수정할 때")
    class DescribeUpdate {

        @Test
        @DisplayName("새 썸네일을 저장하고 영업시간을 교체한다")
        void itStoresNewThumbnailAndReplacesBusinessHours() {
            // given
            List<PlaceDailySchedule> originalSchedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand original = new PlaceUpsertCommand(
                    "성수 카페", "서울 성동구", 37.54, 127.05, "성수역", 320, PlaceType.CAFE, null, null, originalSchedules);
            Long placeId = placeService.create(
                    original, new MockMultipartFile("thumbnail", "old.png", "image/png", new byte[] {1}));
            List<PlaceDailySchedule> revisedSchedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(11, 0), LocalTime.of(23, 0)))))
                    .toList();
            PlaceUpsertCommand revised = new PlaceUpsertCommand(
                    "수정 카페", "서울 성동구", 37.54, 127.05, "뚝섬역", 120, PlaceType.CAFE, null, null, revisedSchedules);

            // when
            placeService.update(
                    placeId, revised, new MockMultipartFile("thumbnail", "new.png", "image/png", new byte[] {2}));

            // then
            Place place = placeRepository.findById(placeId).orElseThrow();
            assertThat(place.getName()).isEqualTo("수정 카페");
            assertThat(place.getThumbnailUrl()).isEqualTo("https://storage.test/new.png");
            assertThat(placeBusinessHourRepository.findAllByPlaceId(placeId))
                    .filteredOn(businessHour -> businessHour.isActive())
                    .allMatch(businessHour ->
                            businessHour.getTimeRange().getStartTime().equals(LocalTime.of(11, 0)));
        }

        @Test
        @DisplayName("장소 유형을 바꾸려고 하면 PLACE400-INVALID_REQUEST를 반환한다")
        void itRejectsTypeChange() {
            // given
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand createCommand = new PlaceUpsertCommand(
                    "성수 카페", "서울 성동구", 37.54, 127.05, "성수역", 320, PlaceType.CAFE, null, null, schedules);
            Long placeId = placeService.create(
                    createCommand, new MockMultipartFile("thumbnail", "old.png", "image/png", new byte[] {1}));
            PlaceUpsertCommand updateCommand = new PlaceUpsertCommand(
                    "성수 팝업",
                    "서울 성동구",
                    37.54,
                    127.05,
                    "성수역",
                    320,
                    PlaceType.POPUP,
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 20),
                    schedules);

            // when & then
            assertThatThrownBy(() -> placeService.update(placeId, updateCommand, null))
                    .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                            .isEqualTo(PlaceErrorStatus.INVALID_REQUEST));
        }

        @Test
        @DisplayName("팝업 운영 기간을 수정하면 기존 행의 식별자를 유지한다")
        void itRevisesExistingPopupOperationPeriod() {
            // given
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand original = new PlaceUpsertCommand(
                    "성수 팝업",
                    "서울 성동구",
                    37.54,
                    127.05,
                    "성수역",
                    320,
                    PlaceType.POPUP,
                    LocalDate.of(2026, 9, 10),
                    LocalDate.of(2026, 9, 20),
                    schedules);
            Long placeId = placeService.create(
                    original, new MockMultipartFile("thumbnail", "old.png", "image/png", new byte[] {1}));
            Long operationPeriodId = placeOperationPeriodRepository
                    .findByPlaceId(placeId)
                    .orElseThrow()
                    .getId();
            PlaceUpsertCommand revised = new PlaceUpsertCommand(
                    "성수 팝업",
                    "서울 성동구",
                    37.54,
                    127.05,
                    "성수역",
                    320,
                    PlaceType.POPUP,
                    LocalDate.of(2026, 9, 12),
                    LocalDate.of(2026, 9, 25),
                    schedules);

            // when
            placeService.update(placeId, revised, null);

            // then
            var operationPeriod =
                    placeOperationPeriodRepository.findByPlaceId(placeId).orElseThrow();
            assertThat(operationPeriod.getId()).isEqualTo(operationPeriodId);
            assertThat(operationPeriod.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 12));
            assertThat(operationPeriod.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        }
    }

    @Nested
    @DisplayName("장소를 삭제하고 복구할 때")
    class DescribeDeleteAndRestore {

        @Test
        @DisplayName("장소 상태와 삭제 시각만 변경한 뒤 다시 활성화한다")
        void itChangesOnlyPlaceStatusAndRestoresIt() {
            // given
            List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                    .map(day -> new PlaceDailySchedule(
                            day,
                            false,
                            List.of(new PlaceTimeRange(
                                    PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                    .toList();
            PlaceUpsertCommand command = new PlaceUpsertCommand(
                    "성수 카페", "서울 성동구", 37.54, 127.05, "성수역", 320, PlaceType.CAFE, null, null, schedules);
            Long placeId = placeService.create(
                    command, new MockMultipartFile("thumbnail", "thumbnail.png", "image/png", new byte[] {1}));

            // when
            placeService.delete(placeId);

            // then
            Place deleted = placeRepository.findById(placeId).orElseThrow();
            assertThat(deleted.getEntityStatus()).isEqualTo(EntityStatus.DELETED);
            assertThat(deleted.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 12, 0));
            assertThat(placeBusinessHourRepository.findAllByPlaceId(placeId))
                    .allMatch(businessHour -> businessHour.getEntityStatus() == EntityStatus.ACTIVE);

            // when
            placeService.restore(placeId);

            // then
            Place restored = placeRepository.findById(placeId).orElseThrow();
            assertThat(restored.getEntityStatus()).isEqualTo(EntityStatus.ACTIVE);
            assertThat(restored.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("이미 삭제된 장소를 다시 삭제하면 PLACE409-ALREADY_DELETED를 반환한다")
        void itRejectsDeletingDeletedPlace() {
            // given
            Place place = Place.builder()
                    .name("성수 카페")
                    .address("서울 성동구")
                    .latitude(37.54)
                    .longitude(127.05)
                    .station("성수역")
                    .stationDistanceMeters(320)
                    .type(PlaceType.CAFE)
                    .thumbnailUrl("https://storage.test/thumbnail.png")
                    .build();
            Long placeId = placeRepository.save(place).getId();
            placeService.delete(placeId);

            // when & then
            assertThatThrownBy(() -> placeService.delete(placeId))
                    .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                            .isEqualTo(PlaceErrorStatus.ALREADY_DELETED));
        }

        @Test
        @DisplayName("이미 활성화된 장소를 복구하면 PLACE409-ALREADY_ACTIVE를 반환한다")
        void itRejectsRestoringActivePlace() {
            // given
            Place place = Place.builder()
                    .name("성수 카페")
                    .address("서울 성동구")
                    .latitude(37.54)
                    .longitude(127.05)
                    .station("성수역")
                    .stationDistanceMeters(320)
                    .type(PlaceType.CAFE)
                    .thumbnailUrl("https://storage.test/thumbnail.png")
                    .build();
            Long placeId = placeRepository.save(place).getId();

            // when & then
            assertThatThrownBy(() -> placeService.restore(placeId))
                    .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                            .isEqualTo(PlaceErrorStatus.ALREADY_ACTIVE));
        }

        @Test
        @DisplayName("없는 장소를 삭제하면 PLACE404-NOT_FOUND를 반환한다")
        void itReturnsNotFoundWhenPlaceDoesNotExist() {
            // when & then
            assertThatThrownBy(() -> placeService.delete(Long.MAX_VALUE))
                    .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                            .isEqualTo(PlaceErrorStatus.NOT_FOUND));
        }
    }
}

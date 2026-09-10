package com.fmi.domain.place.web.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fmi.domain.place.data.PlaceDailySchedule;
import com.fmi.domain.place.data.PlaceTimeRange;
import com.fmi.domain.place.data.PlaceUpsertCommand;
import com.fmi.domain.place.data.enums.PlaceBusinessHourType;
import com.fmi.domain.place.data.enums.PlaceType;
import com.fmi.domain.place.repository.PlaceBusinessHourRepository;
import com.fmi.domain.place.repository.PlaceFavoriteRepository;
import com.fmi.domain.place.repository.PlaceRepository;
import com.fmi.domain.place.service.PlaceService;
import com.fmi.global.dto.UploadedImage;
import com.fmi.global.service.S3Service;
import com.fmi.support.IntegrationTestSupport;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
@DisplayName("PlaceController")
class PlaceControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

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
    @DisplayName("홈 장소를 조회할 때")
    class DescribeGetHomePlaces {

        @Nested
        @DisplayName("로그인하지 않은 사용자이면")
        class ContextWithoutAuthentication {

            @Test
            @DisplayName("조회 요청을 허용한다")
            void itAllowsRequest() throws Exception {
                List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                day == DayOfWeek.THURSDAY,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();
                placeService.create(
                        new PlaceUpsertCommand(
                                "목요일 휴무 카페",
                                "서울 성동구",
                                37.54,
                                127.05,
                                "성수역",
                                100,
                                PlaceType.CAFE,
                                null,
                                null,
                                schedules),
                        new MockMultipartFile("thumbnail", "closed.png", "image/png", new byte[] {1}));

                // when & then
                mockMvc.perform(get("/places"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(
                                jsonPath("$.result.places[0].operationStatus").value("CLOSED"))
                        .andExpect(jsonPath("$.result.places[0].todayBusinessHours")
                                .value(nullValue()))
                        .andExpect(jsonPath("$.result.places[0].isFavorite").value(false));
            }
        }

        @Nested
        @DisplayName("지원하지 않는 장소 유형이면")
        class ContextWithInvalidPlaceType {

            @Test
            @DisplayName("400을 반환한다")
            void itReturnsBadRequest() throws Exception {
                // when & then
                mockMvc.perform(get("/places").queryParam("type", "INVALID")).andExpect(status().isBadRequest());
            }
        }
    }

    @Nested
    @DisplayName("가보고 싶은 장소를 변경할 때")
    class DescribeChangeFavorite {

        @Nested
        @DisplayName("로그인하지 않은 사용자이면")
        class ContextWithoutAuthentication {

            @Test
            @DisplayName("저장과 취소 요청에 401을 반환한다")
            void itReturnsUnauthorized() throws Exception {
                // when & then
                mockMvc.perform(post("/places/1/favorites")).andExpect(status().isUnauthorized());
                mockMvc.perform(delete("/places/1/favorites")).andExpect(status().isUnauthorized());
            }
        }
    }
}

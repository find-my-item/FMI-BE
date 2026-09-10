package com.fmi.domain.place.web.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
@DisplayName("MainPlaceController")
class MainPlaceControllerTest extends IntegrationTestSupport {

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
    @DisplayName("지도 범위의 장소를 조회할 때")
    class DescribeSearchLocation {

        @Nested
        @DisplayName("로그인하지 않은 사용자이면")
        class ContextWithoutAuthentication {

            @Test
            @DisplayName("조회 요청을 허용한다")
            void itAllowsRequest() throws Exception {
                // given
                List<PlaceDailySchedule> schedules = Arrays.stream(DayOfWeek.values())
                        .map(day -> new PlaceDailySchedule(
                                day,
                                false,
                                List.of(new PlaceTimeRange(
                                        PlaceBusinessHourType.BUSINESS, LocalTime.of(10, 0), LocalTime.of(22, 0)))))
                        .toList();
                placeService.create(
                        new PlaceUpsertCommand(
                                "지도 카페",
                                "서울 성동구",
                                37.5421,
                                127.0549,
                                "성수역",
                                100,
                                PlaceType.CAFE,
                                null,
                                null,
                                schedules),
                        new MockMultipartFile("thumbnail", "map.png", "image/png", new byte[] {1}));

                // when & then
                mockMvc.perform(get("/main/places/search-location")
                                .queryParam("latitude", "37.5421")
                                .queryParam("longitude", "127.0549")
                                .queryParam("type", "CAFE"))
                        .andExpect(status().isOk());
            }
        }
    }
}

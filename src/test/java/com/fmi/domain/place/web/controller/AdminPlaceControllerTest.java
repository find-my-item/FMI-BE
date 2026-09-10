package com.fmi.domain.place.web.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fmi.global.dto.UploadedImage;
import com.fmi.global.service.S3Service;
import com.fmi.support.IntegrationTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdminPlaceControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private S3Service s3Service;

    @Nested
    @DisplayName("POST /admin/places")
    class DescribeCreate {

        @Nested
        @DisplayName("관리자이면")
        class ContextWithAdmin {

            @Test
            @WithMockUser(roles = "ADMIN")
            @DisplayName("multipart 요청으로 장소를 등록한다")
            void itCreatesPlaceWithMultipartRequest() throws Exception {
                // given
                String requestJson = """
                    {
                      "name": "성수 카페",
                      "address": "서울특별시 성동구 연무장길 1",
                      "latitude": 37.5421,
                      "longitude": 127.0549,
                      "station": "성수역",
                      "stationDistanceMeters": 320,
                      "type": "CAFE",
                      "weeklySchedules": [
                        {"dayOfWeek":"MONDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"TUESDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"WEDNESDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"THURSDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"FRIDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"SATURDAY","isClosed":false,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]},
                        {"dayOfWeek":"SUNDAY","isClosed":true,"timeRanges":[{"type":"BUSINESS","startTime":"10:00","endTime":"22:00"}]}
                      ]
                    }
                    """;
                MockMultipartFile request = new MockMultipartFile(
                        "request",
                        "request.json",
                        MediaType.APPLICATION_JSON_VALUE,
                        requestJson.getBytes(StandardCharsets.UTF_8));
                MockMultipartFile thumbnail =
                        new MockMultipartFile("thumbnail", "thumbnail.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1});
                when(s3Service.uploadWithThumbnail(anyList()))
                        .thenReturn(List.of(new UploadedImage("original-url", "thumbnail-url")));

                // when & then
                mockMvc.perform(multipart("/admin/places").file(request).file(thumbnail))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.code").value("COMMON200"))
                        .andExpect(jsonPath("$.result.name").value("성수 카페"))
                        .andExpect(jsonPath("$.result.thumbnailUrl").value("thumbnail-url"))
                        .andExpect(jsonPath("$.result.weeklySchedules.length()").value(7));
            }
        }

        @Nested
        @DisplayName("일반 사용자이면")
        class ContextWithUser {

            @Test
            @WithMockUser(roles = "USER")
            @DisplayName("장소를 등록할 수 없다")
            void itReturnsForbidden() throws Exception {
                MockMultipartFile request = new MockMultipartFile(
                        "request",
                        "request.json",
                        MediaType.APPLICATION_JSON_VALUE,
                        "{}".getBytes(StandardCharsets.UTF_8));
                MockMultipartFile thumbnail =
                        new MockMultipartFile("thumbnail", "thumbnail.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1});

                // when & then
                mockMvc.perform(multipart("/admin/places").file(request).file(thumbnail))
                        .andExpect(status().isForbidden());
            }
        }
    }

    @Nested
    @DisplayName("관리자 장소를 조회할 때")
    class DescribeGetPlace {

        @Nested
        @DisplayName("로그인하지 않은 사용자이면")
        class ContextWithoutAuthentication {

            @Test
            @DisplayName("401을 반환한다")
            void itReturnsUnauthorized() throws Exception {
                // when & then
                mockMvc.perform(get("/admin/places/1")).andExpect(status().isUnauthorized());
            }
        }
    }
}

package com.fmi.domain.place.web.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fmi.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@DisplayName("MyFavoritePlaceController")
class MyFavoritePlaceControllerTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("가보고 싶은 장소 목록을 조회할 때")
    class DescribeGetFavorites {

        @Nested
        @DisplayName("로그인하지 않은 사용자이면")
        class ContextWithoutAuthentication {

            @Test
            @DisplayName("401을 반환한다")
            void itReturnsUnauthorized() throws Exception {
                // when & then
                mockMvc.perform(get("/users/me/favorite-places")).andExpect(status().isUnauthorized());
            }
        }
    }
}

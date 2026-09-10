package com.fmi.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthCookieResolverTest {

    private AuthCookieResolver authCookieResolver;

    @BeforeEach
    void setUp() {
        authCookieResolver = new AuthCookieResolver("access_token", "refresh_token");
    }

    @Nested
    @DisplayName("액세스 토큰 쿠키 조회")
    class FindAccessToken {

        @Test
        @DisplayName("요청에 쿠키가 없으면 빈 결과를 반환한다")
        void returnsEmptyWithoutCookies() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // when
            Optional<String> token = authCookieResolver.findAccessToken(request);

            // then
            assertThat(token).isEmpty();
        }

        @Test
        @DisplayName("여러 쿠키 중 액세스 토큰 쿠키 값을 반환한다")
        void returnsAccessTokenValue() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("other", "other-value"), new Cookie("access_token", "access-value"));

            // when
            Optional<String> token = authCookieResolver.findAccessToken(request);

            // then
            assertThat(token).contains("access-value");
        }

        @Test
        @DisplayName("액세스 토큰 쿠키 값이 비어 있으면 빈 문자열을 값으로 반환한다")
        void preservesEmptyValue() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("access_token", ""));

            // when
            Optional<String> token = authCookieResolver.findAccessToken(request);

            // then
            assertThat(token).contains("");
        }
    }

    @Nested
    @DisplayName("리프레시 토큰 쿠키 조회")
    class FindRefreshToken {

        @Test
        @DisplayName("여러 쿠키 중 리프레시 토큰 쿠키 값을 반환한다")
        void returnsRefreshTokenValue() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(
                    new Cookie("access_token", "access-value"), new Cookie("refresh_token", "refresh-value"));

            // when
            Optional<String> token = authCookieResolver.findRefreshToken(request);

            // then
            assertThat(token).contains("refresh-value");
        }

        @Test
        @DisplayName("리프레시 토큰 쿠키가 없으면 빈 결과를 반환한다")
        void returnsEmptyWithoutRefreshToken() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("access_token", "access-value"));

            // when
            Optional<String> token = authCookieResolver.findRefreshToken(request);

            // then
            assertThat(token).isEmpty();
        }
    }
}

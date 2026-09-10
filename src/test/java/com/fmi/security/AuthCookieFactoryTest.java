package com.fmi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthCookieFactoryTest {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    private AuthCookieFactory authCookieFactory;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        authCookieFactory = new AuthCookieFactory(true, "None", ".finditem.kr", "access_token", "refresh_token", clock);
    }

    @Nested
    @DisplayName("인증 쿠키 생성")
    class Create {

        @Test
        @DisplayName("액세스 쿠키와 리프레시 쿠키를 각각의 만료 시간으로 생성한다")
        void createsAccessAndRefreshCookies() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // when
            ResponseCookie accessCookie =
                    authCookieFactory.createAccessCookie(request, "access-value", Date.from(NOW.plusSeconds(900)));
            ResponseCookie refreshCookie =
                    authCookieFactory.createRefreshCookie(request, "refresh-value", Date.from(NOW.plusSeconds(1_200)));

            // then
            assertThat(accessCookie.getName()).isEqualTo("access_token");
            assertThat(accessCookie.getValue()).isEqualTo("access-value");
            assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(900));
            assertThat(refreshCookie.getName()).isEqualTo("refresh_token");
            assertThat(refreshCookie.getValue()).isEqualTo("refresh-value");
            assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ofSeconds(1_200));
        }
    }

    @Nested
    @DisplayName("Origin 기준 Domain 결정")
    class ResolveDomain {

        @Test
        @DisplayName("Origin이 localhost면 Domain을 설정하지 않는다")
        void keepsLocalhostHostOnly() {
            // given
            MockHttpServletRequest request = requestWithHeader("Origin", "http://localhost:3000");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isNull();
        }

        @Test
        @DisplayName("Origin이 부모 도메인의 서브도메인이면 부모 도메인을 설정한다")
        void usesParentDomainForSubdomain() {
            // given
            MockHttpServletRequest request = requestWithHeader("Origin", "https://release.finditem.kr");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isEqualTo(".finditem.kr");
        }

        @Test
        @DisplayName("Origin이 부모 도메인 자체면 부모 도메인을 설정한다")
        void usesParentDomainForBareDomain() {
            // given
            MockHttpServletRequest request = requestWithHeader("Origin", "https://finditem.kr");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isEqualTo(".finditem.kr");
        }

        @Test
        @DisplayName("부모 도메인을 가장한 도메인이면 Domain을 설정하지 않는다")
        void rejectsSpoofedDomain() {
            // given
            MockHttpServletRequest request = requestWithHeader("Origin", "https://finditem.kr.evil.com");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isNull();
        }

        @Test
        @DisplayName("Origin이 없으면 Referer를 사용한다")
        void fallsBackToReferer() {
            // given
            MockHttpServletRequest request = requestWithHeader("Referer", "https://release.finditem.kr/login");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isEqualTo(".finditem.kr");
        }

        @Test
        @DisplayName("Origin과 Referer가 없으면 Domain을 설정하지 않는다")
        void keepsCookieHostOnlyWithoutOriginAndReferer() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isNull();
        }

        @Test
        @DisplayName("부모 도메인 설정이 비어 있으면 Domain을 설정하지 않는다")
        void keepsCookieHostOnlyWhenParentDomainIsBlank() {
            // given
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            authCookieFactory = new AuthCookieFactory(true, "None", "", "access_token", "refresh_token", clock);
            MockHttpServletRequest request = requestWithHeader("Origin", "https://release.finditem.kr");

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.getDomain()).isNull();
        }
    }

    @Nested
    @DisplayName("쿠키 공통 속성")
    class CommonAttributes {

        @Test
        @DisplayName("HttpOnly, Secure, SameSite, Path를 설정값대로 적용한다")
        void appliesCommonAttributes() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // when
            ResponseCookie cookie = createAccessCookie(request);

            // then
            assertThat(cookie.isHttpOnly()).isTrue();
            assertThat(cookie.isSecure()).isTrue();
            assertThat(cookie.getSameSite()).isEqualTo("None");
            assertThat(cookie.getPath()).isEqualTo("/");
        }
    }

    @Nested
    @DisplayName("인증 쿠키 만료")
    class Expire {

        @Test
        @DisplayName("액세스 쿠키와 리프레시 쿠키를 빈 값과 Max-Age 0으로 생성한다")
        void expiresAccessAndRefreshCookies() {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();

            // when
            ResponseCookie accessCookie = authCookieFactory.expireAccessCookie(request);
            ResponseCookie refreshCookie = authCookieFactory.expireRefreshCookie(request);

            // then
            assertThat(accessCookie.getName()).isEqualTo("access_token");
            assertThat(accessCookie.getValue()).isEmpty();
            assertThat(accessCookie.getMaxAge()).isEqualTo(Duration.ZERO);
            assertThat(refreshCookie.getName()).isEqualTo("refresh_token");
            assertThat(refreshCookie.getValue()).isEmpty();
            assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("만료 쿠키에도 생성 쿠키와 같은 Domain 규칙을 적용한다")
        void appliesSameDomainRule() {
            // given
            MockHttpServletRequest request = requestWithHeader("Origin", "https://release.finditem.kr");

            // when
            ResponseCookie accessCookie = authCookieFactory.expireAccessCookie(request);
            ResponseCookie refreshCookie = authCookieFactory.expireRefreshCookie(request);

            // then
            assertThat(accessCookie.getDomain()).isEqualTo(".finditem.kr");
            assertThat(refreshCookie.getDomain()).isEqualTo(".finditem.kr");
        }
    }

    private ResponseCookie createAccessCookie(MockHttpServletRequest request) {
        return authCookieFactory.createAccessCookie(request, "access-value", Date.from(NOW.plusSeconds(900)));
    }

    private MockHttpServletRequest requestWithHeader(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(name, value);
        return request;
    }
}

package com.fmi.global.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fmi.security.AuthCookieResolver;
import com.fmi.security.JwtTokenProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.WebSocketHandler;

class CustomHandshakeInterceptorTest {

    private JwtTokenProvider jwtTokenProvider;
    private AuthCookieResolver authCookieResolver;
    private CustomHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authCookieResolver = mock(AuthCookieResolver.class);
        interceptor = new CustomHandshakeInterceptor(jwtTokenProvider, authCookieResolver);
    }

    @Nested
    @DisplayName("WebSocket 인증")
    class AuthenticateWebSocket {

        @Test
        @DisplayName("액세스 토큰 쿠키가 없으면 handshake를 거부한다")
        void rejectsHandshakeWithoutAccessToken() {
            // given
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
            ServerHttpResponse response = mock(ServerHttpResponse.class);
            when(authCookieResolver.findAccessToken(servletRequest)).thenReturn(Optional.empty());

            // when
            boolean accepted =
                    interceptor.beforeHandshake(request, response, mock(WebSocketHandler.class), new HashMap<>());

            // then
            assertThat(accepted).isFalse();
            verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("유효한 액세스 토큰 쿠키가 있으면 인증 정보를 저장한다")
        void storesAuthenticationWithValidAccessToken() {
            // given
            MockHttpServletRequest servletRequest = new MockHttpServletRequest();
            ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
            Map<String, Object> attributes = new HashMap<>();
            when(authCookieResolver.findAccessToken(servletRequest)).thenReturn(Optional.of("access-token"));
            when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
            when(jwtTokenProvider.getUserId("access-token")).thenReturn(1L);
            when(jwtTokenProvider.getRole("access-token")).thenReturn("ROLE_USER");

            // when
            boolean accepted = interceptor.beforeHandshake(
                    request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);

            // then
            assertThat(accepted).isTrue();
            assertThat(attributes.get("AUTH")).isInstanceOf(Authentication.class);
            Authentication authentication = (Authentication) attributes.get("AUTH");
            assertThat(authentication.getPrincipal()).isEqualTo("1");
            assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        }
    }
}

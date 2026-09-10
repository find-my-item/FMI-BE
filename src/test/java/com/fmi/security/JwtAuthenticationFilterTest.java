package com.fmi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private UserDetailsService userDetailsService;
    private AuthCookieResolver authCookieResolver;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(UserDetailsService.class);
        authCookieResolver = mock(AuthCookieResolver.class);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, authCookieResolver);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("인증 토큰 조회")
    class ResolveAuthenticationToken {

        @Test
        @DisplayName("액세스 토큰 쿠키가 있으면 Authorization 헤더보다 먼저 사용한다")
        void prefersAccessTokenCookie() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer header-token");
            UserDetails userDetails = userDetails("member@finditem.kr");
            when(authCookieResolver.findAccessToken(request)).thenReturn(Optional.of("cookie-token"));
            when(tokenProvider.validateToken("cookie-token")).thenReturn(true);
            when(tokenProvider.getSubject("cookie-token")).thenReturn("member@finditem.kr");
            when(userDetailsService.loadUserByUsername("member@finditem.kr")).thenReturn(userDetails);

            // when
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

            // then
            verify(tokenProvider).validateToken("cookie-token");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(userDetails);
        }

        @Test
        @DisplayName("액세스 토큰 쿠키가 없으면 Bearer 토큰을 사용한다")
        void fallsBackToBearerToken() throws Exception {
            // given
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("Authorization", "Bearer header-token");
            UserDetails userDetails = userDetails("member@finditem.kr");
            when(authCookieResolver.findAccessToken(request)).thenReturn(Optional.empty());
            when(tokenProvider.validateToken("header-token")).thenReturn(true);
            when(tokenProvider.getSubject("header-token")).thenReturn("member@finditem.kr");
            when(userDetailsService.loadUserByUsername("member@finditem.kr")).thenReturn(userDetails);

            // when
            filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

            // then
            verify(tokenProvider).validateToken("header-token");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                    .isEqualTo(userDetails);
        }
    }

    private UserDetails userDetails(String username) {
        return User.withUsername(username)
                .password("password")
                .authorities("ROLE_USER")
                .build();
    }
}

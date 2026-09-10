package com.fmi.domain.auth.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.auth.service.SocialLoginService;
import com.fmi.domain.auth.service.TokenIssuer;
import com.fmi.domain.auth.web.dto.AppleLoginRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.domain.user.data.User;
import com.fmi.external.oauth.apple.AppleOAuthClient;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.security.AuthCookieFactory;
import java.time.Instant;
import java.util.Date;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AppleAuthControllerTest {

    @Mock
    private AppleOAuthClient appleOAuthService;

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @InjectMocks
    private AppleAuthController appleAuthController;

    @Test
    void Apple_로그인에_성공하면_두_JWT_쿠키와_로그인_응답을_반환한다() {
        // given
        AppleLoginRequest request = new AppleLoginRequest("apple-code", "dev");
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User user = User.builder()
                .id(1L)
                .email("apple_apple-subject@apple.local")
                .role(Role.USER)
                .build();
        when(appleOAuthService.exchangeCodeForSubject("apple-code", "dev")).thenReturn("apple-subject");
        when(socialLoginService.upsertUserFromApple("apple-subject"))
                .thenReturn(new SocialLoginService.AppleLoginResult(user));
        Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
        Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));
        when(tokenIssuer.issue(user, false, Provider.APPLE))
                .thenReturn(new TokenIssuer.IssuedTokens(
                        "access-token", accessExpiration, "refresh-token", refreshExpiration));
        when(authCookieFactory.createAccessCookie(httpRequest, "access-token", accessExpiration))
                .thenReturn(ResponseCookie.from("access_token", "access-token").build());
        when(authCookieFactory.createRefreshCookie(httpRequest, "refresh-token", refreshExpiration))
                .thenReturn(
                        ResponseCookie.from("refresh_token", "refresh-token").build());

        // when
        ResponseEntity<ApiResponse<LoginResponse>> response = appleAuthController.loginWithApple(request, httpRequest);

        // then
        verify(tokenIssuer).issue(user, false, Provider.APPLE);
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(response.getStatusCode().value()).isEqualTo(200);
            softly.assertThat(response.getHeaders().get("Set-Cookie"))
                    .containsExactly("access_token=access-token", "refresh_token=refresh-token");
            softly.assertThat(response.getBody().getResult().getUserId()).isEqualTo(1L);
            softly.assertThat(response.getBody().getResult().isTemporaryPassword())
                    .isFalse();
            softly.assertThat(response.getBody().getResult().isTermsAgreed()).isFalse();
        });
    }
}

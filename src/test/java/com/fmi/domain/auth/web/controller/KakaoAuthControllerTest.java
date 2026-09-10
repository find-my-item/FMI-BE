package com.fmi.domain.auth.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.auth.service.SocialLoginService;
import com.fmi.domain.auth.service.TokenIssuer;
import com.fmi.domain.auth.web.dto.KakaoLoginRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.domain.user.data.User;
import com.fmi.external.oauth.kakao.KakaoOAuthClient;
import com.fmi.external.oauth.kakao.KakaoOAuthClient.KakaoToken;
import com.fmi.external.oauth.kakao.KakaoOAuthClient.KakaoUser;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.security.AuthCookieFactory;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class KakaoAuthControllerTest {

    @Mock
    private KakaoOAuthClient kakaoOAuthService;

    @Mock
    private SocialLoginService socialLoginService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @InjectMocks
    private KakaoAuthController kakaoAuthController;

    @Nested
    @DisplayName("카카오 로그인")
    class LoginWithKakao {

        @Nested
        @DisplayName("카카오 OAuth 연동이 성공하면")
        class WithSuccessfulKakaoOAuth {

            @Test
            @DisplayName("KAKAO 제공자 클레임과 두 쿠키를 반환한다")
            void returnsProviderClaimAndCookies() {
                // given
                String email = "member@finditem.kr";
                User localUser = User.builder()
                        .id(1L)
                        .email(email)
                        .role(Role.USER)
                        .privacyPolicyAgreed(true)
                        .termsOfServiceAgreed(true)
                        .build();
                KakaoLoginRequest request = new KakaoLoginRequest("authorization-code", "dev");
                MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
                Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));
                KakaoToken kakaoToken = new KakaoToken("bearer", "kakao-access-token", 300, null, null, null);
                KakaoUser kakaoUser = new KakaoUser(
                        100L,
                        new KakaoUser.KakaoAccount(
                                email, new KakaoUser.KakaoAccount.Profile("카카오토끼", "https://example.com/profile.png")),
                        null);

                when(kakaoOAuthService.exchangeCodeForToken("authorization-code", "dev"))
                        .thenReturn(kakaoToken);
                when(kakaoOAuthService.getUserInfo("kakao-access-token")).thenReturn(kakaoUser);
                when(socialLoginService.upsertUserFromKakao(100L, email, "카카오토끼", "https://example.com/profile.png"))
                        .thenReturn(new SocialLoginService.KakaoLoginResult(localUser));
                when(tokenIssuer.issue(localUser, false, Provider.KAKAO))
                        .thenReturn(new TokenIssuer.IssuedTokens(
                                "access-token", accessExpiration, "refresh-token", refreshExpiration));
                when(authCookieFactory.createAccessCookie(httpRequest, "access-token", accessExpiration))
                        .thenReturn(ResponseCookie.from("access_token", "access-token")
                                .build());
                when(authCookieFactory.createRefreshCookie(httpRequest, "refresh-token", refreshExpiration))
                        .thenReturn(ResponseCookie.from("refresh_token", "refresh-token")
                                .build());

                // when
                ResponseEntity<ApiResponse<LoginResponse>> response =
                        kakaoAuthController.loginWithKakao(request, httpRequest);

                // then
                verify(tokenIssuer).issue(localUser, false, Provider.KAKAO);
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getHeaders().get("Set-Cookie"))
                        .containsExactly("access_token=access-token", "refresh_token=refresh-token");
                assertThat(response.getBody().getResult()).isEqualTo(new LoginResponse(1L, false, true));
            }
        }
    }
}

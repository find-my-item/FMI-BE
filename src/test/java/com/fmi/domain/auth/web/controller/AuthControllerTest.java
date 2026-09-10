package com.fmi.domain.auth.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Role;
import com.fmi.domain.Enum.WithdrawalReason;
import com.fmi.domain.auth.service.AuthService;
import com.fmi.domain.auth.service.PasswordService;
import com.fmi.domain.auth.service.TokenIssuer;
import com.fmi.domain.auth.service.WithdrawalService;
import com.fmi.domain.auth.web.dto.AccountDeleteRequest;
import com.fmi.domain.auth.web.dto.LoginRequest;
import com.fmi.domain.auth.web.dto.PasswordChangeRequest;
import com.fmi.domain.auth.web.dto.PasswordVerifyRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.service.NicknameService;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.security.AuthCookieFactory;
import com.fmi.security.AuthCookieResolver;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private NicknameService nicknameService;

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private PasswordService passwordService;

    @Mock
    private WithdrawalService withdrawalService;

    @Mock
    private AuthCookieFactory authCookieFactory;

    @Mock
    private AuthCookieResolver authCookieResolver;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private AuthController authController;

    @Nested
    @DisplayName("토큰 갱신")
    class Refresh {

        @Nested
        @DisplayName("리프레시 토큰 쿠키가 없으면")
        class WithoutRefreshCookie {

            @Test
            @DisplayName("기존 오류 코드와 메시지로 401 응답을 반환한다")
            void returnsUnauthorizedResponse() {
                // given
                MockHttpServletRequest request = new MockHttpServletRequest();
                when(authCookieResolver.findRefreshToken(request)).thenReturn(Optional.empty());

                // when
                ResponseEntity<ApiResponse<LoginResponse>> response = authController.refresh(request);

                // then
                assertThat(response.getStatusCode().value()).isEqualTo(401);
                assertThat(response.getBody().getCode()).isEqualTo("AUTH401-INVALID_REFRESH");
                assertThat(response.getBody().getMessage()).isEqualTo("리프레시 토큰 없음");
                verifyNoInteractions(tokenIssuer);
            }
        }

        @Nested
        @DisplayName("TokenIssuer가 갱신에 실패하면")
        class WithRefreshFailure {

            @ParameterizedTest
            @EnumSource(TokenIssuer.RefreshFailure.class)
            @DisplayName("기존 오류 메시지로 401 응답을 반환한다")
            void returnsExistingFailureMessage(TokenIssuer.RefreshFailure failure) {
                // given
                String refreshToken = "refresh-token";
                MockHttpServletRequest request = new MockHttpServletRequest();
                when(authCookieResolver.findRefreshToken(request)).thenReturn(Optional.of(refreshToken));
                when(tokenIssuer.refresh(refreshToken)).thenReturn(new TokenIssuer.RefreshResult(null, failure));

                // when
                ResponseEntity<ApiResponse<LoginResponse>> response = authController.refresh(request);

                // then
                assertThat(response.getStatusCode().value()).isEqualTo(401);
                assertThat(response.getBody().getMessage()).isEqualTo(refreshFailureMessage(failure));
            }
        }

        @Nested
        @DisplayName("유효한 갱신 토큰 쿠키가 있으면")
        class WithValidRefreshCookie {

            @Test
            @DisplayName("기존 JTI를 폐기한 뒤 제공자 클레임과 두 쿠키를 발급한다")
            void 유효한_refresh는_기존_JTI를_폐기한_뒤_provider_claim과_두_쿠키를_발급한다() {
                // given
                String oldRefreshToken = "old-refresh-token";
                String newAccessToken = "new-access-token";
                String newRefreshToken = "new-refresh-token";
                MockHttpServletRequest request = new MockHttpServletRequest();
                Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
                Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));

                when(authCookieResolver.findRefreshToken(request)).thenReturn(Optional.of(oldRefreshToken));
                when(tokenIssuer.refresh(oldRefreshToken))
                        .thenReturn(new TokenIssuer.RefreshResult(
                                new TokenIssuer.IssuedTokens(
                                        newAccessToken, accessExpiration, newRefreshToken, refreshExpiration),
                                null));
                when(authCookieFactory.createAccessCookie(request, newAccessToken, accessExpiration))
                        .thenReturn(accessCookie(newAccessToken));
                when(authCookieFactory.createRefreshCookie(request, newRefreshToken, refreshExpiration))
                        .thenReturn(refreshCookie(newRefreshToken));

                // when
                ResponseEntity<ApiResponse<LoginResponse>> response = authController.refresh(request);

                // then
                verify(tokenIssuer).refresh(oldRefreshToken);
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getHeaders().get("Set-Cookie"))
                        .containsExactly("access_token=" + newAccessToken, "refresh_token=" + newRefreshToken);
                assertThat(response.getBody().getResult()).isEqualTo(new LoginResponse(null, false, true));
            }
        }
    }

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Nested
        @DisplayName("갱신 토큰 쿠키가 없으면")
        class WithoutRefreshCookie {

            @Test
            @DisplayName("성공 응답과 두 만료 쿠키를 반환한다")
            void refresh_쿠키가_없어도_logout은_성공하고_두_쿠키를_만료한다() {
                // given
                MockHttpServletRequest request = new MockHttpServletRequest();
                when(authCookieResolver.findRefreshToken(request)).thenReturn(Optional.empty());
                when(authCookieFactory.expireAccessCookie(request))
                        .thenReturn(ResponseCookie.from("access_token", "")
                                .maxAge(0)
                                .build());
                when(authCookieFactory.expireRefreshCookie(request))
                        .thenReturn(ResponseCookie.from("refresh_token", "")
                                .maxAge(0)
                                .build());

                // when
                ResponseEntity<ApiResponse<String>> response = authController.logout(request);

                // then
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getHeaders().get("Set-Cookie"))
                        .containsExactly(
                                "access_token=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
                                "refresh_token=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT");
                assertThat(response.getBody().getResult()).isEqualTo("OK");
                verifyNoInteractions(tokenIssuer);
            }
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Nested
        @DisplayName("임시 비밀번호 인증 결과면")
        class WithTemporaryPassword {

            @Test
            @DisplayName("임시 클레임과 두 쿠키를 발급한다")
            void 임시_비밀번호_로그인은_임시_claim과_두_쿠키를_발급한다() {
                // given
                String email = "member@finditem.kr";
                String accessToken = "access-token";
                String refreshToken = "refresh-token";
                User user = User.builder().id(1L).email(email).role(Role.USER).build();
                LoginRequest request = new LoginRequest();
                request.setEmail(email);
                request.setPassword("temporary-password");
                MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
                Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));

                when(authService.authenticate(email, "temporary-password"))
                        .thenReturn(new AuthService.AuthenticateResult(user, true));
                when(tokenIssuer.issue(user, true, null))
                        .thenReturn(new TokenIssuer.IssuedTokens(
                                accessToken, accessExpiration, refreshToken, refreshExpiration));
                when(authCookieFactory.createAccessCookie(httpRequest, accessToken, accessExpiration))
                        .thenReturn(accessCookie(accessToken));
                when(authCookieFactory.createRefreshCookie(httpRequest, refreshToken, refreshExpiration))
                        .thenReturn(refreshCookie(refreshToken));

                // when
                ResponseEntity<ApiResponse<LoginResponse>> response = authController.login(request, httpRequest);

                // then
                verify(tokenIssuer).issue(user, true, null);
                assertThat(response.getStatusCode().value()).isEqualTo(200);
                assertThat(response.getHeaders().get("Set-Cookie"))
                        .containsExactly("access_token=" + accessToken, "refresh_token=" + refreshToken);
                assertThat(response.getBody().getResult()).isEqualTo(new LoginResponse(1L, true, true));
            }
        }
    }

    @Nested
    @DisplayName("비밀번호 검증")
    class VerifyPassword {

        @Test
        @DisplayName("기존 비밀번호 검증 경로를 유지한다")
        void keepsPasswordVerifyPath() throws NoSuchMethodException {
            // given
            Class<AuthController> controllerType = AuthController.class;

            // when
            PostMapping mapping = controllerType
                    .getMethod("verifyPassword", UserDetails.class, PasswordVerifyRequest.class)
                    .getAnnotation(PostMapping.class);

            // then
            assertThat(mapping.value()).containsExactly("/users/me/password/verify");
        }

        @Test
        @DisplayName("현재 비밀번호 검증을 비밀번호 유스케이스에 전달한다")
        void verifiesCurrentPassword() {
            // given
            String email = "member@finditem.kr";
            PasswordVerifyRequest request = new PasswordVerifyRequest();
            request.setCurrentPassword("CurrentPassword1!");
            when(userDetails.getUsername()).thenReturn(email);

            // when
            authController.verifyPassword(userDetails, request);

            // then
            verify(passwordService).verify(email, request);
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("기존 비밀번호 변경 경로를 유지한다")
        void keepsPasswordChangePath() throws NoSuchMethodException {
            // given
            Class<AuthController> controllerType = AuthController.class;

            // when
            PatchMapping mapping = controllerType
                    .getMethod("changePassword", UserDetails.class, PasswordChangeRequest.class)
                    .getAnnotation(PatchMapping.class);

            // then
            assertThat(mapping.value()).containsExactly("/users/me/password");
        }

        @Test
        @DisplayName("요청 값을 해체해 비밀번호 유스케이스에 전달한다")
        void changesPassword() {
            // given
            String email = "member@finditem.kr";
            PasswordChangeRequest request = new PasswordChangeRequest();
            request.setNewPassword("NewPassword1!");
            request.setNewPasswordConfirm("NewPassword1!");
            when(userDetails.getUsername()).thenReturn(email);

            // when
            authController.changePassword(userDetails, request);

            // then
            verify(passwordService).change(email, "NewPassword1!", "NewPassword1!");
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class DeleteAccount {

        @Test
        @DisplayName("기존 회원 탈퇴 경로를 유지한다")
        void keepsAccountDeletePath() throws NoSuchMethodException {
            // given
            Class<AuthController> controllerType = AuthController.class;

            // when
            DeleteMapping mapping = controllerType
                    .getMethod(
                            "deleteAccount",
                            UserDetails.class,
                            AccountDeleteRequest.class,
                            jakarta.servlet.http.HttpServletRequest.class)
                    .getAnnotation(DeleteMapping.class);

            // then
            assertThat(mapping.value()).containsExactly("/users/me");
        }

        @Nested
        @DisplayName("탈퇴 요청이 유효하면")
        class WithValidRequest {

            @Test
            @DisplayName("탈퇴를 처리하고 액세스 쿠키와 리프레시 쿠키를 순서대로 만료한다")
            void deletesAccountAndExpiresCookiesInOrder() {
                // given
                String email = "member@finditem.kr";
                AccountDeleteRequest request = new AccountDeleteRequest();
                request.setReasons(List.of(WithdrawalReason.NOT_USING));
                MockHttpServletRequest httpRequest = new MockHttpServletRequest();
                when(userDetails.getUsername()).thenReturn(email);
                when(authCookieFactory.expireAccessCookie(httpRequest)).thenReturn(accessCookie(""));
                when(authCookieFactory.expireRefreshCookie(httpRequest)).thenReturn(refreshCookie(""));

                // when
                ResponseEntity<ApiResponse<Void>> response =
                        authController.deleteAccount(userDetails, request, httpRequest);

                // then
                verify(withdrawalService).delete(email, request);
                assertThat(response.getHeaders().get("Set-Cookie")).containsExactly("access_token=", "refresh_token=");
            }
        }
    }

    private static ResponseCookie accessCookie(String token) {
        return ResponseCookie.from("access_token", token).build();
    }

    private static ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from("refresh_token", token).build();
    }

    private static String refreshFailureMessage(TokenIssuer.RefreshFailure failure) {
        return switch (failure) {
            case INVALID_TOKEN -> "유효하지 않은 리프레시";
            case MISSING_JTI -> "유효하지 않은 리프레시(jti 없음)";
            case HASH_MISMATCH -> "유효하지 않은 리프레시(대조 실패)";
            case USER_NOT_FOUND -> "유효하지 않은 리프레시(사용자 없음)";
        };
    }
}

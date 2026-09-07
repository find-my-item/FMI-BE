package com.fmi.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.auth.data.SocialAccounts;
import com.fmi.domain.auth.repository.SocialAccountsRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.JwtTokenProvider;
import com.fmi.security.RefreshTokenStore;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountsRepository socialAccountsRepository;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    @Captor
    private ArgumentCaptor<String> refreshHashCaptor;

    @InjectMocks
    private TokenIssuer tokenIssuer;

    @Nested
    @DisplayName("토큰 발급")
    class Issue {

        @Nested
        @DisplayName("카카오 로그인 사용자이면")
        class WithKakaoUser {

            @Test
            @DisplayName("제공자 claim과 refresh hash를 저장한다")
            void issuesTokensAndStoresRefreshHash() {
                // given
                User user = User.builder()
                        .id(1L)
                        .email("member@finditem.kr")
                        .role(Role.USER)
                        .build();
                Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
                Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));
                when(jwtTokenProvider.createAccessToken(eq(user.getEmail()), claimsCaptor.capture()))
                        .thenReturn("access-token");
                when(jwtTokenProvider.createRefreshToken(eq(user.getEmail()), anyString()))
                        .thenReturn("refresh-token");
                when(jwtTokenProvider.getExpiration("access-token")).thenReturn(accessExpiration);
                when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(refreshExpiration);

                // when
                TokenIssuer.IssuedTokens issuedTokens = tokenIssuer.issue(user, false, Provider.KAKAO);

                // then
                verify(refreshTokenStore)
                        .issue(
                                anyString(),
                                eq(user.getEmail()),
                                refreshHashCaptor.capture(),
                                eq(refreshExpiration.toInstant()));
                assertThat(issuedTokens.accessToken()).isEqualTo("access-token");
                assertThat(issuedTokens.accessExpiration()).isEqualTo(accessExpiration);
                assertThat(issuedTokens.refreshToken()).isEqualTo("refresh-token");
                assertThat(issuedTokens.refreshExpiration()).isEqualTo(refreshExpiration);
                assertThat(claimsCaptor.getValue())
                        .containsEntry("userId", 1L)
                        .containsEntry("role", "USER")
                        .containsEntry("provider", "KAKAO")
                        .containsEntry("purpose", "access");
                assertThat(refreshHashCaptor.getValue()).isEqualTo(sha256Hex("refresh-token"));
            }
        }
    }

    @Nested
    @DisplayName("토큰 갱신")
    class Refresh {

        @Nested
        @DisplayName("refresh token 검증에 실패하면")
        class WithInvalidRefreshToken {

            @Test
            @DisplayName("Redis 상태를 변경하지 않고 유효하지 않은 refresh token 예외를 던진다")
            void throwsExceptionWithoutChangingRedis() {
                // given
                String refreshToken = "refresh-token";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

                // when & then
                assertInvalidRefreshTokenWithoutChangingRedis(refreshToken);
            }

            @Test
            @DisplayName("JTI가 없으면 Redis 상태를 변경하지 않고 외부용 토큰 예외를 던진다")
            void throwsExceptionWhenJtiIsMissing() {
                // given
                String refreshToken = "refresh-token";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getSubject(refreshToken)).thenReturn("member@finditem.kr");
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn(null);

                // when & then
                assertInvalidRefreshTokenWithoutChangingRedis(refreshToken);
            }

            @Test
            @DisplayName("저장된 hash와 다르면 Redis 상태를 변경하지 않고 외부용 토큰 예외를 던진다")
            void throwsExceptionWhenHashDoesNotMatch() {
                // given
                String refreshToken = "refresh-token";
                String email = "member@finditem.kr";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getSubject(refreshToken)).thenReturn(email);
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn("refresh-jti");
                when(refreshTokenStore.validate(eq("refresh-jti"), anyString(), eq(email)))
                        .thenReturn(false);

                // when & then
                assertInvalidRefreshTokenWithoutChangingRedis(refreshToken);
            }

            @Test
            @DisplayName("사용자를 찾을 수 없으면 Redis 상태를 변경하지 않고 외부용 토큰 예외를 던진다")
            void throwsExceptionWhenUserDoesNotExist() {
                // given
                String refreshToken = "refresh-token";
                String email = "member@finditem.kr";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getSubject(refreshToken)).thenReturn(email);
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn("refresh-jti");
                when(refreshTokenStore.validate(eq("refresh-jti"), anyString(), eq(email)))
                        .thenReturn(true);
                when(userRepository.findByEmail(email)).thenReturn(java.util.Optional.empty());

                // when & then
                assertInvalidRefreshTokenWithoutChangingRedis(refreshToken);
            }
        }

        @Nested
        @DisplayName("유효한 refresh token과 활성 사용자가 있으면")
        class WithValidRefreshToken {

            @Test
            @DisplayName("기존 JTI를 폐기한 뒤 제공자 claim을 포함한 새 토큰을 발급한다")
            void rotatesTokensAfterRevokingOldJti() {
                // given
                String oldRefreshToken = "old-refresh-token";
                String oldJti = "old-jti";
                User user = User.builder()
                        .id(1L)
                        .email("member@finditem.kr")
                        .role(Role.USER)
                        .build();
                SocialAccounts socialAccount = SocialAccounts.builder()
                        .user(user)
                        .provider(Provider.KAKAO)
                        .build();
                Date accessExpiration = Date.from(Instant.now().plusSeconds(900));
                Date refreshExpiration = Date.from(Instant.now().plusSeconds(1_200));
                when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
                when(jwtTokenProvider.getSubject(oldRefreshToken)).thenReturn(user.getEmail());
                when(jwtTokenProvider.getJti(oldRefreshToken)).thenReturn(oldJti);
                when(refreshTokenStore.validate(eq(oldJti), anyString(), eq(user.getEmail())))
                        .thenReturn(true);
                when(userRepository.findByEmail(user.getEmail())).thenReturn(java.util.Optional.of(user));
                when(socialAccountsRepository.findByUser(user)).thenReturn(java.util.Optional.of(socialAccount));
                when(jwtTokenProvider.createAccessToken(eq(user.getEmail()), claimsCaptor.capture()))
                        .thenReturn("access-token");
                when(jwtTokenProvider.createRefreshToken(eq(user.getEmail()), anyString()))
                        .thenReturn("refresh-token");
                when(jwtTokenProvider.getExpiration("access-token")).thenReturn(accessExpiration);
                when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(refreshExpiration);

                // when
                TokenIssuer.IssuedTokens issuedTokens = tokenIssuer.refresh(oldRefreshToken);

                // then
                InOrder refreshStoreOrder = org.mockito.Mockito.inOrder(refreshTokenStore);
                refreshStoreOrder.verify(refreshTokenStore).validate(eq(oldJti), anyString(), eq(user.getEmail()));
                refreshStoreOrder.verify(refreshTokenStore).revoke(oldJti);
                refreshStoreOrder
                        .verify(refreshTokenStore)
                        .issue(anyString(), eq(user.getEmail()), anyString(), eq(refreshExpiration.toInstant()));
                assertThat(issuedTokens).isNotNull();
                assertThat(claimsCaptor.getValue()).containsEntry("provider", "KAKAO");
            }
        }
    }

    @Nested
    @DisplayName("토큰 폐기")
    class Revoke {

        @Nested
        @DisplayName("refresh token이 유효하지 않으면")
        class WithInvalidRefreshToken {

            @Test
            @DisplayName("저장된 토큰을 폐기하지 않는다")
            void doesNotRevokeToken() {
                // given
                String refreshToken = "refresh-token";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

                // when
                tokenIssuer.revoke(refreshToken);

                // then
                verifyNoInteractions(refreshTokenStore);
            }
        }

        @Nested
        @DisplayName("refresh token에 JTI가 없으면")
        class WithoutJti {

            @Test
            @DisplayName("저장된 토큰을 폐기하지 않는다")
            void doesNotRevokeToken() {
                // given
                String refreshToken = "refresh-token";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn(null);

                // when
                tokenIssuer.revoke(refreshToken);

                // then
                verifyNoInteractions(refreshTokenStore);
            }
        }

        @Nested
        @DisplayName("유효한 refresh token에 JTI가 있으면")
        class WithValidRefreshToken {

            @Test
            @DisplayName("해당 JTI를 폐기한다")
            void revokesJti() {
                // given
                String refreshToken = "refresh-token";
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn("refresh-jti");

                // when
                tokenIssuer.revoke(refreshToken);

                // then
                verify(refreshTokenStore).revoke("refresh-jti");
            }
        }
    }

    private void assertInvalidRefreshTokenWithoutChangingRedis(String refreshToken) {
        assertThatThrownBy(() -> tokenIssuer.refresh(refreshToken))
                .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorStatus._INVALID_REFRESH_TOKEN));
        verify(refreshTokenStore, never()).revoke(anyString());
        verify(refreshTokenStore, never()).issue(anyString(), anyString(), anyString(), any());
    }

    private static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

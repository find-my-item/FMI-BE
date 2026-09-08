package com.fmi.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.auth.data.IssuedTokens;
import com.fmi.domain.auth.data.SocialAccounts;
import com.fmi.domain.auth.repository.SocialAccountsRepository;
import com.fmi.domain.auth.service.internal.TokenIssuer;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.JwtTokenProvider;
import com.fmi.security.RefreshTokenStore;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final String REFRESH_TOKEN_HASH = "0eb17643d4e9261163783a420859c92c7d212fa9624106a12b510afbec266120";

    @Mock
    private TokenIssuer tokenIssuer;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountsRepository socialAccountsRepository;

    @InjectMocks
    private TokenService tokenService;

    @Nested
    @DisplayName("토큰 발급")
    class Issue {

        @Test
        @DisplayName("내부 발급기에 사용자와 로그인 정보를 전달한다")
        void delegatesIssuance() {
            // given
            User user = User.builder()
                    .id(1L)
                    .email("member@finditem.kr")
                    .role(Role.USER)
                    .build();
            IssuedTokens expected = issuedTokens();
            TokenIssuer.IssueResult issueResult = new TokenIssuer.IssueResult(expected, "new-jti");
            when(tokenIssuer.issue(user, true, Provider.KAKAO)).thenReturn(issueResult);

            // when
            IssuedTokens actual = tokenService.issue(user, true, Provider.KAKAO);

            // then
            assertThat(actual).isSameAs(expected);
            verify(refreshTokenStore)
                    .issue(
                            "new-jti",
                            user.getEmail(),
                            REFRESH_TOKEN_HASH,
                            expected.refreshExpiration().toInstant());
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
                when(refreshTokenStore.validate("refresh-jti", REFRESH_TOKEN_HASH, email))
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
                when(refreshTokenStore.validate("refresh-jti", REFRESH_TOKEN_HASH, email))
                        .thenReturn(true);
                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

                // when & then
                assertInvalidRefreshTokenWithoutChangingRedis(refreshToken);
            }
        }

        @Nested
        @DisplayName("유효한 refresh token과 활성 사용자가 있으면")
        class WithValidRefreshToken {

            @Test
            @DisplayName("기존 JTI를 폐기한 뒤 제공자 정보를 전달해 새 토큰을 발급한다")
            void rotatesTokensAfterRevokingOldJti() {
                // given
                String refreshToken = "refresh-token";
                String jti = "refresh-jti";
                User user = User.builder()
                        .id(1L)
                        .email("member@finditem.kr")
                        .role(Role.USER)
                        .build();
                SocialAccounts socialAccount = SocialAccounts.builder()
                        .user(user)
                        .provider(Provider.KAKAO)
                        .build();
                IssuedTokens expected = issuedTokens();
                TokenIssuer.IssueResult issueResult = new TokenIssuer.IssueResult(expected, "new-jti");
                when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
                when(jwtTokenProvider.getSubject(refreshToken)).thenReturn(user.getEmail());
                when(jwtTokenProvider.getJti(refreshToken)).thenReturn(jti);
                when(refreshTokenStore.validate(jti, REFRESH_TOKEN_HASH, user.getEmail()))
                        .thenReturn(true);
                when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
                when(socialAccountsRepository.findByUser(user)).thenReturn(Optional.of(socialAccount));
                when(tokenIssuer.issue(user, false, Provider.KAKAO)).thenReturn(issueResult);

                // when
                IssuedTokens actual = tokenService.refresh(refreshToken);

                // then
                InOrder refreshStoreOrder = org.mockito.Mockito.inOrder(refreshTokenStore);
                refreshStoreOrder.verify(refreshTokenStore).validate(jti, REFRESH_TOKEN_HASH, user.getEmail());
                refreshStoreOrder.verify(refreshTokenStore).revoke(jti);
                refreshStoreOrder
                        .verify(refreshTokenStore)
                        .issue(
                                "new-jti",
                                user.getEmail(),
                                REFRESH_TOKEN_HASH,
                                expected.refreshExpiration().toInstant());
                verify(tokenIssuer).issue(user, false, Provider.KAKAO);
                assertThat(actual).isSameAs(expected);
            }
        }
    }

    @Nested
    @DisplayName("토큰 폐기")
    class Revoke {

        @Test
        @DisplayName("refresh token이 유효하지 않으면 저장된 토큰을 폐기하지 않는다")
        void doesNotRevokeInvalidToken() {
            // given
            String refreshToken = "refresh-token";
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(false);

            // when
            tokenService.revoke(refreshToken);

            // then
            verifyNoInteractions(refreshTokenStore);
        }

        @Test
        @DisplayName("refresh token에 JTI가 없으면 저장된 토큰을 폐기하지 않는다")
        void doesNotRevokeTokenWithoutJti() {
            // given
            String refreshToken = "refresh-token";
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJti(refreshToken)).thenReturn(null);

            // when
            tokenService.revoke(refreshToken);

            // then
            verifyNoInteractions(refreshTokenStore);
        }

        @Test
        @DisplayName("유효한 refresh token에 JTI가 있으면 해당 JTI를 폐기한다")
        void revokesJti() {
            // given
            String refreshToken = "refresh-token";
            when(jwtTokenProvider.validateToken(refreshToken)).thenReturn(true);
            when(jwtTokenProvider.getJti(refreshToken)).thenReturn("refresh-jti");

            // when
            tokenService.revoke(refreshToken);

            // then
            verify(refreshTokenStore).revoke("refresh-jti");
        }
    }

    private void assertInvalidRefreshTokenWithoutChangingRedis(String refreshToken) {
        assertThatThrownBy(() -> tokenService.refresh(refreshToken))
                .isInstanceOfSatisfying(GeneralException.class, exception -> assertThat(exception.getCode())
                        .isEqualTo(ErrorStatus._INVALID_REFRESH_TOKEN));
        verify(refreshTokenStore, never()).revoke(anyString());
        verify(refreshTokenStore, never()).issue(anyString(), anyString(), anyString(), any());
        verifyNoInteractions(tokenIssuer);
    }

    private static IssuedTokens issuedTokens() {
        return new IssuedTokens(
                "access-token",
                Date.from(Instant.parse("2026-01-01T00:15:00Z")),
                "refresh-token",
                Date.from(Instant.parse("2026-01-08T00:00:00Z")));
    }
}

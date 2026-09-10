package com.fmi.domain.auth.service.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.Enum.Role;
import com.fmi.domain.auth.data.IssuedTokens;
import com.fmi.domain.user.data.User;
import com.fmi.security.JwtTokenProvider;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    @Captor
    private ArgumentCaptor<String> jtiCaptor;

    private TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer(jwtTokenProvider);
    }

    @Test
    @DisplayName("사용자와 제공자 정보로 access token, refresh token과 JTI를 생성한다")
    void issuesTokensAndRefreshTokenId() {
        // given
        User user = User.builder()
                .id(1L)
                .email("member@finditem.kr")
                .role(Role.USER)
                .build();
        Date accessExpiration = Date.from(Instant.parse("2026-01-01T00:15:00Z"));
        Date refreshExpiration = Date.from(Instant.parse("2026-01-08T00:00:00Z"));
        when(jwtTokenProvider.createAccessToken(eq(user.getEmail()), claimsCaptor.capture()))
                .thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(eq(user.getEmail()), jtiCaptor.capture()))
                .thenReturn("refresh-token");
        when(jwtTokenProvider.getExpiration("access-token")).thenReturn(accessExpiration);
        when(jwtTokenProvider.getExpiration("refresh-token")).thenReturn(refreshExpiration);

        // when
        TokenIssuer.IssueResult issueResult = tokenIssuer.issue(user, false, Provider.KAKAO);

        // then
        IssuedTokens issuedTokens = issueResult.issuedTokens();
        assertThat(issuedTokens.accessToken()).isEqualTo("access-token");
        assertThat(issuedTokens.accessExpiration()).isEqualTo(accessExpiration);
        assertThat(issuedTokens.refreshToken()).isEqualTo("refresh-token");
        assertThat(issuedTokens.refreshExpiration()).isEqualTo(refreshExpiration);
        assertThat(claimsCaptor.getValue())
                .containsEntry("userId", 1L)
                .containsEntry("role", "USER")
                .containsEntry("provider", "KAKAO")
                .containsEntry("purpose", "access");
        assertThat(jtiCaptor.getValue()).isNotBlank();
        assertThat(issueResult.refreshTokenId()).isEqualTo(jtiCaptor.getValue());
    }
}

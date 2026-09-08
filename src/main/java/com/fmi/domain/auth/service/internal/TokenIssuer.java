package com.fmi.domain.auth.service.internal;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.auth.data.IssuedTokens;
import com.fmi.domain.user.data.User;
import com.fmi.security.JwtTokenProvider;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;

    public IssueResult issue(User user, boolean isTemporaryPassword, Provider provider) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());
        if (provider != null) {
            claims.put("provider", provider.name());
        }
        claims.put("purpose", "access");
        if (isTemporaryPassword) {
            claims.put("isTemporaryPassword", true);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), claims);
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), jti);
        Date accessExpiration = jwtTokenProvider.getExpiration(accessToken);
        Date refreshExpiration = jwtTokenProvider.getExpiration(refreshToken);

        IssuedTokens issuedTokens = new IssuedTokens(accessToken, accessExpiration, refreshToken, refreshExpiration);
        return new IssueResult(issuedTokens, jti);
    }

    public record IssueResult(IssuedTokens issuedTokens, String refreshTokenId) {}
}

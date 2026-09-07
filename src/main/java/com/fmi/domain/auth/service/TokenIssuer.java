package com.fmi.domain.auth.service;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.auth.repository.SocialAccountsRepository;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.JwtTokenProvider;
import com.fmi.security.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenIssuer {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final SocialAccountsRepository socialAccountsRepository;

    public IssuedTokens issue(User user, boolean isTemporaryPassword, Provider provider) {
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
        refreshTokenStore.issue(jti, user.getEmail(), sha256Hex(refreshToken), refreshExpiration.toInstant());

        return new IssuedTokens(accessToken, accessExpiration, refreshToken, refreshExpiration);
    }

    public IssuedTokens refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new GeneralException(ErrorStatus._INVALID_REFRESH_TOKEN);
        }

        String email = jwtTokenProvider.getSubject(refreshToken);
        String jti = jwtTokenProvider.getJti(refreshToken);
        if (jti == null || jti.isEmpty()) {
            throw new GeneralException(ErrorStatus._INVALID_REFRESH_TOKEN);
        }

        if (!refreshTokenStore.validate(jti, sha256Hex(refreshToken), email)) {
            throw new GeneralException(ErrorStatus._INVALID_REFRESH_TOKEN);
        }

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus._INVALID_REFRESH_TOKEN));

        refreshTokenStore.revoke(jti);
        Provider provider = socialAccountsRepository
                .findByUser(user)
                .map(account -> account.getProvider())
                .orElse(null);
        return issue(user, false, provider);
    }

    public void revokeIfValid(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return;
        }

        String jti = jwtTokenProvider.getJti(refreshToken);
        if (jti != null && !jti.isEmpty()) {
            refreshTokenStore.revoke(jti);
        }
    }

    private static String sha256Hex(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record IssuedTokens(
            String accessToken, Date accessExpiration, String refreshToken, Date refreshExpiration) {}
}

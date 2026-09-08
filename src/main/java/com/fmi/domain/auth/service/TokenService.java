package com.fmi.domain.auth.service;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.auth.data.IssuedTokens;
import com.fmi.domain.auth.repository.SocialAccountsRepository;
import com.fmi.domain.auth.service.internal.TokenIssuer;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.JwtTokenProvider;
import com.fmi.security.RefreshTokenStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenIssuer tokenIssuer;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final UserRepository userRepository;
    private final SocialAccountsRepository socialAccountsRepository;

    public IssuedTokens issue(User user, boolean isTemporaryPassword, Provider provider) {
        TokenIssuer.IssueResult issueResult = tokenIssuer.issue(user, isTemporaryPassword, provider);
        IssuedTokens issuedTokens = issueResult.issuedTokens();
        refreshTokenStore.issue(
                issueResult.refreshTokenId(),
                user.getEmail(),
                sha256Hex(issuedTokens.refreshToken()),
                issuedTokens.refreshExpiration().toInstant());
        return issuedTokens;
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

    public void revoke(String refreshToken) {
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
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}

package com.fmi.domain.auth.web.controller;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.auth.service.SocialLoginService;
import com.fmi.domain.auth.service.TokenIssuer;
import com.fmi.domain.auth.web.dto.AppleLoginRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.external.oauth.apple.AppleOAuthClient;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/apple")
@RequiredArgsConstructor
public class AppleAuthController {

    private final AppleOAuthClient appleOAuthService;
    private final SocialLoginService socialLoginService;
    private final TokenIssuer tokenIssuer;
    private final AuthCookieFactory authCookieFactory;

    @PostMapping
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithApple(
            @Valid @RequestBody AppleLoginRequest request, HttpServletRequest httpServletRequest) {
        String subject = appleOAuthService.exchangeCodeForSubject(request.getCode(), request.getEnvironment());
        var localUser = socialLoginService.upsertUserFromApple(subject).user();
        boolean termsAgreed = localUser.isPrivacyPolicyAgreed() && localUser.isTermsOfServiceAgreed();

        TokenIssuer.IssuedTokens issuedTokens = tokenIssuer.issue(localUser, false, Provider.APPLE);

        ResponseCookie accessCookie = authCookieFactory.createAccessCookie(
                httpServletRequest, issuedTokens.accessToken(), issuedTokens.accessExpiration());
        ResponseCookie refreshCookie = authCookieFactory.createRefreshCookie(
                httpServletRequest, issuedTokens.refreshToken(), issuedTokens.refreshExpiration());

        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess(new LoginResponse(localUser.getId(), false, termsAgreed)));
    }
}

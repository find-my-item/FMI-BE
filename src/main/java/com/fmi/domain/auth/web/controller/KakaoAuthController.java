package com.fmi.domain.auth.web.controller;

import com.fmi.domain.Enum.Provider;
import com.fmi.domain.auth.service.SocialLoginService;
import com.fmi.domain.auth.service.TokenIssuer;
import com.fmi.domain.auth.web.dto.KakaoLoginRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.domain.auth.web.swagger.KakaoAuthSwagger;
import com.fmi.external.oauth.kakao.KakaoOAuthClient;
import com.fmi.external.oauth.kakao.KakaoOAuthClient.KakaoToken;
import com.fmi.external.oauth.kakao.KakaoOAuthClient.KakaoUser;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.security.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/kakao")
@RequiredArgsConstructor
public class KakaoAuthController implements KakaoAuthSwagger {

    private final KakaoOAuthClient kakaoOAuthService;
    private final SocialLoginService socialLoginService;
    private final TokenIssuer tokenIssuer;
    private final AuthCookieFactory authCookieFactory;

    @PostMapping
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest req, HttpServletRequest request) {
        // environment에 따라 환경 변수에서 자동 선택
        KakaoToken token = kakaoOAuthService.exchangeCodeForToken(req.getCode(), req.getEnvironment());
        String kakaoAccessToken = token.getAccess_token();

        KakaoUser user = kakaoOAuthService.getUserInfo(kakaoAccessToken);
        String email = user.getKakao_account() != null ? user.getKakao_account().getEmail() : null;
        String nickname = null;
        String profile = null;
        if (user.getKakao_account() != null && user.getKakao_account().getProfile() != null) {
            nickname = user.getKakao_account().getProfile().getNickname();
            profile = user.getKakao_account().getProfile().getProfile_image_url();
        }
        if (nickname == null && user.getProperties() != null) {
            nickname = user.getProperties().getNickname();
            profile = profile != null ? profile : user.getProperties().getProfile_image();
        }

        var result = socialLoginService.upsertUserFromKakao(user.getId(), email, nickname, profile);
        var localUser = result.user();
        boolean termsAgreed = localUser.isPrivacyPolicyAgreed() && localUser.isTermsOfServiceAgreed();

        TokenIssuer.IssuedTokens issuedTokens = tokenIssuer.issue(localUser, false, Provider.KAKAO);

        ResponseCookie accessCookie = authCookieFactory.createAccessCookie(
                request, issuedTokens.accessToken(), issuedTokens.accessExpiration());
        ResponseCookie refreshCookie = authCookieFactory.createRefreshCookie(
                request, issuedTokens.refreshToken(), issuedTokens.refreshExpiration());

        // 응답 생성 및 반환
        // accessToken과 refreshToken은 쿠키로 전송되므로 응답 body에는 포함하지 않습니다.
        // 소셜 로그인은 임시 비밀번호 기능이 없으므로 isTemporaryPassword는 항상 false입니다.
        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess(new LoginResponse(localUser.getId(), false, termsAgreed)));
    }
}

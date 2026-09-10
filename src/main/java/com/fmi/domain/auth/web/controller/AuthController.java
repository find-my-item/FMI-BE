package com.fmi.domain.auth.web.controller;

import com.fmi.domain.auth.converter.AuthConverter;
import com.fmi.domain.auth.data.IssuedTokens;
import com.fmi.domain.auth.service.AuthService;
import com.fmi.domain.auth.service.PasswordService;
import com.fmi.domain.auth.service.TokenService;
import com.fmi.domain.auth.service.WithdrawalService;
import com.fmi.domain.auth.web.dto.AccountDeleteRequest;
import com.fmi.domain.auth.web.dto.LoginRequest;
import com.fmi.domain.auth.web.dto.PasswordChangeRequest;
import com.fmi.domain.auth.web.dto.PasswordVerifyRequest;
import com.fmi.domain.auth.web.dto.SignupRequest;
import com.fmi.domain.auth.web.response.LoginResponse;
import com.fmi.domain.auth.web.swagger.AuthSwagger;
import com.fmi.domain.user.service.NicknameService;
import com.fmi.domain.user.web.response.CheckResponse;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.AuthCookieFactory;
import com.fmi.security.AuthCookieResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthSwagger {

    private final AuthService authService;
    private final NicknameService nicknameService;
    private final TokenService tokenService;
    private final PasswordService passwordService;
    private final WithdrawalService withdrawalService;
    private final AuthCookieFactory authCookieFactory;
    private final AuthCookieResolver authCookieResolver;

    @PostMapping("/auth/signup")
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        var user = authService.signup(request);
        return buildTokenResponse(httpRequest, user, false);
    }

    @PostMapping("/auth/login")
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        var authResult = authService.authenticate(request.getEmail(), request.getPassword());
        return buildTokenResponse(httpRequest, authResult.getUser(), authResult.isTemporaryPassword());
    }

    @GetMapping("/auth/check-nickname")
    @Override
    public ResponseEntity<ApiResponse<?>> checkNickname(@RequestParam("nickname") @NotBlank String nickname) {
        CheckResponse response = nicknameService.check(nickname);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/auth/refresh")
    @Override
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(HttpServletRequest request) {
        String refreshJwt = authCookieResolver
                .findRefreshToken(request)
                .filter(token -> !token.isEmpty())
                .orElseThrow(() -> new GeneralException(ErrorStatus._REFRESH_TOKEN_NOT_FOUND));
        IssuedTokens issuedTokens = tokenService.refresh(refreshJwt);

        ResponseCookie accessCookie = authCookieFactory.createAccessCookie(
                request, issuedTokens.accessToken(), issuedTokens.accessExpiration());
        ResponseCookie refreshCookie = authCookieFactory.createRefreshCookie(
                request, issuedTokens.refreshToken(), issuedTokens.refreshExpiration());

        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess(AuthConverter.toLoginResponse(null)));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> buildTokenResponse(
            HttpServletRequest request, com.fmi.domain.user.data.User user, boolean isTemporaryPassword) {
        IssuedTokens issuedTokens = tokenService.issue(user, isTemporaryPassword, null);

        ResponseCookie accessCookie = authCookieFactory.createAccessCookie(
                request, issuedTokens.accessToken(), issuedTokens.accessExpiration());
        ResponseCookie refreshCookie = authCookieFactory.createRefreshCookie(
                request, issuedTokens.refreshToken(), issuedTokens.refreshExpiration());

        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess(AuthConverter.toLoginResponse(user.getId(), isTemporaryPassword)));
    }

    @PostMapping("/auth/logout")
    @Override
    public ResponseEntity<ApiResponse<String>> logout(HttpServletRequest request) {
        authCookieResolver
                .findRefreshToken(request)
                .filter(token -> !token.isEmpty())
                .ifPresent(tokenService::revoke);

        ResponseCookie accessCookie = authCookieFactory.expireAccessCookie(request);
        ResponseCookie refreshCookie = authCookieFactory.expireRefreshCookie(request);

        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess("OK"));
    }

    @PostMapping("/users/me/password/verify")
    public ApiResponse<Void> verifyPassword(
            @AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody PasswordVerifyRequest request) {
        passwordService.verify(userDetails.getUsername(), request);
        return ApiResponse.onSuccess(null);
    }

    @PatchMapping("/users/me/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails, @Valid @RequestBody PasswordChangeRequest request) {
        passwordService.change(userDetails.getUsername(), request.getNewPassword(), request.getNewPasswordConfirm());
        return ApiResponse.onSuccess(null);
    }

    @DeleteMapping("/users/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody AccountDeleteRequest request,
            HttpServletRequest httpRequest) {
        withdrawalService.delete(userDetails.getUsername(), request);
        ResponseCookie accessCookie = authCookieFactory.expireAccessCookie(httpRequest);
        ResponseCookie refreshCookie = authCookieFactory.expireRefreshCookie(httpRequest);
        return ResponseEntity.ok()
                .header("Set-Cookie", accessCookie.toString())
                .header("Set-Cookie", refreshCookie.toString())
                .body(ApiResponse.onSuccess(null));
    }
}

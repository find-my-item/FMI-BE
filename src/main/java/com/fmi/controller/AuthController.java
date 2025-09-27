package com.fmi.controller;

import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import com.fmi.domain.Enum.Role;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        Long id = authService.signup(
                request.getEmail(),
                request.getPassword(),
                request.getNickname(),
                request.getName(),
                request.getPhoneNumber(),
                request.getProfileImg(),
                request.getRole(),
                request.getTermsOfServiceAgreed(),
                request.getPrivacyPolicyAgreed(),
                request.getMarketingConsent(),
                request.getTrustScore(),
                request.getEmailVerified(),
                request.getPhoneVerified()
        );
        return ApiResponse.onSuccess(new SignupResponse(id));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var user = authService.authenticate(request.getEmail(), request.getPassword());
        return ApiResponse.onSuccess(new LoginResponse(user.getUserId()));
    }

    @Data
    public static class SignupRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
        @NotBlank
        private String nickname;
        @NotBlank
        private String name;

        // 선택/부가 정보
        private String phoneNumber;
        private String profileImg;
        private Role role; // 기본값 USER (null이면 서버에서 설정)

        // 동의 항목
        private Boolean termsOfServiceAgreed;
        private Boolean privacyPolicyAgreed;
        private Boolean marketingConsent;

        // 검증/점수(옵션)
        private Long trustScore;
        private Boolean emailVerified;
        private Boolean phoneVerified;
    }

    @Data
    @AllArgsConstructor
    public static class SignupResponse {
        private Long id;
    }

    @Data
    public static class LoginRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        private String password;
    }

    @Data
    @AllArgsConstructor
    public static class LoginResponse {
        private Long userId;
    }
}



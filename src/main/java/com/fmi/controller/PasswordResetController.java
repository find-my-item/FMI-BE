package com.fmi.controller;

import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.service.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService resetService;

    @PostMapping("/request")
    public ApiResponse<RequestResponse> request(@Valid @RequestBody RequestDto dto) {
        String token = resetService.requestReset(dto.getEmail());
        return ApiResponse.onSuccess(new RequestResponse(token));
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody ConfirmDto dto) {
        resetService.confirmReset(dto.getToken(), dto.getNewPassword());
        return ApiResponse.onSuccess(null);
    }

    @Data
    public static class RequestDto {
        @Email
        @NotBlank
        private String email;
    }

    @Data
    public static class ConfirmDto {
        @NotBlank
        private String token;
        @NotBlank
        private String newPassword;
    }

    @Data
    @AllArgsConstructor
    public static class RequestResponse {
        private String token;
    }
}



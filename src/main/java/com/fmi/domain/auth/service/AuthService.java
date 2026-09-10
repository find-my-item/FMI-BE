package com.fmi.domain.auth.service;

import com.fmi.domain.Enum.Role;
import com.fmi.domain.admin.web.dto.AdminSignupRequest;
import com.fmi.domain.auth.event.UserSignedUpEvent;
import com.fmi.domain.auth.service.internal.PasswordValidator;
import com.fmi.domain.auth.service.internal.SignupValidator;
import com.fmi.domain.auth.web.dto.SignupRequest;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.domain.user.service.internal.NicknameValidator;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.util.Optional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NicknameValidator nicknameValidator;
    private final EmailVerificationService emailVerificationService;
    private final PasswordValidator passwordValidator;
    private final SignupValidator signupValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public User signup(SignupRequest request) {
        signupValidator.validate(request.getEmail());
        passwordValidator.validateNewPassword(request.getPassword());
        nicknameValidator.validateAvailable(request.getNickname());

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.USER)
                .email_verified(true)
                .privacyPolicyAgreed(Boolean.TRUE.equals(request.getPrivacyPolicyAgreed()))
                .termsOfServiceAgreed(Boolean.TRUE.equals(request.getTermsOfServiceAgreed()))
                .contentPolicyAgreed(Boolean.TRUE.equals(request.getContentPolicyAgreed()))
                .marketingConsent(Boolean.TRUE.equals(request.getMarketingConsent()))
                .build();

        // 약관 동의 필드는 @NotNull로 값은 필수지만, true/false 모두 허용
        // (null 체크는 @NotNull에서 처리됨)

        // 보안: 프론트엔드에서 보낸 emailVerified 값은 무시하고, Redis에서 실제 인증 여부 확인
        boolean isEmailVerified = emailVerificationService.isEmailVerified(request.getEmail());
        if (!isEmailVerified) {
            throw new GeneralException(ErrorStatus._EMAIL_NOT_VERIFIED);
        }

        User savedUser = userRepository.save(user);
        eventPublisher.publishEvent(UserSignedUpEvent.from(savedUser));

        return savedUser;
    }

    /**
     * 관리자 회원가입
     * Role을 ADMIN으로 강제 설정합니다.
     * 동의 항목은 받지 않습니다.
     */
    @Transactional
    public Long adminSignup(AdminSignupRequest request) {
        signupValidator.validate(request.getEmail());
        passwordValidator.validateNewPassword(request.getPassword());
        nicknameValidator.validateAvailable(request.getNickname());

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(Role.ADMIN)
                .email_verified(Boolean.TRUE.equals(request.getEmailVerified()))
                .privacyPolicyAgreed(false)
                .termsOfServiceAgreed(false)
                .contentPolicyAgreed(false)
                .marketingConsent(false)
                .build();
        return userRepository.save(user).getId();
    }

    /**
     * 인증 및 임시 비밀번호 여부 확인
     * @return AuthenticateResult (user, isTemporaryPassword)
     */
    public AuthenticateResult authenticate(String email, String rawPassword) {
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus._INVALID_CREDENTIALS));

        boolean isTemporaryPassword = passwordValidator.matchesTemporaryPassword(user, rawPassword);
        if (!isTemporaryPassword && !passwordValidator.matchesPermanentPassword(user, rawPassword)) {
            throw new GeneralException(ErrorStatus._INVALID_CREDENTIALS);
        }
        return new AuthenticateResult(user, isTemporaryPassword);
    }

    /**
     * 활성 사용자 조회 (deletedAt이 null인 사용자만)
     */
    public Optional<User> findActiveUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Data
    public static class AuthenticateResult {
        private final User user;
        private final boolean isTemporaryPassword;
    }
}

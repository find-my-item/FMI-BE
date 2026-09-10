package com.fmi.domain.auth.service;

import com.fmi.domain.auth.repository.SocialAccountsRepository;
import com.fmi.domain.auth.service.internal.PasswordGenerator;
import com.fmi.domain.auth.service.internal.PasswordValidator;
import com.fmi.domain.auth.web.dto.PasswordVerifyRequest;
import com.fmi.domain.user.data.User;
import com.fmi.domain.user.repository.UserRepository;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.security.RefreshTokenStore;
import com.fmi.service.EmailService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordService {

    private final UserRepository userRepository;
    private final SocialAccountsRepository socialAccountsRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final PasswordGenerator passwordGenerator;
    private final RefreshTokenStore refreshTokenStore;
    private final EmailService emailService;
    private final Clock clock;

    public void verify(String email, PasswordVerifyRequest request) {
        User user =
                userRepository.findByEmail(email).orElseThrow(() -> new GeneralException(ErrorStatus._USER_NOT_FOUND));
        boolean matchesPassword = passwordValidator.matchesTemporaryPassword(user, request.getCurrentPassword())
                || passwordValidator.matchesPermanentPassword(user, request.getCurrentPassword());
        if (!matchesPassword) {
            throw new GeneralException(ErrorStatus._CURRENT_PASSWORD_INCORRECT);
        }
    }

    public void change(String email, String newPassword, String newPasswordConfirm) {
        User user =
                userRepository.findByEmail(email).orElseThrow(() -> new GeneralException(ErrorStatus._USER_NOT_FOUND));
        passwordValidator.validateConfirmation(newPassword, newPasswordConfirm);
        passwordValidator.validateNewPassword(newPassword);

        String encodedPassword = passwordEncoder.encode(newPassword);
        LocalDateTime changedAt = LocalDateTime.now(clock);
        user.changePassword(encodedPassword, changedAt);
        userRepository.save(user);

        refreshTokenStore.revokeAllForUser(email);
    }

    public void issueTemporaryPassword(String email) {
        User user =
                userRepository.findByEmail(email).orElseThrow(() -> new GeneralException(ErrorStatus._USER_NOT_FOUND));

        if (socialAccountsRepository.findByUser(user).isPresent()) {
            throw new GeneralException(ErrorStatus._SOCIAL_ACCOUNT);
        }

        String temporaryPassword = passwordGenerator.generateTemporaryPassword();
        LocalDateTime now = LocalDateTime.now(clock);
        user.issueTemporaryPassword(passwordEncoder.encode(temporaryPassword), now.plusHours(1), now);
        userRepository.save(user);
        emailService.sendHtmlEmail(
                email, "임시 비밀번호 발급", "password-reset-email.html", Map.of("PASSWORD", temporaryPassword));
    }
}

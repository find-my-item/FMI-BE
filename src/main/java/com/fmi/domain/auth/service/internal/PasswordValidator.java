package com.fmi.domain.auth.service.internal;

import com.fmi.domain.user.data.PasswordPolicy;
import com.fmi.domain.user.data.User;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordValidator {
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public void validateNewPassword(String rawPassword) {
        PasswordPolicy.validate(rawPassword);
    }

    public void validateConfirmation(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new GeneralException(ErrorStatus._PASSWORD_CONFIRMATION_MISMATCH);
        }
    }

    public boolean matchesTemporaryPassword(User user, String rawPassword) {
        LocalDateTime now = LocalDateTime.now(clock);
        return user.hasActiveTemporaryPassword(now)
                && passwordEncoder.matches(rawPassword, user.getTemporaryPassword());
    }

    public boolean matchesPermanentPassword(User user, String rawPassword) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (user.hasActiveTemporaryPassword(now) || user.hasExpiredTemporaryPassword(now)) {
            return user.getOriginalPassword() != null
                    && passwordEncoder.matches(rawPassword, user.getOriginalPassword());
        }

        return passwordEncoder.matches(rawPassword, user.getPassword());
    }
}

package com.fmi.service;

import com.fmi.domain.PasswordResetToken;
import com.fmi.domain.User;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.repository.PasswordResetTokenRepository;
import com.fmi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public String requestReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus._BAD_REQUEST));
        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        tokenRepository.save(prt);
        return token; // 실제 환경에서는 이메일 전송
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new GeneralException(ErrorStatus._RESET_TOKEN_INVALID));
        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new GeneralException(ErrorStatus._RESET_TOKEN_EXPIRED);
        }
        User user = prt.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        tokenRepository.delete(prt);
    }
}



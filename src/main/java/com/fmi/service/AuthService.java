package com.fmi.service;

import com.fmi.domain.User;
import com.fmi.domain.Enum.Role;
import com.fmi.global.apiPayload.exception.GeneralException;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long signup(String email, String rawPassword, String nickname, String name,
                       String phoneNumber, String profileImg, Role role,
                       Boolean termsOfServiceAgreed, Boolean privacyPolicyAgreed, Boolean marketingConsent,
                       Long trustScore, Boolean emailVerified, Boolean phoneVerified) {
        if (userRepository.existsByEmail(email)) {
            throw new GeneralException(ErrorStatus._EMAIL_DUPLICATED);
        }
        // 비밀번호 규칙: 8자 이상, 대문자/소문자/숫자/특수문자 포함
        String pw = rawPassword == null ? "" : rawPassword;
        boolean valid = pw.length() >= 8
                && pw.matches(".*[A-Z].*")
                && pw.matches(".*[a-z].*")
                && pw.matches(".*[0-9].*")
                && pw.matches(".*[!@#$%^&*()\\-_=+\\[{\\]}\\\\|;:'\",<.>/?].*");
        if (!valid) {
            throw new GeneralException(ErrorStatus._WEAK_PASSWORD);
        }
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .name(name)
                .phone_number(phoneNumber)
                .profile_img(profileImg != null ? profileImg : "")
                .role(role != null ? role : Role.USER)
                .termsOfServiceAgreed(Boolean.TRUE.equals(termsOfServiceAgreed))
                .privacyPolicyAgreed(Boolean.TRUE.equals(privacyPolicyAgreed))
                .marketingConsent(Boolean.TRUE.equals(marketingConsent))
                .trust_score(trustScore != null ? trustScore : 0L)
                .email_verified(Boolean.TRUE.equals(emailVerified))
                .phone_verified(Boolean.TRUE.equals(phoneVerified))
                .build();
        return userRepository.save(user).getUserId();
    }

    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new GeneralException(ErrorStatus._UNAUTHORIZED));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new GeneralException(ErrorStatus._UNAUTHORIZED);
        }
        return user;
    }
}



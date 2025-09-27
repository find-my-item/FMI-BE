package com.fmi.domain;


import com.fmi.domain.Enum.Role;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "nickname", nullable = false)
    private String nickname;

    @Email
    @Column(name = "email",nullable = true)
    private String email;

    @Column(name = "email_verified")
    private boolean email_verified;

    @Column(name = "phone_number")
    private String phone_number;

    @Column(name = "phone_verified")
    private boolean phone_verified;

    @Enumerated(EnumType.STRING) // 여기서 STRING으로 매핑
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "profile_img", nullable = false)
    private String profile_img;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "trust_score")
    private Long trust_score;

    private boolean termsOfServiceAgreed;      // 서비스 이용약관 동의
    private boolean privacyPolicyAgreed;       // 개인정보 처리방침 동의
    private boolean marketingConsent;           // 마케팅 수신 동의

    public void updateUserInfo(String name, String email) {
        if (name != null) {
            this.name = name;
        }
        if (email != null) {
            this.email = email;
        }
    }
}

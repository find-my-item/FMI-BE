package com.fmi.domain;


import com.fmi.domain.Enum.InquiryStatus;
import com.fmi.domain.Enum.InquiryType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    private InquiryType inquiryType; // 문의 종류: 1:1 문의, 전체문의

    private InquiryStatus answer_status; // 처리 상태: 대기중, 처리완료

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 문의자

}


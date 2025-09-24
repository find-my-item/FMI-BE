package com.fmi.domain;

import com.fmi.domain.Enum.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notice {//공지사항

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "pinned")
    private boolean pinned; // 상단 고정 여부

    @Column(name = "view_cnt")
    private String view_cnt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;


}

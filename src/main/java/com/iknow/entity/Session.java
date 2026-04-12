package com.iknow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String sessionId;  // 숫자 + 대문자 알파벳 8자리 (예: AB12CD34)

    private String classId;

    private Integer thresholdPct;  // 혼란 감지 임계값 % (대시보드 참고용, 기본 50)

    @Column(columnDefinition = "TEXT")
    private String curriculum;     // 커리큘럼 텍스트 (대시보드 참고용)

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    public enum SessionStatus {
        ACTIVE, ENDED
    }
}

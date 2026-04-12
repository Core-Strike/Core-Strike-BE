package com.iknow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sessionId;

    private String classId;

    private String curriculum;

    private String studentId;

    private String studentName;    // 수강생 이름

    private Integer studentCount;

    private Integer totalStudentCount;

    @Column(nullable = false)
    private LocalDateTime capturedAt;

    private Double confusedScore;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // confused 발생 시점 기준 가장 가까운 강의 토픽 (LectureTopic에서 매칭)
    @Column(columnDefinition = "TEXT")
    private String unclearTopic;

    // 이벤트 직후 2분 녹음 STT 원문
    @Column(columnDefinition = "TEXT")
    private String lectureText;

    // GPT 요약문 (FastAPI /summarize 결과)
    @Column(columnDefinition = "TEXT")
    private String lectureSummary;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

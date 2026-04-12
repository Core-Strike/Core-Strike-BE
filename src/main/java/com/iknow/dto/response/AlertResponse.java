package com.iknow.dto.response;

import com.iknow.entity.Alert;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AlertResponse {
    private Long id;
    private String sessionId;
    private String classId;
    private String curriculum;
    private String studentId;
    private String studentName;
    private Integer studentCount;
    private Integer totalStudentCount;
    private LocalDateTime capturedAt;
    private Double confusedScore;
    private String reason;
    private String unclearTopic;
    private String lectureText;
    private String lectureSummary;
    private List<String> keywords;
    private LocalDateTime createdAt;

    public static AlertResponse from(Alert alert, List<String> keywords) {
        return AlertResponse.builder()
                .id(alert.getId())
                .sessionId(alert.getSessionId())
                .classId(alert.getClassId())
                .curriculum(alert.getCurriculum())
                .studentId(alert.getStudentId())
                .studentName(alert.getStudentName())
                .studentCount(alert.getStudentCount())
                .totalStudentCount(alert.getTotalStudentCount())
                .capturedAt(alert.getCapturedAt())
                .confusedScore(alert.getConfusedScore())
                .reason(alert.getReason())
                .unclearTopic(alert.getUnclearTopic())
                .lectureText(alert.getLectureText())
                .lectureSummary(alert.getLectureSummary())
                .keywords(keywords)
                .createdAt(alert.getCreatedAt())
                .build();
    }

    public static AlertResponse from(Alert alert) {
        return from(alert, List.of());
    }
}

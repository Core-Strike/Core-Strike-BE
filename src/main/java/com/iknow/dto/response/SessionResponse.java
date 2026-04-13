package com.iknow.dto.response;

import com.iknow.entity.Session;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class SessionResponse {
    private String sessionId;
    private String classId;
    private Integer thresholdPct;
    private String curriculum;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long activeParticipantCount;

    public static SessionResponse from(Session session) {
        return from(session, 0L);
    }

    public static SessionResponse from(Session session, long activeParticipantCount) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .classId(session.getClassId())
                .thresholdPct(session.getThresholdPct())
                .curriculum(session.getCurriculum())
                .status(session.getStatus().name())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .activeParticipantCount(activeParticipantCount)
                .build();
    }
}

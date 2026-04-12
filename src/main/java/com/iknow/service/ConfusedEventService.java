package com.iknow.service;

import com.iknow.dto.request.ConfusedEventRequest;
import com.iknow.dto.response.AlertWebSocketPayload;
import com.iknow.entity.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class ConfusedEventService {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public void handleConfusedEvent(ConfusedEventRequest request) {
        Session session = sessionService.getActiveSessionOrThrow(request.getSessionId());

        AlertWebSocketPayload payload = AlertWebSocketPayload.builder()
                .sessionId(request.getSessionId())
                .classId(session.getClassId())
                .studentCount(request.getStudentCount() != null ? request.getStudentCount() : 1)
                .totalStudentCount(request.getTotalStudentCount() != null ? request.getTotalStudentCount() : 1)
                .confusedScore(request.getConfusedScore())
                .reason(request.getReason())
                .capturedAt(request.getCapturedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        messagingTemplate.convertAndSend("/topic/alert/" + request.getSessionId(), payload);
    }
}

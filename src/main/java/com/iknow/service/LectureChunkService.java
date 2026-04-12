package com.iknow.service;

import com.iknow.dto.request.LectureChunkRequest;
import com.iknow.dto.response.AlertResponse;
import com.iknow.entity.Alert;
import com.iknow.entity.Session;
import com.iknow.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LectureChunkService {

    private final AlertRepository alertRepository;
    private final SessionService sessionService;

    @Transactional
    public AlertResponse saveLectureChunk(LectureChunkRequest request) {
        Session session = sessionService.getActiveSessionOrThrow(request.getSessionId());

        Alert alert = Alert.builder()
                .sessionId(session.getSessionId())
                .studentCount(request.getStudentCount() != null ? request.getStudentCount() : 1)
                .totalStudentCount(request.getTotalStudentCount() != null ? request.getTotalStudentCount() : 1)
                .capturedAt(request.getCapturedAt() != null ? request.getCapturedAt() : LocalDateTime.now())
                .confusedScore(request.getConfusedScore())
                .reason(request.getReason())
                .unclearTopic(request.getAudioText())
                .lectureText(request.getAudioText())
                .build();

        return AlertResponse.from(alertRepository.save(alert));
    }
}

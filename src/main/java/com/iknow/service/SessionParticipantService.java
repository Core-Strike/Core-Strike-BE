package com.iknow.service;

import com.iknow.dto.request.SessionParticipantRequest;
import com.iknow.entity.Session;
import com.iknow.entity.SessionParticipant;
import com.iknow.repository.SessionParticipantRepository;
import com.iknow.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionParticipantService {

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    @Transactional
    public void joinSession(String sessionId, SessionParticipantRequest request) {
        String studentId = normalizeRequired(request.getStudentId(), "Student ID is required");
        String studentName = normalizeOptional(request.getStudentName());
        Session session = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId));

        if (session.getStatus() != Session.SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is not active: " + sessionId);
        }

        SessionParticipant participant = sessionParticipantRepository
                .findTopBySessionIdAndStudentIdOrderByJoinedAtDesc(sessionId, studentId)
                .orElse(SessionParticipant.builder()
                        .sessionId(sessionId)
                        .studentId(studentId)
                        .build());

        participant.setClassId(session.getClassId());
        participant.setCurriculum(session.getCurriculum());
        participant.setStudentName(studentName);
        participant.setActive(true);
        participant.setLeftAt(null);
        if (participant.getId() != null) {
            participant.setJoinedAt(LocalDateTime.now());
        }

        sessionParticipantRepository.save(participant);
    }

    @Transactional
    public void leaveSession(String sessionId, SessionParticipantRequest request) {
        String studentId = normalizeRequired(request.getStudentId(), "Student ID is required");

        sessionParticipantRepository.findTopBySessionIdAndStudentIdOrderByJoinedAtDesc(sessionId, studentId)
                .ifPresent(participant -> {
                    participant.setActive(false);
                    participant.setLeftAt(LocalDateTime.now());
                });
    }

    @Transactional
    public void leaveAllActiveParticipants(String sessionId) {
        List<SessionParticipant> participants = sessionParticipantRepository.findBySessionIdAndActiveTrue(sessionId);
        LocalDateTime now = LocalDateTime.now();
        participants.forEach(participant -> {
            participant.setActive(false);
            participant.setLeftAt(now);
        });
    }

    @Transactional(readOnly = true)
    public long countActiveParticipants(String sessionId) {
        return sessionParticipantRepository.countBySessionIdAndActiveTrue(sessionId);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }
}

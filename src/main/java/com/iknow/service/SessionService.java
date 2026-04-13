package com.iknow.service;

import com.iknow.dto.request.CreateSessionRequest;
import com.iknow.dto.response.SessionResponse;
import com.iknow.entity.Session;
import com.iknow.repository.CurriculumRepository;
import com.iknow.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Duration SESSION_MAX_DURATION = Duration.ofDays(1);
    private static final String SESSION_ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SESSION_ID_LENGTH = 8;

    private final CurriculumRepository curriculumRepository;
    private final SessionParticipantService sessionParticipantService;
    private final SessionRepository sessionRepository;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        String sessionId = generateUniqueSessionId();
        String curriculum = normalizeOptional(request.getCurriculum());
        String classId = normalizeOptional(request.getClassId());
        if (curriculum.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Curriculum is required");
        }
        if (!curriculumRepository.existsByName(curriculum)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown curriculum: " + curriculum);
        }

        terminateExistingSessionsForScope(curriculum, classId);

        Session session = Session.builder()
                .sessionId(sessionId)
                .classId(classId)
                .thresholdPct(request.getThresholdPct() != null ? request.getThresholdPct() : 50)
                .curriculum(curriculum)
                .status(Session.SessionStatus.ACTIVE)
                .build();

        return SessionResponse.from(sessionRepository.save(session), 0L);
    }

    @Transactional
    public SessionResponse endSession(String sessionId) {
        Session session = findSessionOrThrow(sessionId);
        return terminateSession(session);
    }

    @Transactional
    public void terminateSessionIfExists(String sessionId) {
        sessionParticipantService.leaveAllActiveParticipants(sessionId);
        sessionRepository.deleteBySessionIdIfExists(sessionId);
    }

    @Transactional
    public SessionResponse getSession(String sessionId) {
        Session session = findSessionOrThrow(sessionId);
        expireSessionIfNeeded(session);
        long activeParticipantCount = sessionParticipantService.countActiveParticipants(sessionId);
        return SessionResponse.from(session, activeParticipantCount);
    }

    @Transactional
    public Session getActiveSessionOrThrow(String sessionId) {
        Session session = findSessionOrThrow(sessionId);
        expireSessionIfNeeded(session);

        if (session.getStatus() != Session.SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session is no longer active: " + sessionId);
        }

        return session;
    }

    private Session findSessionOrThrow(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId));
    }

    private void expireSessionIfNeeded(Session session) {
        if (session.getStatus() != Session.SessionStatus.ACTIVE || session.getStartedAt() == null) {
            return;
        }

        LocalDateTime expiresAt = session.getStartedAt().plus(SESSION_MAX_DURATION);
        if (!expiresAt.isAfter(LocalDateTime.now())) {
            session.setStatus(Session.SessionStatus.ENDED);
            if (session.getEndedAt() == null) {
                session.setEndedAt(expiresAt);
            }
        }
    }

    private SessionResponse terminateSession(Session session) {
        session.setStatus(Session.SessionStatus.ENDED);
        if (session.getEndedAt() == null) {
            session.setEndedAt(LocalDateTime.now());
        }

        sessionParticipantService.leaveAllActiveParticipants(session.getSessionId());

        SessionResponse response = SessionResponse.from(session, 0L);
        sessionRepository.deleteBySessionIdIfExists(session.getSessionId());
        return response;
    }

    private void terminateExistingSessionsForScope(String curriculum, String classId) {
        List<Session> existingSessions = sessionRepository.findAllByStatusAndCurriculumAndClassId(
                Session.SessionStatus.ACTIVE,
                curriculum,
                classId
        );

        existingSessions.forEach(this::terminateSession);
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String generateUniqueSessionId() {
        String sessionId;
        do {
            sessionId = ThreadLocalRandom.current()
                    .ints(SESSION_ID_LENGTH, 0, SESSION_ID_CHARS.length())
                    .mapToObj(i -> String.valueOf(SESSION_ID_CHARS.charAt(i)))
                    .collect(Collectors.joining());
        } while (sessionRepository.existsBySessionId(sessionId));
        return sessionId;
    }
}

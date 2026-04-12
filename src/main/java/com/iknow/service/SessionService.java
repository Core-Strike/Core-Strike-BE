package com.iknow.service;

import com.iknow.dto.request.CreateSessionRequest;
import com.iknow.dto.response.SessionResponse;
import com.iknow.entity.Session;
import com.iknow.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Duration SESSION_MAX_DURATION = Duration.ofDays(1);

    private final SessionRepository sessionRepository;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        String sessionId = generateUniqueSessionId();

        Session session = Session.builder()
                .sessionId(sessionId)
                .classId(request.getClassId())
                .thresholdPct(request.getThresholdPct() != null ? request.getThresholdPct() : 50)
                .curriculum(request.getCurriculum())
                .status(Session.SessionStatus.ACTIVE)
                .build();

        return SessionResponse.from(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse endSession(String sessionId) {
        Session session = findSessionOrThrow(sessionId);
        session.setStatus(Session.SessionStatus.ENDED);
        if (session.getEndedAt() == null) {
            session.setEndedAt(LocalDateTime.now());
        }
        return SessionResponse.from(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse getSession(String sessionId) {
        Session session = findSessionOrThrow(sessionId);
        expireSessionIfNeeded(session);
        return SessionResponse.from(session);
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

    // 100000~999999 범위 랜덤 숫자, 중복이면 재생성
    private String generateUniqueSessionId() {
        String sessionId;
        do {
            int random = ThreadLocalRandom.current().nextInt(100000, 1000000);
            sessionId = String.valueOf(random);
        } while (sessionRepository.existsBySessionId(sessionId));
        return sessionId;
    }
}

package com.iknow.repository;

import com.iknow.entity.SessionParticipant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SessionParticipantRepository extends JpaRepository<SessionParticipant, Long> {
    Optional<SessionParticipant> findTopBySessionIdAndStudentIdOrderByJoinedAtDesc(String sessionId, String studentId);
    List<SessionParticipant> findByJoinedAtBetween(LocalDateTime start, LocalDateTime end);
    List<SessionParticipant> findBySessionIdAndActiveTrue(String sessionId);
    long countBySessionIdAndActiveTrue(String sessionId);
}

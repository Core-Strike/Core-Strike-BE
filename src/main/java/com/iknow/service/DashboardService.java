package com.iknow.service;

import com.iknow.dto.response.AlertResponse;
import com.iknow.dto.response.DashboardClassResponse;
import com.iknow.dto.response.KeywordReportResponse;
import com.iknow.entity.Alert;
import com.iknow.entity.AlertKeyword;
import com.iknow.entity.Session;
import com.iknow.entity.SessionParticipant;
import com.iknow.repository.AlertRepository;
import com.iknow.repository.AlertKeywordRepository;
import com.iknow.repository.SessionParticipantRepository;
import com.iknow.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final SessionRepository sessionRepository;
    private final AlertRepository alertRepository;
    private final AlertKeywordRepository alertKeywordRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    @Transactional(readOnly = true)
    public List<DashboardClassResponse> getDashboardClasses(LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Alert> alertsForDate = alertRepository.findByCapturedAtBetweenOrderByCapturedAtDesc(startOfDay, endOfDay);
        List<SessionParticipant> participantsForDate = sessionParticipantRepository.findByJoinedAtBetween(startOfDay, endOfDay);

        Set<String> sessionIds = alertsForDate.stream()
                .map(Alert::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Session> sessionBySessionId = sessionRepository.findAllBySessionIdIn(sessionIds)
                .stream()
                .collect(Collectors.toMap(Session::getSessionId, session -> session));
        Map<String, SessionParticipant> participantSnapshotBySessionId = participantsForDate.stream()
                .filter(participant -> participant.getSessionId() != null)
                .collect(Collectors.toMap(
                        SessionParticipant::getSessionId,
                        participant -> participant,
                        (first, second) -> first
                ));

        Map<ClassGroupingKey, List<Alert>> alertsByClass = alertsForDate.stream()
                .collect(Collectors.groupingBy(alert -> {
                    Session session = sessionBySessionId.get(alert.getSessionId());
                    SessionParticipant participant = participantSnapshotBySessionId.get(alert.getSessionId());
                    String curriculum = session != null && session.getCurriculum() != null
                            ? session.getCurriculum()
                            : (alert.getCurriculum() != null ? alert.getCurriculum() : (participant != null ? participant.getCurriculum() : ""));
                    String classId = session != null && session.getClassId() != null
                            ? session.getClassId()
                            : (alert.getClassId() != null ? alert.getClassId() : (participant != null ? participant.getClassId() : "unknown"));
                    return new ClassGroupingKey(curriculum, classId);
                }));

        return alertsByClass.entrySet().stream()
                .map(entry -> buildClassResponse(entry.getKey(), entry.getValue(), participantsForDate))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public KeywordReportResponse getKeywordReport(LocalDate date, String keyword, String curriculum, String classId) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }

        FilteredAlertContext context = getFilteredAlertContext(date, curriculum, classId);
        List<Long> alertIds = context.alerts().stream()
                .map(Alert::getId)
                .filter(Objects::nonNull)
                .toList();

        if (alertIds.isEmpty()) {
            return buildKeywordReport(date, normalizedKeyword, curriculum, classId, List.of());
        }

        Set<Long> matchedAlertIds = alertKeywordRepository.findByAlertIdInAndKeyword(alertIds, normalizedKeyword).stream()
                .map(AlertKeyword::getAlertId)
                .collect(Collectors.toSet());

        List<Alert> matchedAlerts = context.alerts().stream()
                .filter(alert -> matchedAlertIds.contains(alert.getId()))
                .sorted(Comparator.comparing(Alert::getCapturedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return buildKeywordReport(date, normalizedKeyword, curriculum, classId, matchedAlerts);
    }

    private KeywordReportResponse buildKeywordReport(LocalDate date, String keyword, String curriculum, String classId, List<Alert> matchedAlerts) {
        int avgConfusion = matchedAlerts.isEmpty()
                ? 0
                : (int) Math.round(matchedAlerts.stream()
                        .mapToInt(this::calculateConfusionPercent)
                        .average()
                        .orElse(0.0));
        int avgUnderstanding = Math.max(0, 100 - avgConfusion);
        int reinforcementNeed = avgConfusion;

        String reinforcementLevel = reinforcementNeed >= 70
                ? "높음"
                : reinforcementNeed >= 40
                ? "보통"
                : "낮음";

        String report = matchedAlerts.isEmpty()
                ? String.format("'%s' 키워드와 연결된 알림이 아직 없습니다.", keyword)
                : String.format("'%s' 관련 알림은 %d건이며 평균 이해도는 %d%%입니다. 보충 필요도는 %s(%d%%)로 판단됩니다.",
                keyword, matchedAlerts.size(), avgUnderstanding, reinforcementLevel, reinforcementNeed);

        List<String> occurrenceTimes = matchedAlerts.stream()
                .map(Alert::getCapturedAt)
                .filter(Objects::nonNull)
                .map(TIME_FORMATTER::format)
                .distinct()
                .limit(5)
                .toList();

        return KeywordReportResponse.builder()
                .keyword(keyword)
                .curriculum(curriculum)
                .classId(classId)
                .date(date.toString())
                .alertCount(matchedAlerts.size())
                .avgUnderstanding(avgUnderstanding)
                .reinforcementNeed(reinforcementNeed)
                .reinforcementLevel(reinforcementLevel)
                .report(report)
                .occurrenceTimes(occurrenceTimes)
                .build();
    }

    private DashboardClassResponse buildClassResponse(ClassGroupingKey key, List<Alert> alerts, List<SessionParticipant> participantsForDate) {
        List<Long> alertIds = alerts.stream()
                .map(Alert::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, List<String>> keywordsByAlertId = alertKeywordRepository.findByAlertIdIn(alertIds).stream()
                .collect(Collectors.groupingBy(
                        AlertKeyword::getAlertId,
                        Collectors.mapping(AlertKeyword::getKeyword, Collectors.toList())
                ));

        long participantCount = participantsForDate.stream()
                .filter(participant -> key.curriculum().equals(participant.getCurriculum()))
                .filter(participant -> key.classId().equals(participant.getClassId()))
                .map(SessionParticipant::getStudentId)
                .filter(Objects::nonNull)
                .filter(studentId -> !studentId.isBlank())
                .distinct()
                .count();

        double avgScore = alerts.stream()
                .filter(a -> (a.getTotalStudentCount() != null && a.getTotalStudentCount() > 0) || a.getConfusedScore() != null)
                .mapToDouble(a -> calculateConfusionPercent(a) / 100.0)
                .average()
                .orElse(0.0);

        List<String> topTopics = alerts.stream()
                .filter(a -> a.getUnclearTopic() != null)
                .collect(Collectors.groupingBy(Alert::getUnclearTopic, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<AlertResponse> recentAlerts = alerts.stream()
                .map(alert -> AlertResponse.from(alert, keywordsByAlertId.getOrDefault(alert.getId(), List.of())))
                .collect(Collectors.toList());

        return DashboardClassResponse.builder()
                .curriculum(key.curriculum())
                .classId(key.classId())
                .alertCount(alerts.size())
                .participantCount(participantCount)
                .avgConfusedScore(avgScore)
                .topTopics(topTopics)
                .recentAlerts(recentAlerts)
                .build();
    }

    private FilteredAlertContext getFilteredAlertContext(LocalDate date, String curriculum, String classId) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        List<Alert> alertsForDate = alertRepository.findByCapturedAtBetweenOrderByCapturedAtDesc(startOfDay, endOfDay);

        Set<String> sessionIds = alertsForDate.stream()
                .map(Alert::getSessionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, Session> sessionBySessionId = sessionRepository.findAllBySessionIdIn(sessionIds).stream()
                .collect(Collectors.toMap(Session::getSessionId, session -> session));
        Map<String, SessionParticipant> participantSnapshotBySessionId = sessionParticipantRepository.findByJoinedAtBetween(startOfDay, endOfDay).stream()
                .filter(participant -> participant.getSessionId() != null)
                .collect(Collectors.toMap(
                        SessionParticipant::getSessionId,
                        participant -> participant,
                        (first, second) -> first
                ));

        List<Alert> filteredAlerts = alertsForDate.stream()
                .filter(alert -> matchesCurriculum(sessionBySessionId.get(alert.getSessionId()), alert, participantSnapshotBySessionId.get(alert.getSessionId()), curriculum))
                .filter(alert -> matchesClassId(sessionBySessionId.get(alert.getSessionId()), alert, participantSnapshotBySessionId.get(alert.getSessionId()), classId))
                .toList();

        return new FilteredAlertContext(filteredAlerts, sessionBySessionId);
    }

    private boolean matchesCurriculum(Session session, String curriculum) {
        return matchesCurriculum(session, null, null, curriculum);
    }

    private boolean matchesCurriculum(Session session, Alert alert, String curriculum) {
        return matchesCurriculum(session, alert, null, curriculum);
    }

    private boolean matchesCurriculum(Session session, Alert alert, SessionParticipant participant, String curriculum) {
        if (curriculum == null || curriculum.isBlank()) {
            return true;
        }
        if (session != null && session.getCurriculum() != null) {
            return curriculum.equals(session.getCurriculum());
        }
        if (alert != null && curriculum.equals(alert.getCurriculum())) {
            return true;
        }
        return participant != null && curriculum.equals(participant.getCurriculum());
    }

    private boolean matchesClassId(Session session, String classId) {
        return matchesClassId(session, null, null, classId);
    }

    private boolean matchesClassId(Session session, Alert alert, String classId) {
        return matchesClassId(session, alert, null, classId);
    }

    private boolean matchesClassId(Session session, Alert alert, SessionParticipant participant, String classId) {
        if (classId == null || classId.isBlank() || "전체 반".equals(classId)) {
            return true;
        }
        if (session != null && session.getClassId() != null) {
            return classId.equals(session.getClassId());
        }
        if (alert != null && classId.equals(alert.getClassId())) {
            return true;
        }
        return participant != null && classId.equals(participant.getClassId());
    }

    private int calculateConfusionPercent(Alert alert) {
        if (alert.getTotalStudentCount() != null && alert.getTotalStudentCount() > 0 && alert.getStudentCount() != null) {
            return (int) Math.round((alert.getStudentCount() * 100.0) / alert.getTotalStudentCount());
        }
        return (int) Math.round((alert.getConfusedScore() != null ? alert.getConfusedScore() : 0.0) * 100);
    }

    private record FilteredAlertContext(List<Alert> alerts, Map<String, Session> sessionBySessionId) {
    }

    private record ClassGroupingKey(String curriculum, String classId) {
    }
}

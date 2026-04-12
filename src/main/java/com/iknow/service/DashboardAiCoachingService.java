package com.iknow.service;

import com.iknow.dto.request.DashboardAiCoachingRequest;
import com.iknow.dto.response.DashboardAiCoachingDataResponse;
import com.iknow.dto.response.DashboardAiRecentAlertItemResponse;
import com.iknow.dto.response.DashboardAiSignalItemResponse;
import com.iknow.dto.response.DashboardClassResponse;
import com.iknow.dto.response.SignalBreakdownResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardAiCoachingService {
    private static final String ALL_CLASSES = "전체 반";

    private final DashboardService dashboardService;

    public DashboardAiCoachingDataResponse getCoachingData(DashboardAiCoachingRequest request) {
        List<DashboardClassResponse> filteredItems = dashboardService.getDashboardClasses(request.getDate()).stream()
                .filter(item -> request.getCurriculum() == null || request.getCurriculum().isBlank() || request.getCurriculum().equals(item.getCurriculum()))
                .filter(item -> request.getClassId() == null || request.getClassId().isBlank() || ALL_CLASSES.equals(request.getClassId()) || request.getClassId().equals(item.getClassId()))
                .toList();

        return buildPayload(request, filteredItems);
    }

    private DashboardAiCoachingDataResponse buildPayload(DashboardAiCoachingRequest request, List<DashboardClassResponse> items) {
        int totalAlerts = items.stream().mapToInt(item -> Math.toIntExact(item.getAlertCount())).sum();
        int totalStudents = items.stream().mapToInt(item -> Math.toIntExact(item.getParticipantCount())).sum();
        int avgConfusion = items.isEmpty()
                ? 0
                : (int) Math.round(items.stream().mapToDouble(DashboardClassResponse::getAvgConfusedScore).average().orElse(0.0) * 100);

        List<DashboardAiSignalItemResponse> signals = items.stream()
                .flatMap(item -> item.getSignalBreakdown().stream())
                .collect(Collectors.groupingBy(SignalBreakdownResponse::getSignalType))
                .entrySet().stream()
                .map(entry -> {
                    List<SignalBreakdownResponse> grouped = entry.getValue();
                    long count = grouped.stream().mapToLong(SignalBreakdownResponse::getCount).sum();
                    String label = grouped.stream().map(SignalBreakdownResponse::getLabel).filter(Objects::nonNull).findFirst().orElse(entry.getKey());
                    return DashboardAiSignalItemResponse.builder()
                            .signalType(entry.getKey())
                            .label(label)
                            .count(count)
                            .build();
                })
                .sorted(Comparator.comparingLong(DashboardAiSignalItemResponse::getCount).reversed())
                .toList();

        Set<String> keywords = new LinkedHashSet<>();
        items.stream()
                .flatMap(item -> item.getRecentAlerts().stream())
                .flatMap(alert -> alert.getKeywords().stream())
                .filter(Objects::nonNull)
                .filter(keyword -> !keyword.isBlank())
                .forEach(keywords::add);

        Set<String> topics = new LinkedHashSet<>();
        items.stream()
                .flatMap(item -> item.getTopTopics().stream())
                .filter(Objects::nonNull)
                .filter(topic -> !topic.isBlank())
                .forEach(topics::add);

        List<DashboardAiRecentAlertItemResponse> recentAlerts = items.stream()
                .flatMap(item -> item.getRecentAlerts().stream()
                        .map(alert -> DashboardAiRecentAlertItemResponse.builder()
                                .classId(item.getClassId())
                                .capturedAt(alert.getCapturedAt() != null ? alert.getCapturedAt().toString() : "")
                                .topic(alert.getLectureSummary() != null && !alert.getLectureSummary().isBlank()
                                        ? alert.getLectureSummary()
                                        : alert.getUnclearTopic())
                                .reason(alert.getReason())
                                .confusionPercent(calculateConfusionPercent(alert))
                                .build()))
                .sorted(Comparator.comparing(DashboardAiRecentAlertItemResponse::getCapturedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .toList();

        return DashboardAiCoachingDataResponse.builder()
                .date(request.getDate() != null ? request.getDate().toString() : "")
                .curriculum(request.getCurriculum())
                .classId(request.getClassId())
                .classIds(items.stream().map(DashboardClassResponse::getClassId).filter(Objects::nonNull).distinct().sorted().toList())
                .participantCount(totalStudents)
                .alertCount(totalAlerts)
                .avgConfusionPercent(avgConfusion)
                .topKeywords(keywords.stream().limit(5).toList())
                .topTopics(topics.stream().limit(5).toList())
                .signalBreakdown(signals)
                .recentAlerts(recentAlerts)
                .build();
    }

    private int calculateConfusionPercent(com.iknow.dto.response.AlertResponse alert) {
        if (alert.getTotalStudentCount() != null && alert.getTotalStudentCount() > 0 && alert.getStudentCount() != null) {
            return (int) Math.round((alert.getStudentCount() * 100.0) / alert.getTotalStudentCount());
        }
        return (int) Math.round((alert.getConfusedScore() != null ? alert.getConfusedScore() : 0.0) * 100);
    }
}

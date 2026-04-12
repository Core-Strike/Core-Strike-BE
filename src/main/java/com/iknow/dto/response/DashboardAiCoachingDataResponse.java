package com.iknow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardAiCoachingDataResponse {
    private String date;
    private String curriculum;
    private String classId;
    private List<String> classIds;
    private int participantCount;
    private int alertCount;
    private int avgConfusionPercent;
    private List<String> topKeywords;
    private List<String> topTopics;
    private List<DashboardAiSignalItemResponse> signalBreakdown;
    private List<DashboardAiRecentAlertItemResponse> recentAlerts;
}

package com.iknow.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardAiRecentAlertItemResponse {
    private String classId;
    private String capturedAt;
    private String topic;
    private String reason;
    private int confusionPercent;
}

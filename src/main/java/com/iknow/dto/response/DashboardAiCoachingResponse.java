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
public class DashboardAiCoachingResponse {
    private String summary;
    private String priorityLevel;
    private List<String> coachingTips;
    private List<String> reExplainTopics;
    private List<String> studentSignals;
    private String recommendedActionNow;
    private List<String> sampleMentions;
}

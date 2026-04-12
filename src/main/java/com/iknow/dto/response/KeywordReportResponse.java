package com.iknow.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class KeywordReportResponse {
    private String keyword;
    private String curriculum;
    private String classId;
    private String date;
    private long alertCount;
    private int avgUnderstanding;
    private int reinforcementNeed;
    private String reinforcementLevel;
    private String report;
    private List<String> occurrenceTimes;
}

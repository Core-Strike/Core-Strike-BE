package com.iknow.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class DashboardAiCoachingRequest {
    private LocalDate date;
    private String curriculum;
    private String classId;
}

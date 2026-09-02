package com.AI_Interview.AI_Interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DashboardResponse {

    private int totalInterviews;
    private int completedInterviews;
    private double averageScore;
}
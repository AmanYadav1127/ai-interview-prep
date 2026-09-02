package com.AI_Interview.AI_Interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AnswerEvaluationResponse {

    private double score;
    private double technicalAccuracy;
    private double completeness;
    private double clarity;

    private String correctPoints;
    private String missingPoints;
    private String feedback;
}
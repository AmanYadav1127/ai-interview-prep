package com.AI_Interview.AI_Interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AnswerEvaluationResponse {

    private double score;
    private double technicalAccuracy;
    private double completeness;
    private double clarity;

    private String correctPoints;
    private String missingPoints;
    private String feedback;
    private String idealAnswer;

    public AnswerEvaluationResponse(
            double score,
            double technicalAccuracy,
            double completeness,
            double clarity,
            String correctPoints,
            String missingPoints,
            String feedback) {
        this.score = score;
        this.technicalAccuracy = technicalAccuracy;
        this.completeness = completeness;
        this.clarity = clarity;
        this.correctPoints = correctPoints;
        this.missingPoints = missingPoints;
        this.feedback = feedback;
        this.idealAnswer = "";
    }
}
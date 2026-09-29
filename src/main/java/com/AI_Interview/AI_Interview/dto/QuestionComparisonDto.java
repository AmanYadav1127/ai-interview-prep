package com.AI_Interview.AI_Interview.dto;

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
public class QuestionComparisonDto {

    private Long questionId;
    private int questionOrder;
    private String questionText;
    private String topic;
    private String difficulty;
    private String questionType;

    private String candidateAnswer;
    private String idealAnswer;

    // How much percent is correct from actual answer (0–100%)
    private double accuracyPercentage;

    private double score;
    private double technicalAccuracy;
    private double completeness;
    private double clarity;

    private String feedback;
    private String correctPoints;
    private String missingPoints;
}

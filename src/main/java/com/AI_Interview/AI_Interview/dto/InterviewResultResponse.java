package com.AI_Interview.AI_Interview.dto;

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
public class InterviewResultResponse {

    private Long id;
    private double overallScore;
    private double technicalScore;
    private double communicationScore;
    private String strengths;
    private String weaknesses;
    private String recommendations;

    private InterviewSummaryDto interview;
    private List<QuestionComparisonDto> questions;
}

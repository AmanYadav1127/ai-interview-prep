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
public class InterviewSummaryDto {

    private Long id;
    private String title;
    private String role;
    private String mode;
    private String type;
    private String difficulty;
    private int questionLimit;
    private Integer durationMinutes;
    private String status;
    private String startedAt;
    private String completedAt;
}

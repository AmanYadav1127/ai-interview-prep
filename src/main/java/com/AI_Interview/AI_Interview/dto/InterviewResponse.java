package com.AI_Interview.AI_Interview.dto;

import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.InterviewMode;
import com.AI_Interview.AI_Interview.enums.InterviewType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InterviewResponse {

    private Long id;
    private String title;
    private String role;
    private InterviewMode mode;
    private InterviewType type;
    private Difficulty difficulty;
    private int questionLimit;
    private String status;
}
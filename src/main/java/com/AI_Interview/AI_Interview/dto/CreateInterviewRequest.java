package com.AI_Interview.AI_Interview.dto;

import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.InterviewMode;
import com.AI_Interview.AI_Interview.enums.InterviewType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInterviewRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String role;

    private InterviewMode mode;

    private InterviewType type;

    private Difficulty difficulty;

    @Min(1)
    private int questionLimit;
}
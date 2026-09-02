package com.AI_Interview.AI_Interview.dto;

import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NextQuestionResponse {

    private String question;
    private String topic;
    private Difficulty difficulty;
    private QuestionType questionType;
}
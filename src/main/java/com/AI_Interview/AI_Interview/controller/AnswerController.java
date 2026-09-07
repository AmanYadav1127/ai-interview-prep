package com.AI_Interview.AI_Interview.controller;

import com.AI_Interview.AI_Interview.dto.SubmitAnswerRequest;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;

    @PostMapping("/{questionId}")
    public ResponseEntity<Question> submitAnswer(
            @PathVariable Long questionId,
            @RequestBody SubmitAnswerRequest request) {

        return ResponseEntity.ok(
                answerService.submitAnswer(
                        questionId,
                        request
                )
        );
    }
}
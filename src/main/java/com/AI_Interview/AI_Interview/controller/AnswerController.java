package com.AI_Interview.AI_Interview.controller;

import com.AI_Interview.AI_Interview.dto.SubmitAnswerRequest;
import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.service.AnswerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;

    @PostMapping("/{questionId}")
    public ResponseEntity<Answer> submitAnswer(
            @PathVariable Long questionId,
            @Valid @RequestBody SubmitAnswerRequest request) {

        return ResponseEntity.ok(
                answerService.submitAnswer(questionId, request)
        );
    }

    @GetMapping("/{questionId}")
    public ResponseEntity<Answer> getAnswer(
            @PathVariable Long questionId) {

        return ResponseEntity.ok(
                answerService.getAnswer(questionId)
        );
    }
}
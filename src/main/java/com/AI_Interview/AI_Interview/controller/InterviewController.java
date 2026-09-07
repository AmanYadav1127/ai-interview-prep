package com.AI_Interview.AI_Interview.controller;

import com.AI_Interview.AI_Interview.dto.CreateInterviewRequest;
import com.AI_Interview.AI_Interview.entity.Interview;
import com.AI_Interview.AI_Interview.entity.InterviewResult;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.exception.ResourceNotFoundException;
import com.AI_Interview.AI_Interview.repository.InterviewResultRepository;
import com.AI_Interview.AI_Interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;
    private final InterviewResultRepository interviewResultRepository;

    @PostMapping
    public ResponseEntity<Interview> createInterview(
            @RequestBody CreateInterviewRequest request,
            Authentication authentication) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                interviewService.createInterview(request, email)
        );
    }

    @GetMapping
    public ResponseEntity<List<Interview>> getMyInterviews(
            Authentication authentication) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                interviewService.getUserInterviews(email)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Interview> getInterview(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                interviewService.getInterviewById(id)
        );
    }

    @PostMapping("/{interviewId}/start")
    public ResponseEntity<Question> startInterview(
            @PathVariable Long interviewId) {

        return ResponseEntity.ok(
                interviewService.startInterview(interviewId)
        );
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<String> completeInterview(
            @PathVariable Long id) {

        interviewService.completeInterview(id);

        return ResponseEntity.ok("Interview completed");
    }

    // Get final AI interview report
    @GetMapping("/{interviewId}/result")
    public ResponseEntity<InterviewResult> getInterviewResult(
            @PathVariable Long interviewId) {

        Interview interview =
                interviewService.getInterviewById(interviewId);

        InterviewResult result =
                interviewResultRepository.findByInterview(interview)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Interview result not found"
                                ));

        return ResponseEntity.ok(result);
    }
}
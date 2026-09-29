package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.CreateInterviewRequest;
import com.AI_Interview.AI_Interview.dto.NextQuestionResponse;
import com.AI_Interview.AI_Interview.entity.Interview;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.entity.User;
import com.AI_Interview.AI_Interview.exception.ResourceNotFoundException;
import com.AI_Interview.AI_Interview.repository.InterviewRepository;
import com.AI_Interview.AI_Interview.repository.QuestionRepository;
import com.AI_Interview.AI_Interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final AIService aiService;

    // =========================================================
    // 1. CREATE INTERVIEW
    // =========================================================

    public Interview createInterview(
            CreateInterviewRequest request,
            String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        Interview interview = new Interview();

        interview.setUser(user);
        interview.setTitle(request.getTitle());
        interview.setRole(request.getRole());
        interview.setMode(request.getMode());
        interview.setType(request.getType());
        interview.setDifficulty(request.getDifficulty());
        interview.setQuestionLimit(request.getQuestionLimit());
        interview.setDurationMinutes(
                request.getDurationMinutes() != null && request.getDurationMinutes() > 0
                        ? request.getDurationMinutes()
                        : 10
        );

        interview.setStatus("NOT_STARTED");
        interview.setStartedAt(null);
        interview.setCompletedAt(null);

        return interviewRepository.save(interview);
    }

    // =========================================================
    // 2. GET INTERVIEW BY ID
    // =========================================================

    public Interview getInterviewById(Long interviewId) {

        return interviewRepository.findById(interviewId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Interview not found"));
    }

    // =========================================================
    // 3. GET USER'S INTERVIEWS
    // =========================================================

    public List<Interview> getUserInterviews(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        return interviewRepository.findByUser(user);
    }

    // =========================================================
    // 4. START INTERVIEW
    // =========================================================

    public Question startInterview(Long interviewId) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Interview not found"));

        // If questions already exist for this interview, return the latest
        List<Question> existingQuestions =
                questionRepository.findByInterviewOrderByQuestionOrderAsc(interview);

        if (!existingQuestions.isEmpty()) {
            return existingQuestions.get(existingQuestions.size() - 1);
        }

        // Start interview
        if (interview.getStartedAt() == null) {
            interview.setStartedAt(LocalDateTime.now());
            interview.setStatus("IN_PROGRESS");
            interviewRepository.save(interview);
        }

        // Generate initial AI question
        NextQuestionResponse response =
                aiService.generateInitialQuestion(
                        interview.getRole(),
                        interview.getDifficulty(),
                        interview.getType()
                );

        // Create question
        Question question = new Question();

        question.setInterview(interview);
        question.setQuestionText(response.getQuestion());
        question.setTopic(response.getTopic());
        question.setDifficulty(response.getDifficulty());
        question.setQuestionType(response.getQuestionType());
        question.setQuestionOrder(0);

        return questionRepository.save(question);
    }

    // =========================================================
    // 5. COMPLETE INTERVIEW
    // =========================================================

    public Interview completeInterview(Long interviewId) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Interview not found"));

        interview.setStatus("COMPLETED");
        interview.setCompletedAt(LocalDateTime.now());

        return interviewRepository.save(interview);
    }
}
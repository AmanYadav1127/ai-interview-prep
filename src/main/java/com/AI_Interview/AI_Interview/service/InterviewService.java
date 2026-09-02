package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.CreateInterviewRequest;
import com.AI_Interview.AI_Interview.dto.InterviewResponse;
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

    public InterviewResponse createInterview(
            CreateInterviewRequest request,
            String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Interview interview = new Interview();

        interview.setUser(user);
        interview.setTitle(request.getTitle());
        interview.setRole(request.getRole());
        interview.setMode(request.getMode());
        interview.setType(request.getType());
        interview.setDifficulty(request.getDifficulty());
        interview.setQuestionLimit(request.getQuestionLimit());
        interview.setStatus("NOT_STARTED");
        interview.setStartedAt(null);

        Interview saved = interviewRepository.save(interview);

        return new InterviewResponse(
                saved.getId(),
                saved.getTitle(),
                saved.getRole(),
                saved.getMode(),
                saved.getType(),
                saved.getDifficulty(),
                saved.getQuestionLimit(),
                saved.getStatus()
        );
    }

    public List<Interview> getUserInterviews(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return interviewRepository.findByUser(user);
    }

    public Interview getInterviewById(Long id) {

        return interviewRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Interview not found"));
    }

    public Question startInterview(Long interviewId) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview not found"));

        if (interview.getStartedAt() == null) {
            interview.setStartedAt(LocalDateTime.now());
        }

        NextQuestionResponse response = aiService.generateInitialQuestion(
                interview.getRole(),
                interview.getDifficulty()
        );

        Question question = new Question();

        question.setInterview(interview);
        question.setQuestionText(response.getQuestion());
        question.setTopic(response.getTopic());
        question.setDifficulty(response.getDifficulty());
        question.setQuestionType(response.getQuestionType());

        interviewRepository.save(interview);

        return questionRepository.save(question);
    }

    public void completeInterview(Long id) {

        Interview interview = getInterviewById(id);

        interview.setStatus("COMPLETED");
        interview.setCompletedAt(LocalDateTime.now());

        interviewRepository.save(interview);
    }
}
package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.SubmitAnswerRequest;
import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.repository.AnswerRepository;
import com.AI_Interview.AI_Interview.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;

    public Answer submitAnswer(
            Long questionId,
            SubmitAnswerRequest request) {

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new RuntimeException("Question not found"));

        Answer answer = new Answer();

        answer.setQuestion(question);
        answer.setAnswerText(request.getAnswerText());
        answer.setAnsweredAt(LocalDateTime.now());

        return answerRepository.save(answer);
    }

    public Answer getAnswer(Long questionId) {

        Question question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new RuntimeException("Question not found"));

        return answerRepository.findByQuestion(question)
                .orElseThrow(() ->
                        new RuntimeException("Answer not found"));
    }
}
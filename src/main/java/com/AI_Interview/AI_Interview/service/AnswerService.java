package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.AnswerEvaluationResponse;
import com.AI_Interview.AI_Interview.dto.NextQuestionResponse;
import com.AI_Interview.AI_Interview.dto.SubmitAnswerRequest;
import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.entity.Evaluation;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.exception.ResourceNotFoundException;
import com.AI_Interview.AI_Interview.repository.AnswerRepository;
import com.AI_Interview.AI_Interview.repository.EvaluationRepository;
import com.AI_Interview.AI_Interview.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final EvaluationRepository evaluationRepository;
    private final AIService aiService;

    public Question submitAnswer(
            Long questionId,
            SubmitAnswerRequest request) {

        // 1. Find current question
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Question not found"));

        // 2. Save candidate answer
        Answer answer = new Answer();

        answer.setQuestion(question);
        answer.setAnswerText(request.getAnswerText());
        answer.setAnsweredAt(LocalDateTime.now());

        Answer savedAnswer = answerRepository.save(answer);

        // 3. AI evaluates the answer
        AnswerEvaluationResponse evaluationResponse =
                aiService.evaluateAnswer(
                        question.getQuestionText(),
                        request.getAnswerText()
                );

        // 4. Save evaluation in database
        Evaluation evaluation = new Evaluation();

        evaluation.setAnswer(savedAnswer);
        evaluation.setScore(evaluationResponse.getScore());
        evaluation.setTechnicalAccuracy(
                evaluationResponse.getTechnicalAccuracy()
        );
        evaluation.setCompleteness(
                evaluationResponse.getCompleteness()
        );
        evaluation.setClarity(
                evaluationResponse.getClarity()
        );
        evaluation.setFeedback(
                evaluationResponse.getFeedback()
        );
        evaluation.setCorrectPoints(
                evaluationResponse.getCorrectPoints()
        );
        evaluation.setMissingPoints(
                evaluationResponse.getMissingPoints()
        );

        evaluationRepository.save(evaluation);

        // 5. AI generates next adaptive question
        NextQuestionResponse nextQuestionResponse =
                aiService.generateNextQuestion(
                        question.getInterview().getRole(),
                        question.getQuestionText(),
                        request.getAnswerText(),
                        evaluationResponse
                );

        // 6. Create next question
        Question nextQuestion = new Question();

        nextQuestion.setInterview(question.getInterview());
        nextQuestion.setQuestionText(
                nextQuestionResponse.getQuestion()
        );
        nextQuestion.setTopic(
                nextQuestionResponse.getTopic()
        );
        nextQuestion.setDifficulty(
                nextQuestionResponse.getDifficulty()
        );
        nextQuestion.setQuestionType(
                nextQuestionResponse.getQuestionType()
        );
        nextQuestion.setQuestionOrder(
                question.getQuestionOrder() + 1
        );

        // 7. Save next question
        return questionRepository.save(nextQuestion);
    }
}
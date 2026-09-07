package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.AnswerEvaluationResponse;
import com.AI_Interview.AI_Interview.dto.NextQuestionResponse;
import com.AI_Interview.AI_Interview.dto.SubmitAnswerRequest;
import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.entity.Evaluation;
import com.AI_Interview.AI_Interview.entity.Interview;
import com.AI_Interview.AI_Interview.entity.InterviewResult;
import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.exception.ResourceNotFoundException;
import com.AI_Interview.AI_Interview.repository.AnswerRepository;
import com.AI_Interview.AI_Interview.repository.EvaluationRepository;
import com.AI_Interview.AI_Interview.repository.InterviewResultRepository;
import com.AI_Interview.AI_Interview.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final EvaluationRepository evaluationRepository;
    private final InterviewResultRepository interviewResultRepository;
    private final AIService aiService;

    public Question submitAnswer(
            Long questionId,
            SubmitAnswerRequest request) {

        // 1. Find current question
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Question not found"));

        Interview interview = question.getInterview();

        // 2. Save candidate answer
        Answer answer = new Answer();

        answer.setQuestion(question);
        answer.setAnswerText(request.getAnswerText());
        answer.setAnsweredAt(LocalDateTime.now());

        Answer savedAnswer = answerRepository.save(answer);

        // 3. AI evaluates answer
        AnswerEvaluationResponse evaluationResponse =
                aiService.evaluateAnswer(
                        question.getQuestionText(),
                        request.getAnswerText()
                );

        // 4. Save evaluation
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
        evaluation.setCorrectPoints(
                evaluationResponse.getCorrectPoints()
        );
        evaluation.setMissingPoints(
                evaluationResponse.getMissingPoints()
        );
        evaluation.setFeedback(
                evaluationResponse.getFeedback()
        );

        evaluationRepository.save(evaluation);

        // 5. Check question limit
        int currentQuestionNumber =
                question.getQuestionOrder() + 1;

        if (currentQuestionNumber >= interview.getQuestionLimit()) {

            interview.setStatus("COMPLETED");
            interview.setCompletedAt(LocalDateTime.now());

            // 6. Get all answers of this interview
            List<Answer> answers =
                    answerRepository.findByQuestion_Interview(interview);

            // 7. Get all evaluations
            List<Evaluation> evaluations =
                    evaluationRepository
                            .findByAnswer_Question_Interview(interview);

            // 8. Build interview data for AI
            StringBuilder interviewData = new StringBuilder();

            for (int i = 0; i < answers.size(); i++) {

                Answer currentAnswer = answers.get(i);

                interviewData
                        .append("Question: ")
                        .append(
                                currentAnswer
                                        .getQuestion()
                                        .getQuestionText()
                        )
                        .append("\n");

                interviewData
                        .append("Answer: ")
                        .append(currentAnswer.getAnswerText())
                        .append("\n");

                if (i < evaluations.size()) {

                    Evaluation currentEvaluation =
                            evaluations.get(i);

                    interviewData
                            .append("Score: ")
                            .append(currentEvaluation.getScore())
                            .append("\n");

                    interviewData
                            .append("Technical Accuracy: ")
                            .append(
                                    currentEvaluation
                                            .getTechnicalAccuracy()
                            )
                            .append("\n");

                    interviewData
                            .append("Completeness: ")
                            .append(
                                    currentEvaluation
                                            .getCompleteness()
                            )
                            .append("\n");

                    interviewData
                            .append("Clarity: ")
                            .append(
                                    currentEvaluation
                                            .getClarity()
                            )
                            .append("\n");

                    interviewData
                            .append("Correct Points: ")
                            .append(
                                    currentEvaluation
                                            .getCorrectPoints()
                            )
                            .append("\n");

                    interviewData
                            .append("Missing Points: ")
                            .append(
                                    currentEvaluation
                                            .getMissingPoints()
                            )
                            .append("\n");

                    interviewData
                            .append("Feedback: ")
                            .append(
                                    currentEvaluation
                                            .getFeedback()
                            )
                            .append("\n");
                }

                interviewData.append("\n");
            }

            // 9. Generate final AI report
            var report =
                    aiService.generateFinalReport(
                            interview.getRole(),
                            interviewData.toString()
                    );

            // 10. Save InterviewResult
            InterviewResult result = new InterviewResult();

            result.setInterview(interview);

            result.setOverallScore(
                    report.get("overallScore").asDouble()
            );

            result.setTechnicalScore(
                    report.get("technicalScore").asDouble()
            );

            result.setCommunicationScore(
                    report.get("communicationScore").asDouble()
            );

            result.setStrengths(
                    report.get("strengths").asText()
            );

            result.setWeaknesses(
                    report.get("weaknesses").asText()
            );

            result.setRecommendations(
                    report.get("recommendations").asText()
            );

            interviewResultRepository.save(result);

            // 11. Return final question
            return questionRepository.save(question);
        }

        // 12. Generate next adaptive question
        NextQuestionResponse nextQuestionResponse =
                aiService.generateNextQuestion(
                        interview.getRole(),
                        question.getQuestionText(),
                        request.getAnswerText(),
                        evaluationResponse
                );

        // 13. Create next question
        Question nextQuestion = new Question();

        nextQuestion.setInterview(interview);

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

        // 14. Keep interview in progress
        interview.setStatus("IN_PROGRESS");

        // 15. Save next question
        return questionRepository.save(nextQuestion);
    }
}
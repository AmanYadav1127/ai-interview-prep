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
import com.AI_Interview.AI_Interview.repository.InterviewRepository;
import com.AI_Interview.AI_Interview.repository.InterviewResultRepository;
import com.AI_Interview.AI_Interview.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final EvaluationRepository evaluationRepository;
    private final InterviewResultRepository interviewResultRepository;
    private final InterviewRepository interviewRepository;
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
        evaluation.setIdealAnswer(
                evaluationResponse.getIdealAnswer()
        );

        evaluationRepository.save(evaluation);

        // 5. Check question limit
        int currentQuestionNumber =
                question.getQuestionOrder() + 1;

        if (currentQuestionNumber >= interview.getQuestionLimit()) {

            interview.setStatus("COMPLETED");
            interview.setCompletedAt(LocalDateTime.now());
            interviewRepository.save(interview);

            // 6. Generate the final AI report.
            try {
                generateAndSaveFinalReport(interview);
            } catch (Exception e) {
                log.warn(
                        "Final report generation failed for interview {}: {}",
                        interview.getId(),
                        e.getMessage()
                );
            }

            // 7. Return final question
            return questionRepository.save(question);
        }

        // 8. Generate next adaptive question
        List<String> askedQuestions = answerRepository
                .findByQuestion_Interview(interview)
                .stream()
                .map(answerEntity ->
                        answerEntity.getQuestion().getQuestionText())
                .toList();

        NextQuestionResponse nextQuestionResponse =
                aiService.generateNextQuestion(
                        interview.getRole(),
                        question.getQuestionText(),
                        request.getAnswerText(),
                        evaluationResponse,
                        askedQuestions,
                        question.getDifficulty(),
                        interview.getType(),
                        currentQuestionNumber,
                        interview.getQuestionLimit()
                );

        // 9. Create next question
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

        // 10. Keep interview in progress
        interview.setStatus("IN_PROGRESS");

        // 11. Save next question
        return questionRepository.save(nextQuestion);
    }

    // =========================================================
    // FINAL REPORT GENERATION
    // (called after the last answer, and retried on demand
    //  by the result endpoint if it failed earlier)
    // =========================================================

    public InterviewResult generateAndSaveFinalReport(
            Interview interview) {

        // Idempotent — never create a duplicate report row
        Optional<InterviewResult> existing =
                interviewResultRepository.findByInterview(interview);

        if (existing.isPresent()) {
            return existing.get();
        }

        // 1. Get all answers of this interview (in question order)
        List<Answer> answers = answerRepository
                .findByQuestion_InterviewOrderByQuestion_IdAsc(interview);

        if (answers.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No answers were recorded for this interview"
            );
        }

        // 2. Build interview data for AI
        StringBuilder interviewData = new StringBuilder();

        for (Answer currentAnswer : answers) {

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

            // Each answer is paired with its own evaluation
            evaluationRepository
                    .findByAnswer(currentAnswer)
                    .ifPresent(currentEvaluation -> {

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
                    });

            interviewData.append("\n");
        }

        // 3. Generate the final report.
        //
        // The AI path is preferred; if it is unavailable the report is
        // computed offline from the stored per-answer evaluations.
        JsonNode report = null;

        try {
            report = aiService.generateFinalReport(
                    interview.getRole(),
                    interviewData.toString()
            );
        } catch (Exception e) {
            log.warn(
                    "AI final report failed for interview {}: {}",
                    interview.getId(),
                    e.getMessage()
            );
        }

        // 4. Save InterviewResult
        InterviewResult result = new InterviewResult();

        result.setInterview(interview);

        if (report != null) {

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

        } else {

            List<Evaluation> evaluations = evaluationRepository
                    .findByAnswer_Question_Interview(interview);

            applyOfflineReport(result, answers, evaluations);
        }

        return interviewResultRepository.save(result);
    }

    // =========================================================
    // OFFLINE FINAL REPORT
    // (used when the AI service is unavailable — the report is
    //  computed from the stored per-answer evaluations)
    // =========================================================

    private void applyOfflineReport(
            InterviewResult result,
            List<Answer> answers,
            List<Evaluation> evaluations) {

        double overall = 0;
        double technical = 0;
        double communication = 0;

        if (!evaluations.isEmpty()) {

            for (Evaluation evaluation : evaluations) {
                overall += evaluation.getScore();
                technical += evaluation.getTechnicalAccuracy();
                communication += evaluation.getClarity();
            }

            int count = evaluations.size();

            overall /= count;
            technical /= count;
            communication /= count;
        }

        result.setOverallScore(overall);
        result.setTechnicalScore(technical);
        result.setCommunicationScore(communication);

        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        if (technical >= 70) {
            strengths.add(
                    "Solid technical coverage of the questions asked."
            );
        }

        if (communication >= 70) {
            strengths.add(
                    "Answers were clear and well structured."
            );
        }

        strengths.add(
                "Completed the full interview session ("
                        + answers.size() + " questions)."
        );

        if (technical < 70) {
            weaknesses.add(
                    "Some answers missed key technical points "
                            + "mentioned in the questions."
            );
        }

        if (communication < 70) {
            weaknesses.add(
                    "Answers could be more structured: open with the "
                            + "core idea, add detail, then summarize."
            );
        }

        if (weaknesses.isEmpty()) {
            weaknesses.add(
                    "No significant weaknesses detected in this session."
            );
        }

        recommendations.add(
                "Practice explaining core concepts out loud under "
                        + "time pressure."
        );

        recommendations.add(
                "Review the topics listed as missing in each "
                        + "question's feedback."
        );

        recommendations.add(
                "Note: the AI service was unavailable, so this report "
                        + "was generated from offline rule-based "
                        + "evaluation. Scores are indicative only."
        );

        result.setStrengths(String.join("\n", strengths));
        result.setWeaknesses(String.join("\n", weaknesses));
        result.setRecommendations(String.join("\n", recommendations));
    }

    // =========================================================
    // GET COMPLETE INTERVIEW RESULT RESPONSE WITH COMPARISON
    // =========================================================

    public com.AI_Interview.AI_Interview.dto.InterviewResultResponse getInterviewResultResponse(Long interviewId) {

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Interview not found"));

        InterviewResult result = interviewResultRepository.findByInterview(interview)
                .orElseGet(() -> {
                    if (!"COMPLETED".equals(interview.getStatus())) {
                        interview.setStatus("COMPLETED");
                        interview.setCompletedAt(LocalDateTime.now());
                        interviewRepository.save(interview);
                    }
                    return generateAndSaveFinalReport(interview);
                });

        List<Answer> answers = answerRepository
                .findByQuestion_InterviewOrderByQuestion_IdAsc(interview);

        List<com.AI_Interview.AI_Interview.dto.QuestionComparisonDto> questionDtos = new ArrayList<>();

        for (int i = 0; i < answers.size(); i++) {
            Answer ans = answers.get(i);
            Question q = ans.getQuestion();
            Evaluation eval = evaluationRepository.findByAnswer(ans).orElse(null);

            String ideal = eval != null && eval.getIdealAnswer() != null && !eval.getIdealAnswer().isBlank()
                    ? eval.getIdealAnswer()
                    : aiService.generateIdealAnswer(q.getQuestionText(), interview.getRole());

            if (eval != null && (eval.getIdealAnswer() == null || eval.getIdealAnswer().isBlank())) {
                eval.setIdealAnswer(ideal);
                evaluationRepository.save(eval);
            }

            double accuracy = eval != null ? eval.getTechnicalAccuracy() : 0.0;
            double score = eval != null ? eval.getScore() : 0.0;
            double completeness = eval != null ? eval.getCompleteness() : 0.0;
            double clarity = eval != null ? eval.getClarity() : 0.0;
            String feedback = eval != null && eval.getFeedback() != null ? eval.getFeedback() : "Answer recorded.";
            String correctPoints = eval != null && eval.getCorrectPoints() != null ? eval.getCorrectPoints() : "";
            String missingPoints = eval != null && eval.getMissingPoints() != null ? eval.getMissingPoints() : "";

            questionDtos.add(
                    com.AI_Interview.AI_Interview.dto.QuestionComparisonDto.builder()
                            .questionId(q.getId())
                            .questionOrder(q.getQuestionOrder() > 0 ? q.getQuestionOrder() : (i + 1))
                            .questionText(q.getQuestionText())
                            .topic(q.getTopic() != null ? q.getTopic() : "General")
                            .difficulty(q.getDifficulty() != null ? q.getDifficulty().name() : "MEDIUM")
                            .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : "TECHNICAL")
                            .candidateAnswer(ans.getAnswerText())
                            .idealAnswer(ideal)
                            .accuracyPercentage(accuracy)
                            .score(score)
                            .technicalAccuracy(accuracy)
                            .completeness(completeness)
                            .clarity(clarity)
                            .feedback(feedback)
                            .correctPoints(correctPoints)
                            .missingPoints(missingPoints)
                            .build()
            );
        }

        com.AI_Interview.AI_Interview.dto.InterviewSummaryDto interviewSummary =
                com.AI_Interview.AI_Interview.dto.InterviewSummaryDto.builder()
                        .id(interview.getId())
                        .title(interview.getTitle())
                        .role(interview.getRole())
                        .mode(interview.getMode() != null ? interview.getMode().name() : "TEXT")
                        .type(interview.getType() != null ? interview.getType().name() : "LIVE_ADAPTIVE")
                        .difficulty(interview.getDifficulty() != null ? interview.getDifficulty().name() : "MEDIUM")
                        .questionLimit(interview.getQuestionLimit())
                        .durationMinutes(interview.getDurationMinutes() != null ? interview.getDurationMinutes() : 10)
                        .status(interview.getStatus())
                        .startedAt(interview.getStartedAt() != null ? interview.getStartedAt().toString() : "")
                        .completedAt(interview.getCompletedAt() != null ? interview.getCompletedAt().toString() : "")
                        .build();

        return com.AI_Interview.AI_Interview.dto.InterviewResultResponse.builder()
                .id(result.getId())
                .overallScore(result.getOverallScore())
                .technicalScore(result.getTechnicalScore())
                .communicationScore(result.getCommunicationScore())
                .strengths(result.getStrengths())
                .weaknesses(result.getWeaknesses())
                .recommendations(result.getRecommendations())
                .interview(interviewSummary)
                .questions(questionDtos)
                .build();
    }
}
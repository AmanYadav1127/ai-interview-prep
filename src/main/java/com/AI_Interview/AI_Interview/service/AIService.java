package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.AnswerEvaluationResponse;
import com.AI_Interview.AI_Interview.dto.NextQuestionResponse;
import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.QuestionType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class AIService {

    private final RestClient restClient;
    private final JsonMapper objectMapper;

    @Value("${openai.model}")
    private String model;

    public AIService(
            @Value("${openai.api-key}") String apiKey,
            JsonMapper objectMapper) {

        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .defaultHeader(
                        "Content-Type",
                        "application/json"
                )
                .build();
    }

    // =========================================================
    // 1. GENERATE INITIAL QUESTION
    // =========================================================

    public NextQuestionResponse generateInitialQuestion(
            String role,
            Difficulty difficulty) {

        String instructions = """
                You are an expert technical interviewer.

                Generate the first interview question for this role.

                Role: %s
                Difficulty: %s

                The question must be relevant to the role and difficulty.

                Return ONLY valid JSON:

                {
                  "question": "question text",
                  "topic": "topic name"
                }

                Do not return markdown.
                Do not return anything outside JSON.
                """.formatted(role, difficulty);

        String json = callAI(
                instructions,
                "Generate the first interview question."
        );

        try {

            JsonNode node = objectMapper.readTree(json);

            return new NextQuestionResponse(
                    node.get("question").asText(),
                    node.get("topic").asText(),
                    difficulty,
                    QuestionType.INITIAL
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI question response",
                    e
            );
        }
    }

    // =========================================================
    // 2. EVALUATE CANDIDATE ANSWER
    // =========================================================

    public AnswerEvaluationResponse evaluateAnswer(
            String question,
            String answer) {

        String instructions = """
                You are an expert technical interviewer.

                Evaluate the candidate's answer.

                Question:
                %s

                Candidate Answer:
                %s

                Evaluate the answer on:

                1. Overall score from 0 to 100
                2. Technical accuracy from 0 to 100
                3. Completeness from 0 to 100
                4. Clarity from 0 to 100
                5. Correct points
                6. Missing points
                7. Constructive feedback

                Return ONLY valid JSON:

                {
                  "score": 0.0,
                  "technicalAccuracy": 0.0,
                  "completeness": 0.0,
                  "clarity": 0.0,
                  "correctPoints": "string",
                  "missingPoints": "string",
                  "feedback": "string"
                }

                Scores must be numbers between 0 and 100.

                Do not return markdown.
                Do not return anything outside JSON.
                """.formatted(question, answer);

        String json = callAI(
                instructions,
                "Evaluate the candidate answer."
        );

        try {

            JsonNode node = objectMapper.readTree(json);

            return new AnswerEvaluationResponse(
                    node.get("score").asDouble(),
                    node.get("technicalAccuracy").asDouble(),
                    node.get("completeness").asDouble(),
                    node.get("clarity").asDouble(),
                    node.get("correctPoints").asText(),
                    node.get("missingPoints").asText(),
                    node.get("feedback").asText()
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI evaluation response",
                    e
            );
        }
    }

    // =========================================================
    // 3. GENERATE NEXT ADAPTIVE QUESTION
    // =========================================================

    public NextQuestionResponse generateNextQuestion(
            String role,
            String previousQuestion,
            String answer,
            AnswerEvaluationResponse evaluation) {

        String instructions = """
                You are conducting a LIVE ADAPTIVE technical interview.

                Interview role:
                %s

                Previous question:
                %s

                Candidate answer:
                %s

                Previous answer evaluation:

                Overall score: %.2f
                Technical accuracy: %.2f
                Completeness: %.2f
                Clarity: %.2f

                Correct points:
                %s

                Missing points:
                %s

                Feedback:
                %s

                Now decide the best NEXT question.

                IMPORTANT:

                - Do NOT use a fixed question list.
                - Base the next question on the candidate's actual answer.
                - If the candidate is weak, ask an easier clarification or follow-up.
                - If the candidate is partially correct, test the missing concept.
                - If the candidate is strong, increase difficulty.
                - You may ask a deeper technical question.
                - You may ask a practical scenario question.
                - You may move to a related new topic if appropriate.
                - The next question should not simply repeat the previous question.

                Question types allowed:

                FOLLOW_UP
                DEEP_DIVE
                CLARIFICATION
                SCENARIO
                NEW_TOPIC

                Difficulty allowed:

                EASY
                MEDIUM
                HARD

                Return ONLY valid JSON:

                {
                  "question": "next question",
                  "topic": "topic",
                  "difficulty": "EASY",
                  "questionType": "FOLLOW_UP"
                }

                Do not return markdown.
                Do not return anything outside JSON.
                """.formatted(
                role,
                previousQuestion,
                answer,
                evaluation.getScore(),
                evaluation.getTechnicalAccuracy(),
                evaluation.getCompleteness(),
                evaluation.getClarity(),
                evaluation.getCorrectPoints(),
                evaluation.getMissingPoints(),
                evaluation.getFeedback()
        );

        String json = callAI(
                instructions,
                "Generate the next adaptive interview question."
        );

        try {

            JsonNode node = objectMapper.readTree(json);

            Difficulty difficulty = Difficulty.valueOf(
                    node.get("difficulty")
                            .asText()
                            .toUpperCase()
            );

            QuestionType questionType = QuestionType.valueOf(
                    node.get("questionType")
                            .asText()
                            .toUpperCase()
            );

            return new NextQuestionResponse(
                    node.get("question").asText(),
                    node.get("topic").asText(),
                    difficulty,
                    questionType
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI next-question response",
                    e
            );
        }
    }

    // =========================================================
    // 4. GENERATE FINAL INTERVIEW REPORT
    // =========================================================

    public String generateFinalReport(
            String role,
            String interviewData) {

        String instructions = """
                You are an expert technical interviewer.

                Generate a final interview performance report.

                Role:
                %s

                Interview data:
                %s

                Include:

                - Overall performance
                - Technical performance
                - Communication
                - Strong topics
                - Weak topics
                - Strongest answer
                - Weakest answer
                - Specific improvement recommendations
                - Final assessment

                Make the report professional,
                specific and useful for the candidate.

                Do not return markdown if structured output is requested.
                """.formatted(role, interviewData);

        return callAI(
                instructions,
                "Generate the final interview report."
        );
    }

    // =========================================================
    // 5. COMMON OPENAI API CALL
    // =========================================================

    private String callAI(
            String instructions,
            String input) {

        Map<String, Object> request = new HashMap<>();

        request.put("model", model);
        request.put("instructions", instructions);
        request.put("input", input);

        JsonNode response = restClient
                .post()
                .uri("/responses")
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        return extractOutputText(response);
    }

    // =========================================================
    // 6. EXTRACT TEXT FROM OPENAI RESPONSE
    // =========================================================

    private String extractOutputText(JsonNode response) {

        JsonNode output = response.get("output");

        if (output == null || !output.isArray()) {

            throw new RuntimeException(
                    "Invalid response received from OpenAI"
            );
        }

        for (JsonNode outputItem : output) {

            JsonNode content = outputItem.get("content");

            if (content == null || !content.isArray()) {
                continue;
            }

            for (JsonNode contentItem : content) {

                if ("output_text".equals(
                        contentItem.path("type").asText())) {

                    return contentItem
                            .path("text")
                            .asText();
                }
            }
        }

        throw new RuntimeException(
                "No text output received from OpenAI"
        );
    }
}
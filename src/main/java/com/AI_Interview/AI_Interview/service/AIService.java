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

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AIService {

    private final RestClient restClient;
    private final JsonMapper objectMapper;
    private final OfflineFallbackEngine offlineFallbackEngine;

    @Value("${openai.model}")
    private String model;

    public AIService(
            @Value("${openai.api-key}") String apiKey,
            JsonMapper objectMapper,
            OfflineFallbackEngine offlineFallbackEngine) {

        this.objectMapper = objectMapper;
        this.offlineFallbackEngine = offlineFallbackEngine;

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
        return generateInitialQuestion(role, difficulty, com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE);
    }

    public NextQuestionResponse generateInitialQuestion(
            String role,
            Difficulty difficulty,
            com.AI_Interview.AI_Interview.enums.InterviewType type) {

        boolean isLive = type == com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE;

        String instructions;
        if (isLive) {
            instructions = """
                    You are conducting a realistic, conversational 1-on-1 LIVE technical interview as an expert hiring manager and senior engineer.
                    Target Role: %s
                    Difficulty Level: %s

                    Start the interview warmly, engagingly, and professionally, just like a real person-vs-person live interview.
                    Introduce yourself briefly (e.g. "Hello! Welcome to your interview for the %s role. I'm Alex from the engineering team, and I'll be conducting our session today.") and ask an opening warm-up question inviting the candidate to introduce themselves, outline their core background, and mention the primary technologies or projects they've worked on recently.

                    Return ONLY valid JSON:
                    {
                      "question": "greeting and introductory question text",
                      "topic": "Introduction & Background"
                    }
                    Do not return markdown. Do not return anything outside JSON.
                    """.formatted(role, difficulty, role);
        } else {
            instructions = """
                    You are an expert technical interviewer conducting a structured technical interview.
                    Target Role: %s
                    Difficulty Level: %s

                    Generate the first direct technical question for this role testing core fundamentals at the specified difficulty.
                    Do NOT add conversational greetings or small talk; state the question clearly and directly.

                    Return ONLY valid JSON:
                    {
                      "question": "question text",
                      "topic": "topic name"
                    }
                    Do not return markdown. Do not return anything outside JSON.
                    """.formatted(role, difficulty);
        }

        try {
            String json = callAI(
                    instructions,
                    isLive ? "Start live 1-on-1 interview with warm intro." : "Generate first interview question."
            );

            JsonNode node = objectMapper.readTree(json);

            return new NextQuestionResponse(
                    node.get("question").asText(),
                    node.get("topic").asText(),
                    difficulty,
                    QuestionType.INITIAL
            );

        } catch (Exception e) {
            log.warn(
                    "AI initial question failed, using offline fallback: {}",
                    e.getMessage()
            );

            return offlineFallbackEngine.initialQuestion(role, difficulty, type);
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

                Evaluate the candidate's answer against the question asked.

                Question:
                %s

                Candidate Answer:
                %s

                Evaluate the answer on:
                1. Overall score from 0 to 100
                2. Technical accuracy from 0 to 100 (percentage match representing how much percent the candidate's answer is correct compared to the ideal actual answer)
                3. Completeness from 0 to 100
                4. Clarity from 0 to 100
                5. Correct points (bullet points of what candidate got right)
                6. Missing points (bullet points of what key concepts were omitted)
                7. Constructive feedback
                8. Ideal / Correct Answer: Provide a comprehensive, clear, high-quality reference model answer to the question so the candidate can compare what they should have said.

                Return ONLY valid JSON:
                {
                  "score": 0.0,
                  "technicalAccuracy": 0.0,
                  "completeness": 0.0,
                  "clarity": 0.0,
                  "correctPoints": "string",
                  "missingPoints": "string",
                  "feedback": "string",
                  "idealAnswer": "string"
                }

                Scores must be numbers between 0 and 100.
                Do not return markdown.
                Do not return anything outside JSON.
                """.formatted(question, answer);

        try {
            String json = callAI(
                    instructions,
                    "Evaluate the candidate answer."
            );

            JsonNode node = objectMapper.readTree(json);

            String idealAnswer = node.has("idealAnswer") && !node.get("idealAnswer").isNull()
                    ? node.get("idealAnswer").asText()
                    : offlineFallbackEngine.generateModelAnswer(question);

            return new AnswerEvaluationResponse(
                    node.get("score").asDouble(),
                    node.get("technicalAccuracy").asDouble(),
                    node.get("completeness").asDouble(),
                    node.get("clarity").asDouble(),
                    node.get("correctPoints").asText(),
                    node.get("missingPoints").asText(),
                    node.get("feedback").asText(),
                    idealAnswer
            );

        } catch (Exception e) {
            log.warn(
                    "AI evaluation failed, using offline evaluation: {}",
                    e.getMessage()
            );

            return offlineFallbackEngine.evaluate(question, answer);
        }
    }

    // =========================================================
    // 3. GENERATE NEXT ADAPTIVE QUESTION
    // =========================================================

    public NextQuestionResponse generateNextQuestion(
            String role,
            String previousQuestion,
            String answer,
            AnswerEvaluationResponse evaluation,
            List<String> askedQuestions,
            Difficulty baseDifficulty) {
        return generateNextQuestion(
                role,
                previousQuestion,
                answer,
                evaluation,
                askedQuestions,
                baseDifficulty,
                com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE,
                1,
                5
        );
    }

    public NextQuestionResponse generateNextQuestion(
            String role,
            String previousQuestion,
            String answer,
            AnswerEvaluationResponse evaluation,
            List<String> askedQuestions,
            Difficulty baseDifficulty,
            com.AI_Interview.AI_Interview.enums.InterviewType type,
            int currentQuestionNumber,
            int totalQuestionLimit) {

        boolean isLive = type == com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE;
        boolean isFinalQuestion = currentQuestionNumber >= totalQuestionLimit - 1;

        String instructions;
        if (isLive) {
            String timeNotice = isFinalQuestion
                    ? "- TIME & WRAP-UP NOTICE: This is the FINAL question of the interview. Open with a natural, time-aware transition (e.g. 'We have time for one last question before wrapping up today...') and ask a comprehensive question."
                    : "- Pacing: We have plenty of time remaining. Maintain an engaging, steady conversational pace.";

            instructions = """
                    You are conducting a realistic 1-on-1 LIVE PERSON-VS-PERSON technical interview.
                    Target Role: %s

                    Previous Question:
                    %s

                    Candidate's Answer:
                    %s

                    Previous Answer Evaluation:
                    - Accuracy: %.1f%%
                    - Score: %.1f
                    - What candidate got right: %s
                    - What was missing: %s

                    INSTRUCTIONS FOR PERSON-VS-PERSON ADAPTIVE INTERACTION:
                    1. React directly and naturally to what the candidate actually said in their answer (e.g. "I see how you approached caching with Redis, but what if...", or "Thanks for walking through your background; let's drill into the architecture of that project...").
                    2. Dynamic follow-up according to their answer:
                       - If their answer was incomplete or vague: ask a targeted follow-up or clarification on the missing piece.
                       - If their answer was solid: test edge cases, scalability trade-offs, or increase difficulty.
                       - If they introduced themselves: pick 1 key technology they mentioned and ask a practical question about it.
                    3. Do not sound robotic. Sound like a friendly, sharp senior interviewer speaking directly with the candidate.
                    4. %s

                    Question types allowed: FOLLOW_UP, DEEP_DIVE, CLARIFICATION, SCENARIO, NEW_TOPIC
                    Difficulty allowed: EASY, MEDIUM, HARD

                    Return ONLY valid JSON:
                    {
                      "question": "next question text including conversational transition",
                      "topic": "topic",
                      "difficulty": "EASY|MEDIUM|HARD",
                      "questionType": "FOLLOW_UP"
                    }
                    Do not return markdown. Do not return anything outside JSON.
                    """.formatted(
                    role,
                    previousQuestion,
                    answer,
                    evaluation.getTechnicalAccuracy(),
                    evaluation.getScore(),
                    evaluation.getCorrectPoints(),
                    evaluation.getMissingPoints(),
                    timeNotice
            );
        } else {
            instructions = """
                    You are conducting a structured NORMAL technical interview.
                    Target Role: %s

                    Previous Question:
                    %s

                    Candidate's Answer:
                    %s

                    Evaluation Score: %.1f/100

                    Ask the NEXT direct technical question testing another core capability for this role at base difficulty %s.
                    Do not add conversational pleasantries; formulate a direct, objective technical question.

                    Question types allowed: FOLLOW_UP, DEEP_DIVE, SCENARIO, NEW_TOPIC
                    Difficulty allowed: EASY, MEDIUM, HARD

                    Return ONLY valid JSON:
                    {
                      "question": "next question text",
                      "topic": "topic",
                      "difficulty": "EASY|MEDIUM|HARD",
                      "questionType": "NEW_TOPIC"
                    }
                    Do not return markdown. Do not return anything outside JSON.
                    """.formatted(
                    role,
                    previousQuestion,
                    answer,
                    evaluation.getScore(),
                    baseDifficulty
            );
        }

        try {
            String json = callAI(
                    instructions,
                    isLive ? "Generate live conversational adaptive follow-up question." : "Generate next technical question."
            );

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
            log.warn(
                    "AI next-question failed, using offline fallback: {}",
                    e.getMessage()
            );

            return offlineFallbackEngine.nextQuestion(
                    role,
                    askedQuestions,
                    baseDifficulty,
                    evaluation.getScore()
            );
        }
    }

    // =========================================================
    // 4. GENERATE IDEAL / MODEL ANSWER (FOR REVIEW & COMPARISON)
    // =========================================================

    public String generateIdealAnswer(String question, String role) {
        if (question == null || question.isBlank()) {
            return offlineFallbackEngine.generateModelAnswer(question);
        }

        String prompt = """
                You are a senior tech lead. Provide the comprehensive, accurate, and ideal model answer for this interview question:
                Role: %s
                Question: %s

                Provide a 2 to 3 paragraph answer that:
                1. Clearly states the core technical definition and mechanism.
                2. Gives a practical real-world scenario or code approach.
                3. Highlights key architectural trade-offs or performance best practices.

                Return plain text only without markdown backticks or quotes.
                """.formatted(role != null ? role : "Software Engineer", question);

        try {
            return callAI(prompt, "Generate ideal answer for question.");
        } catch (Exception e) {
            log.warn("AI ideal answer generation failed: {}", e.getMessage());
            return offlineFallbackEngine.generateModelAnswer(question);
        }
    }

    // =========================================================
    // 4. GENERATE FINAL INTERVIEW REPORT
    // =========================================================

    public JsonNode generateFinalReport(
            String role,
            String interviewData) {

        String instructions = """
            You are an expert technical interviewer.

            Analyze the complete interview performance.

            Interview Role:
            %s

            Interview Data:
            %s

            Calculate:

            1. Overall score from 0 to 100
            2. Technical score from 0 to 100
            3. Communication score from 0 to 100
            4. Strengths
            5. Weaknesses
            6. Specific recommendations

            Also consider:
            - Technical correctness
            - Completeness
            - Clarity
            - Candidate's understanding
            - Consistency across answers
            - Ability to handle follow-up questions
            - Ability to solve scenario-based questions

            Return ONLY valid JSON:

            {
              "overallScore": 0.0,
              "technicalScore": 0.0,
              "communicationScore": 0.0,
              "strengths": "string",
              "weaknesses": "string",
              "recommendations": "string"
            }

            Scores must be between 0 and 100.

            Do not return markdown.
            Do not return anything outside JSON.
            """.formatted(role, interviewData);

        String json = callAI(
                instructions,
                "Generate the final interview performance report."
        );

        try {
            return objectMapper.readTree(json);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse AI final report",
                    e
            );
        }
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
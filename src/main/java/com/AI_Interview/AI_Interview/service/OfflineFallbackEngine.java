package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.AnswerEvaluationResponse;
import com.AI_Interview.AI_Interview.dto.NextQuestionResponse;
import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.QuestionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Offline fallback used whenever the OpenAI service is unavailable
 * (e.g. quota exhausted, network down, invalid key).
 *
 * Keeps the full interview flow working end to end:
 * questions come from a built-in bank (difficulty adapts to the
 * previous answer's score), and answers are scored by a simple
 * heuristic. Once the AI is reachable again, callers switch back
 * to it automatically — no configuration needed.
 */
@Slf4j
@Component
public class OfflineFallbackEngine {

    private record BankQuestion(
            String text,
            String topic,
            Difficulty difficulty,
            QuestionType type) {
    }

    private static final List<BankQuestion> BANK = List.of(

            // ── EASY ──
            new BankQuestion(
                    "Walk me through your background and the project you are most proud of.",
                    "Background & Experience",
                    Difficulty.EASY,
                    QuestionType.NEW_TOPIC),
            new BankQuestion(
                    "Explain the difference between a class and an object.",
                    "OOP Fundamentals",
                    Difficulty.EASY,
                    QuestionType.CLARIFICATION),
            new BankQuestion(
                    "What is the difference between an array and a linked list?",
                    "Data Structures",
                    Difficulty.EASY,
                    QuestionType.FOLLOW_UP),
            new BankQuestion(
                    "What do the HTTP status codes 200, 404 and 500 mean?",
                    "Web Basics",
                    Difficulty.EASY,
                    QuestionType.FOLLOW_UP),
            new BankQuestion(
                    "Describe how you would debug a program that fails only sometimes.",
                    "Debugging",
                    Difficulty.EASY,
                    QuestionType.SCENARIO),

            // ── MEDIUM ──
            new BankQuestion(
                    "Explain inheritance vs composition. When would you prefer each?",
                    "OOP Design",
                    Difficulty.MEDIUM,
                    QuestionType.DEEP_DIVE),
            new BankQuestion(
                    "How would you design a simple REST API for a book library?",
                    "API Design",
                    Difficulty.MEDIUM,
                    QuestionType.SCENARIO),
            new BankQuestion(
                    "What is the difference between a process and a thread?",
                    "Concurrency",
                    Difficulty.MEDIUM,
                    QuestionType.FOLLOW_UP),
            new BankQuestion(
                    "What are database indexes, and what trade-offs do they introduce?",
                    "Databases",
                    Difficulty.MEDIUM,
                    QuestionType.DEEP_DIVE),
            new BankQuestion(
                    "How do you test a feature you have just finished building?",
                    "Testing",
                    Difficulty.MEDIUM,
                    QuestionType.FOLLOW_UP),
            new BankQuestion(
                    "Tell me about a time you had to optimize slow code. What did you do?",
                    "Performance",
                    Difficulty.MEDIUM,
                    QuestionType.SCENARIO),

            // ── HARD ──
            new BankQuestion(
                    "Design a URL shortener service end to end: components, storage, and scaling.",
                    "System Design",
                    Difficulty.HARD,
                    QuestionType.SCENARIO),
            new BankQuestion(
                    "Explain the CAP theorem and how it affects distributed systems.",
                    "Distributed Systems",
                    Difficulty.HARD,
                    QuestionType.DEEP_DIVE),
            new BankQuestion(
                    "How would you protect an application from SQL injection and XSS?",
                    "Security",
                    Difficulty.HARD,
                    QuestionType.SCENARIO),
            new BankQuestion(
                    "Discuss the trade-offs between SQL and NoSQL for a high-traffic application.",
                    "Data Modeling",
                    Difficulty.HARD,
                    QuestionType.DEEP_DIVE),
            new BankQuestion(
                    "Design a caching strategy for a read-heavy service. Which invalidation approach and why?",
                    "Caching",
                    Difficulty.HARD,
                    QuestionType.SCENARIO));

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "you", "your", "this", "that",
            "with", "from", "have", "has", "was", "were", "what", "when",
            "where", "which", "would", "could", "should", "does", "into",
            "than", "then", "them", "they", "been", "being", "each",
            "more", "most", "some", "such", "only", "also", "very",
            "just", "like", "over", "upon", "about", "after", "before",
            "between", "through", "during", "above", "below", "other",
            "these", "those", "there", "their", "how", "why", "who",
            "whom", "will", "shall", "may", "might", "must", "can",
            "did", "done", "doing", "having", "explain", "describe",
            "difference", "tell", "walk", "give", "name", "list",
            "define", "compare", "discuss");

    // =========================================================
    // 1. INITIAL QUESTION FALLBACK
    // =========================================================

    public NextQuestionResponse initialQuestion(
            String role,
            Difficulty difficulty) {

        BankQuestion question = pick(List.of(), difficulty);

        log.info("Offline fallback: initial question [{}] for role [{}]",
                question.topic(), role);

        return new NextQuestionResponse(
                question.text(),
                question.topic(),
                question.difficulty(),
                QuestionType.INITIAL);
    }

    // =========================================================
    // 2. NEXT ADAPTIVE QUESTION FALLBACK
    // =========================================================

    public NextQuestionResponse nextQuestion(
            String role,
            List<String> askedQuestions,
            Difficulty baseDifficulty,
            double lastScore) {

        // Adapt difficulty from the previous answer's score
        Difficulty target;
        if (lastScore >= 75) {
            target = up(baseDifficulty);
        } else if (lastScore <= 40) {
            target = down(baseDifficulty);
        } else {
            target = baseDifficulty;
        }

        BankQuestion question = pick(askedQuestions, target);

        log.info("Offline fallback: next question [{}] (score {} -> difficulty {})",
                question.topic(), lastScore, question.difficulty());

        return new NextQuestionResponse(
                question.text(),
                question.topic(),
                question.difficulty(),
                question.type());
    }

    // =========================================================
    // 3. ANSWER EVALUATION FALLBACK (heuristic, 0-100 scores)
    // =========================================================

    public AnswerEvaluationResponse evaluate(
            String question,
            String answer) {

        String answerText = answer == null ? "" : answer.trim();

        int words = answerText.isEmpty()
                ? 0
                : answerText.split("\\s+").length;

        Set<String> questionTokens = tokens(question);
        Set<String> answerTokens = tokens(answerText);

        Set<String> matched = new TreeSet<>();
        Set<String> missing = new TreeSet<>();

        for (String token : questionTokens) {
            if (answerTokens.contains(token)) {
                matched.add(token);
            } else {
                missing.add(token);
            }
        }

        double keywordRatio = questionTokens.isEmpty()
                ? 0.5
                : (double) matched.size() / questionTokens.size();

        String lower = answerText.toLowerCase();
        boolean hasExamples = lower.contains("for example")
                || lower.contains("e.g.")
                || lower.contains("such as")
                || lower.contains("in my project")
                || lower.contains("in my experience");

        boolean structured = answerText.contains(". ")
                || answerText.contains("\n")
                || answerText.split("[.!?]").length >= 2;

        double lengthFactor = Math.min(1.0, words / 60.0);

        double technical =
                25 + 50 * keywordRatio + (hasExamples ? 10 : 0);

        double completeness =
                15 + 65 * lengthFactor + (structured ? 10 : 0);

        double clarity =
                25 + 30 * lengthFactor
                        + (structured ? 20 : 0)
                        + (hasExamples ? 10 : 0);

        double overall =
                0.4 * technical + 0.35 * completeness + 0.25 * clarity;

        String correctPoints = matched.isEmpty()
                ? "No direct topic keywords were matched."
                : "Touched on: " + String.join(", ", cap(matched, 6));

        String missingPoints = missing.isEmpty()
                ? "None - the answer covered the main topics."
                : "Not mentioned: " + String.join(", ", cap(missing, 6));

        String detailNote = words < 30
                ? "Try to elaborate more - aim for a few sentences covering the key concepts."
                : "Good level of detail.";

        String feedback = String.format(
                "Offline evaluation (AI service unavailable). "
                        + "Your answer was %d words%s%s. %s",
                words,
                hasExamples ? ", included concrete examples" : "",
                structured ? ", and was structured in complete sentences" : "",
                detailNote);

        return new AnswerEvaluationResponse(
                clamp(overall),
                clamp(technical),
                clamp(completeness),
                clamp(clarity),
                correctPoints,
                missingPoints,
                feedback);
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private BankQuestion pick(
            List<String> askedQuestions,
            Difficulty targetDifficulty) {

        List<BankQuestion> candidates = BANK.stream()
                .filter(b -> b.difficulty() == targetDifficulty)
                .filter(b -> !askedQuestions.contains(b.text()))
                .toList();

        if (candidates.isEmpty()) {
            // No unasked question at this difficulty — take any unasked one
            candidates = BANK.stream()
                    .filter(b -> !askedQuestions.contains(b.text()))
                    .toList();
        }

        if (candidates.isEmpty()) {
            // Everything was asked already — reuse the bank
            candidates = BANK;
        }

        int index = askedQuestions.size() % candidates.size();

        return candidates.get(index);
    }

    private Difficulty up(Difficulty difficulty) {
        return difficulty == Difficulty.EASY
                ? Difficulty.MEDIUM
                : Difficulty.HARD;
    }

    private Difficulty down(Difficulty difficulty) {
        return difficulty == Difficulty.HARD
                ? Difficulty.MEDIUM
                : Difficulty.EASY;
    }

    private Set<String> tokens(String text) {

        if (text == null || text.isBlank()) {
            return Set.of();
        }

        Set<String> tokens = new TreeSet<>();

        for (String raw : text.toLowerCase().split("[^a-z0-9]+")) {

            if (raw.length() >= 3 && !STOPWORDS.contains(raw)) {
                tokens.add(raw);
            }

            if (tokens.size() >= 12) {
                break;
            }
        }

        return tokens;
    }

    private List<String> cap(Collection<String> items, int limit) {
        return new ArrayList<>(items.stream().limit(limit).toList());
    }

    private double clamp(double value) {
        return Math.round(
                Math.max(0, Math.min(100, value)) * 10.0
        ) / 10.0;
    }
}

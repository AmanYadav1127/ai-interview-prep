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
        return initialQuestion(role, difficulty, com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE);
    }

    public NextQuestionResponse initialQuestion(
            String role,
            Difficulty difficulty,
            com.AI_Interview.AI_Interview.enums.InterviewType type) {

        if (type == com.AI_Interview.AI_Interview.enums.InterviewType.LIVE_ADAPTIVE) {
            return new NextQuestionResponse(
                    "Hello and welcome! I'm Alex, your AI interviewer today for the " + (role != null ? role : "Engineering") + " role. To get started, please tell me a bit about yourself, your background, and the key projects and technologies you've been working with recently.",
                    "Introduction & Background",
                    difficulty != null ? difficulty : Difficulty.EASY,
                    QuestionType.INITIAL
            );
        }

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

        String idealAnswer = generateModelAnswer(question);

        return new AnswerEvaluationResponse(
                clamp(overall),
                clamp(technical),
                clamp(completeness),
                clamp(clarity),
                correctPoints,
                missingPoints,
                feedback,
                idealAnswer);
    }

    public String generateModelAnswer(String question) {
        if (question == null || question.isBlank()) {
            return "A strong answer clearly defines the core concept, provides practical implementation examples, and outlines operational trade-offs and performance characteristics.";
        }
        String q = question.toLowerCase();
        if (q.contains("array") && q.contains("linked list")) {
            return "An array is a contiguous block of memory with O(1) random access by index, but fixed size and O(n) insertions/deletions. A linked list consists of nodes with data and pointers to the next node, offering dynamic sizing and O(1) insertion/deletion at known positions, but O(n) sequential search and extra memory overhead for pointers.";
        } else if (q.contains("class") && q.contains("object")) {
            return "A class is a blueprint or template that defines properties and behaviors (methods), while an object is an instantiated runtime instance of that class occupying memory.";
        } else if (q.contains("http") || q.contains("status code")) {
            return "HTTP 200 OK means the request succeeded. HTTP 404 Not Found indicates the requested resource could not be found on the server. HTTP 500 Internal Server Error means an unexpected server-side exception or error prevented fulfilling the request.";
        } else if (q.contains("process") && q.contains("thread")) {
            return "A process is an independent execution environment with its own dedicated memory address space allocated by the OS. A thread is the smallest unit of execution within a process; threads in the same process share code, data, and resources (heap), making thread context switching faster but requiring careful concurrency synchronization.";
        } else if (q.contains("index")) {
            return "Database indexes (commonly B-Trees or Hash tables) are auxiliary data structures that dramatically accelerate query retrieval (SELECT) from O(n) table scans to O(log n). The trade-offs include additional disk storage consumption and write amplification, as INSERT, UPDATE, and DELETE operations must update both the table and its associated indexes.";
        } else if (q.contains("inheritance") && q.contains("composition")) {
            return "Inheritance is an 'is-a' relationship allowing subclasses to inherit fields and methods from a parent class, which creates tight coupling. Composition is a 'has-a' relationship where a class contains instances of other classes to achieve functionality, which promotes loose coupling, easier testing, and dynamic runtime flexibility.";
        } else if (q.contains("introduce") || q.contains("background") || q.contains("tell me about yourself")) {
            return "A strong self-introduction follows the Present-Past-Future structure: briefly summarize your current role and specialization, highlight 1-2 major impactful projects and core technologies you excel in, and express your passion and readiness for the target position.";
        }
        return "An ideal answer should: 1) Clearly define the core technical concepts and architecture. 2) Provide concrete production examples with code or system patterns. 3) Discuss trade-offs, edge cases, and performance considerations (e.g. latency, memory, scalability).";
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

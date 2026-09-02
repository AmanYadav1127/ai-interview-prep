package com.AI_Interview.AI_Interview.service;

import com.AI_Interview.AI_Interview.dto.DashboardResponse;
import com.AI_Interview.AI_Interview.entity.Interview;
import com.AI_Interview.AI_Interview.entity.User;
import com.AI_Interview.AI_Interview.repository.InterviewRepository;
import com.AI_Interview.AI_Interview.repository.InterviewResultRepository;
import com.AI_Interview.AI_Interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewResultRepository interviewResultRepository;

    public DashboardResponse getDashboard(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        List<Interview> interviews =
                interviewRepository.findByUser(user);

        int totalInterviews = interviews.size();
        int completedInterviews = 0;
        double totalScore = 0;

        for (Interview interview : interviews) {

            if ("COMPLETED".equals(interview.getStatus())) {

                completedInterviews++;

                var result = interviewResultRepository
                        .findByInterview(interview);

                if (result.isPresent()) {
                    totalScore += result.get().getOverallScore();
                }
            }
        }

        double averageScore = completedInterviews == 0
                ? 0
                : totalScore / completedInterviews;

        return new DashboardResponse(
                totalInterviews,
                completedInterviews,
                averageScore
        );
    }
}
package com.AI_Interview.AI_Interview.repository;

import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.entity.Evaluation;
import com.AI_Interview.AI_Interview.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    Optional<Evaluation> findByAnswer(Answer answer);

    List<Evaluation> findByAnswer_Question_Interview(Interview interview);
}
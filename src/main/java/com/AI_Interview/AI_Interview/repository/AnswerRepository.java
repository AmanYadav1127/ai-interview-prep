package com.AI_Interview.AI_Interview.repository;

import com.AI_Interview.AI_Interview.entity.Answer;
import com.AI_Interview.AI_Interview.entity.Interview;
import com.AI_Interview.AI_Interview.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {

    Optional<Answer> findByQuestion(Question question);

    List<Answer> findByQuestion_Interview(Interview interview);
}
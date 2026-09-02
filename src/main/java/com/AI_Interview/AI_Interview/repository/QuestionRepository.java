package com.AI_Interview.AI_Interview.repository;

import com.AI_Interview.AI_Interview.entity.Question;
import com.AI_Interview.AI_Interview.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByInterviewOrderByQuestionOrderAsc(Interview interview);
}
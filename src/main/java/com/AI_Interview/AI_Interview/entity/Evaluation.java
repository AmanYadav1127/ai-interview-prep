package com.AI_Interview.AI_Interview.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "evaluations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Evaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "answer_id", nullable = false)
    private Answer answer;

    private double score;

    private double technicalAccuracy;

    private double completeness;

    private double clarity;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(columnDefinition = "TEXT")
    private String correctPoints;

    @Column(columnDefinition = "TEXT")
    private String missingPoints;

    @Column(columnDefinition = "TEXT")
    private String idealAnswer;
}
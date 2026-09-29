package com.AI_Interview.AI_Interview.entity;

import com.AI_Interview.AI_Interview.enums.Difficulty;
import com.AI_Interview.AI_Interview.enums.InterviewMode;
import com.AI_Interview.AI_Interview.enums.InterviewType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "interviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;

    private String role;

    @Enumerated(EnumType.STRING)
    private InterviewMode mode;

    @Enumerated(EnumType.STRING)
    private InterviewType type;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    private int questionLimit;
 
    @Column(name = "duration_minutes")
    private Integer durationMinutes = 10;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;
}
package com.pgoptima.analyticsservice.entity;

import com.pgoptima.shareddto.enums.RecommendationType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "recommendation_results")
@Data
public class RecommendationResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long historyId;
    private RecommendationType type;
    private String description;
    private String sqlCommand;
    private Boolean applied;
    private Double estimatedImprovement;
    private Double actualImprovement;

    @CreationTimestamp
    private Instant createdAt;
}
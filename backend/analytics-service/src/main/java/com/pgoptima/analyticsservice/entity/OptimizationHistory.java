package com.pgoptima.analyticsservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.Duration;

@Entity
@Table(name = "optimization_history")
@Data
public class OptimizationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long connectionId;

    @Column(columnDefinition = "TEXT")
    private String originalQuery;

    private Duration originalExecutionTime;
    private Duration optimizedExecutionTime;
    private Double improvementPercent;
    private Boolean success;

    @CreationTimestamp
    private Instant createdAt;
}
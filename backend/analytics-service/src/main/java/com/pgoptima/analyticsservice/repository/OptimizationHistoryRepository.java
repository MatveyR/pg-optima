package com.pgoptima.analyticsservice.repository;

import com.pgoptima.analyticsservice.entity.OptimizationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptimizationHistoryRepository extends JpaRepository<OptimizationHistory, Long> {
    List<OptimizationHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
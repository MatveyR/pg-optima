package com.pgoptima.analyticsservice.repository;

import com.pgoptima.analyticsservice.entity.RecommendationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecommendationResultRepository extends JpaRepository<RecommendationResult, Long> {
    List<RecommendationResult> findByHistoryId(Long historyId);
}
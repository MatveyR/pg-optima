package com.pgoptima.userservice.repository;

import com.pgoptima.userservice.entity.SavedQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQueryEntity, Long> {
    List<SavedQueryEntity> findByProjectId(Long projectId);
    List<SavedQueryEntity> findByProjectOwnerId(Long ownerId);
}
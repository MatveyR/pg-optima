package com.pgoptima.userservice.repository;

import com.pgoptima.userservice.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {
    List<ProjectEntity> findByOwnerId(Long ownerId);
    Optional<ProjectEntity> findByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}
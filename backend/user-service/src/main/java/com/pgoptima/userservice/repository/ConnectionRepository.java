package com.pgoptima.userservice.repository;

import com.pgoptima.userservice.entity.ConnectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConnectionRepository extends JpaRepository<ConnectionEntity, Long> {
    List<ConnectionEntity> findByOwnerId(Long ownerId);
    Optional<ConnectionEntity> findByIdAndOwnerId(Long id, Long ownerId);
    void deleteByIdAndOwnerId(Long id, Long ownerId);
}
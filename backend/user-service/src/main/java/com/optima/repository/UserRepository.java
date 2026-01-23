package com.optima.repository;

import com.optima.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * User jpa repository.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
}

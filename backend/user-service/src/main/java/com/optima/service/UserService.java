package com.optima.service;

import com.optima.mapper.UserMapper;
import com.optima.model.User;
import com.optima.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service providing business logic for user operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    private final UserMapper mapper;

    /**
     * Fetches all users from the database.
     *
     * @return list of users
     */
    public List<User> getAll() {
        log.info("Getting all users");
        return repository.findAll().stream()
                .map(mapper::convertToUser)
                .toList();
    }

    /**
     * Creates a new user record in the database.
     *
     * @param user user data received from API
     * @return saved user with generated ID
     */
    public User create(User user) {
        log.info("Creating user with username={}", user.getUsername());
        return mapper.convertToUser(repository.save(mapper.convertToUserEntity(user)));
    }

    /**
     * Deletes all users from repository.
     */
    public void deleteAll() {
        log.info("Deleting all users");
        repository.deleteAll();
    }
}

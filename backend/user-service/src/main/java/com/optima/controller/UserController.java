package com.optima.controller;

import com.optima.model.User;
import com.optima.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that handles user-related API requests.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get all users.
     *
     * @return all users
     */
    @GetMapping
    public List<User> getAll() {
        return userService.getAll();
    }

    /**
     * Creates a new user.
     *
     * @param user user from request
     * @return new user
     */
    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    /**
     * Deletes all users.
     */
    @DeleteMapping
    public void clearUsers() {
        userService.deleteAll();
    }
}

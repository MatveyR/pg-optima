package com.optima.model;

import lombok.Data;

/**
 * User Pojo.
 */
@Data
public class User {

    /**
     * Display name of the user.
     */
    private String username;

    /**
     * User's password.
     */
    private String password;
}


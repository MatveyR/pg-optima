package com.pgoptima.shareddto.response;

import com.pgoptima.shareddto.enums.Role;
import lombok.Data;

import java.time.Instant;

@Data
public class UserDTO {
    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private Instant createdAt;
}
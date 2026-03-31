package com.pgoptima.userservice.controller;

import com.pgoptima.userservice.service.AuthService;
import com.pgoptima.shareddto.request.LoginRequest;
import com.pgoptima.shareddto.request.RegisterRequest;
import com.pgoptima.shareddto.request.ValidateTokenRequest;
import com.pgoptima.shareddto.response.LoginResponse;
import com.pgoptima.shareddto.response.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, login, token validation")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new user")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and get JWT tokens")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) {
            throw new IllegalArgumentException("refreshToken is required");
        }
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate JWT token")
    public ResponseEntity<Map<String, Boolean>> validate(@Valid @RequestBody ValidateTokenRequest request) {
        boolean isValid = authService.validateToken(request);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}
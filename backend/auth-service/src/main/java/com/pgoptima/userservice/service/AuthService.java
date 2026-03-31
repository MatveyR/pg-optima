package com.pgoptima.userservice.service;

import com.pgoptima.shareddto.request.LoginRequest;
import com.pgoptima.shareddto.request.RegisterRequest;
import com.pgoptima.shareddto.request.ValidateTokenRequest;
import com.pgoptima.shareddto.response.LoginResponse;
import com.pgoptima.shareddto.response.UserDTO;

public interface AuthService {
    UserDTO register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refreshToken(String refreshToken);
    boolean validateToken(ValidateTokenRequest request);
}
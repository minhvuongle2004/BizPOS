package com.bizpos.service;

import com.bizpos.dto.AuthResponse;
import com.bizpos.dto.LoginRequest;
import com.bizpos.dto.RegisterRequest;

public interface AuthService {

    /**
     * Đăng ký tài khoản mới và trả về JWT token
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Đăng nhập hệ thống và trả về JWT token
     */
    AuthResponse login(LoginRequest request);
}

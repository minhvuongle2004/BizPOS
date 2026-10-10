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

    /**
     * Cập nhật vai trò / phân quyền người dùng (Dành cho Admin)
     */
    com.bizpos.entity.User updateUserRole(Long userId, com.bizpos.entity.Role newRole);
}

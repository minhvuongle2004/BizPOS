package com.bizpos.service.impl;

import com.bizpos.dto.AuthResponse;
import com.bizpos.dto.LoginRequest;
import com.bizpos.dto.RegisterRequest;
import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.repository.UserRepository;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.getUsername().trim();

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new DuplicateResourceException("Username '" + trimmedUsername + "' đã tồn tại!");
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.STAFF;

        User user = User.builder()
                .username(trimmedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .build();

        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String trimmedUsername = request.getUsername().trim();

        // Xác thực username và password thông qua Spring Security AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(trimmedUsername, request.getPassword())
        );

        User user = userRepository.findByUsername(trimmedUsername)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại!"));

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }
}

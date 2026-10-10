package com.bizpos.service.impl;

import com.bizpos.dto.AuthResponse;
import com.bizpos.dto.LoginRequest;
import com.bizpos.dto.RegisterRequest;
import com.bizpos.entity.Role;
import com.bizpos.entity.User;
import com.bizpos.exception.DuplicateResourceException;
import com.bizpos.exception.ResourceNotFoundException;
import com.bizpos.repository.UserRepository;
import com.bizpos.security.JwtTokenProvider;
import com.bizpos.service.AuditLogService;
import com.bizpos.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final com.bizpos.security.LoginRateLimiter loginRateLimiter;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.getUsername().trim();
        String ip = getClientIp();

        if (userRepository.existsByUsername(trimmedUsername)) {
            auditLogService.recordFailureLog(
                    "User",
                    trimmedUsername,
                    "REGISTER_USER_FAILED",
                    "Đăng ký người dùng thất bại",
                    null,
                    null,
                    "Tài khoản '" + trimmedUsername + "' đã tồn tại trong hệ thống",
                    getCurrentUsername(),
                    ip
            );
            throw new DuplicateResourceException("Username '" + trimmedUsername + "' đã tồn tại!");
        }

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.STAFF;

        User user = User.builder()
                .username(trimmedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(assignedRole)
                .build();

        User savedUser = userRepository.save(user);

        auditLogService.recordSuccessLog(
                "User",
                String.valueOf(savedUser.getId()),
                "REGISTER_USER",
                "Đăng ký người dùng mới",
                null,
                "Quyền: " + assignedRole.name(),
                String.format("Tạo mới tài khoản '%s' (ID: %d) với quyền %s",
                        savedUser.getUsername(), savedUser.getId(), assignedRole.name()),
                getCurrentUsername(),
                ip
        );

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
        String ip = getClientIp();

        // 1. Kiểm tra Rate Limiting chống Brute-Force & chống phình bảng Audit Log
        if (loginRateLimiter.isBlocked(trimmedUsername, ip)) {
            long remaining = loginRateLimiter.getRemainingLockSeconds(trimmedUsername, ip);
            log.warn(">> [RATE LIMIT] Chặn đăng nhập cho user '{}' từ IP: {} (còn {}s)", trimmedUsername, ip, remaining);
            throw new com.bizpos.exception.LoginRateLimitExceededException(
                    "Tài khoản hoặc IP tạm thời bị khóa do thử đăng nhập sai quá nhiều lần. Vui lòng thử lại sau " + remaining + " giây.");
        }

        try {
            // Xác thực username và password thông qua Spring Security AuthenticationManager
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(trimmedUsername, request.getPassword())
            );
        } catch (AuthenticationException ex) {
            log.warn("Đăng nhập thất bại cho user '{}' từ IP: {}. Lỗi: {}", trimmedUsername, ip, ex.getMessage());
            boolean justLocked = loginRateLimiter.recordFailure(trimmedUsername, ip);

            // Ghi nhận log thất bại với REQUIRES_NEW (chỉ ghi log cảnh báo khi mới bị khóa)
            if (justLocked) {
                auditLogService.recordFailureLog(
                        "User",
                        trimmedUsername,
                        "LOGIN_LOCKED",
                        "Khóa tạm thời do đăng nhập sai nhiều lần",
                        null,
                        null,
                        "Tài khoản hoặc IP bị tạm khóa 5 phút do vượt quá giới hạn đăng nhập thất bại",
                        trimmedUsername,
                        ip
                );
            } else {
                auditLogService.recordFailureLog(
                        "User",
                        trimmedUsername,
                        "LOGIN_FAILED",
                        "Đăng nhập thất bại",
                        null,
                        null,
                        "Đăng nhập thất bại cho tài khoản '" + trimmedUsername + "': " + ex.getMessage(),
                        trimmedUsername,
                        ip
                );
            }
            throw ex;
        }

        // Đăng nhập thành công -> Reset bộ đếm thử sai
        loginRateLimiter.reset(trimmedUsername, ip);

        User user = userRepository.findByUsername(trimmedUsername)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại!"));

        // Ghi nhận log thành công (Propagation.REQUIRED)
        auditLogService.recordSuccessLog(
                "User",
                String.valueOf(user.getId()),
                "LOGIN_SUCCESS",
                "Đăng nhập thành công",
                null,
                null,
                String.format("Tài khoản '%s' (Quyền: %s) đăng nhập thành công vào hệ thống",
                        user.getUsername(), user.getRole().name()),
                user.getUsername(),
                ip
        );

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .username(user.getUsername())
                .role(user.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public User updateUserRole(Long userId, Role newRole) {
        if (newRole == null) {
            throw new IllegalArgumentException("Quyền (Role) không được để trống!");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));

        Role oldRole = user.getRole();
        if (oldRole == newRole) {
            return user;
        }

        user.setRole(newRole);
        User saved = userRepository.save(user);

        String ip = getClientIp();
        String currentActor = getCurrentUsername();
        String details = String.format("Thay đổi quyền tài khoản '%s' (ID: %d) từ %s sang %s",
                user.getUsername(), user.getId(), oldRole != null ? oldRole.name() : "NONE", newRole.name());

        auditLogService.recordLog(
                "User",
                String.valueOf(user.getId()),
                "CHANGE_ROLE",
                "Thay đổi quyền người dùng",
                oldRole != null ? oldRole.name() : "NONE",
                newRole.name(),
                details,
                currentActor,
                ip
        );

        return saved;
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest req = attributes.getRequest();
                String xf = req.getHeader("X-Forwarded-For");
                if (xf != null && !xf.isBlank()) {
                    return xf.split(",")[0].trim();
                }
                return req.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}

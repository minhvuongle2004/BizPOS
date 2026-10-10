package com.bizpos.controller;

import com.bizpos.dto.UpdateUserRoleRequest;
import com.bizpos.entity.User;
import com.bizpos.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /**
     * Cập nhật vai trò / phân quyền người dùng (Chỉ ADMIN)
     * PUT /api/users/{userId}/role
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        User updated = authService.updateUserRole(userId, request.getRole());
        return ResponseEntity.ok(updated);
    }
}

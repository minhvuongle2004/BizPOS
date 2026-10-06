package com.bizpos.dto;

import com.bizpos.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Username không được để trống!")
    @Size(min = 3, max = 50, message = "Username phải từ 3 đến 50 ký tự!")
    private String username;

    @NotBlank(message = "Password không được để trống!")
    @Size(min = 6, message = "Password phải có ít nhất 6 ký tự!")
    private String password;

    @Builder.Default
    private Role role = Role.STAFF;
}

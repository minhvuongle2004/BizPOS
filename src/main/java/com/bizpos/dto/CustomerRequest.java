package com.bizpos.dto;

import jakarta.validation.constraints.Email;
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
public class CustomerRequest {

    @NotBlank(message = "Họ và tên khách hàng không được để trống!")
    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự!")
    private String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự!")
    private String phone;

    @Email(message = "Email không đúng định dạng!")
    @Size(max = 150, message = "Email không được vượt quá 150 ký tự!")
    private String email;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự!")
    private String address;
}

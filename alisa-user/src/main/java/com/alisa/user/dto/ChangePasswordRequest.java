package com.alisa.user.dto;

import lombok.Data;
import lombok.ToString;

@Data
public class ChangePasswordRequest {
    private String username;
    private String email;

    @ToString.Exclude
    private String currentPassword;

    @ToString.Exclude
    private String newPassword;
    @ToString.Exclude
    private String code;
}

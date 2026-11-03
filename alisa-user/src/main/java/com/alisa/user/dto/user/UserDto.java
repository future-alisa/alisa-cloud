package com.alisa.user.dto.user;

import lombok.Data;
import lombok.ToString;

@Data
public class UserDto {
    private String username;
    private String email;
    @ToString.Exclude
    private String password;
    @ToString.Exclude
    private String newPassword;
}


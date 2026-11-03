package com.alisa.user.entity;

import lombok.Data;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class User {
    private String id;
    private String username;
    @JsonIgnore
    @ToString.Exclude
    private String password;
    private String email;
}


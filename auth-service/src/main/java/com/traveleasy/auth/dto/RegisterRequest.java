package com.traveleasy.auth.dto;

import com.traveleasy.auth.model.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
    private String fullName;
    private String phone;
    private Role role;
}

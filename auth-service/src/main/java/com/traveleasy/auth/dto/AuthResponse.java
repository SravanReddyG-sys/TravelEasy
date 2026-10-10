package com.traveleasy.auth.dto;

import com.traveleasy.auth.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private Long id;
    private String email;
    private String fullName;
    private Role role;
    private String status;
    private String verificationStatus;
    private boolean verified;
}

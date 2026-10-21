package com.traveleasy.traveller.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.Principal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TravellerPrincipal implements Principal {
    private Long userId;
    private String email;
    private String fullName;
    private String role;
    private String token;

    @Override
    public String getName() {
        return email;
    }
}

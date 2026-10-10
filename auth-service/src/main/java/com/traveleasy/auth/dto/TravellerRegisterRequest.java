package com.traveleasy.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TravellerRegisterRequest {
    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 30, message = "First name must be between 1 and 30 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "First name can only contain letters, spaces, apostrophes and hyphens")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 30, message = "Last name must be between 1 and 30 characters")
    @Pattern(regexp = "^[\\p{L} '\\-]+$", message = "Last name can only contain letters, spaces, apostrophes and hyphens")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email cannot exceed 254 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters long")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$",
        message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
    )
    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

    @AssertTrue(message = "Terms and Conditions must be accepted")
    private boolean termsAccepted;
}

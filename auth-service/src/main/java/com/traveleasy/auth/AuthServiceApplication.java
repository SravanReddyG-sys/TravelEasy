package com.traveleasy.auth;

import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDefaultUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Seed Admin User
            if (!userRepository.existsByEmail("admin@traveleasy.com")) {
                userRepository.save(User.builder()
                        .email("admin@traveleasy.com")
                        .password(passwordEncoder.encode("admin123"))
                        .fullName("Platform Administrator")
                        .phone("+1999888777")
                        .role(Role.ROLE_ADMIN)
                        .verified(true)
                        .status("ACTIVE")
                        .build());
            }

            // Seed Sample Bus Operator
            if (!userRepository.existsByEmail("busop@traveleasy.com")) {
                userRepository.save(User.builder()
                        .email("busop@traveleasy.com")
                        .password(passwordEncoder.encode("operator123"))
                        .fullName("Express Bus Travels")
                        .phone("+1987654321")
                        .role(Role.ROLE_BUS_OPERATOR)
                        .verified(true)
                        .status("ACTIVE")
                        .build());
            }

            // Seed Sample Hotel Manager
            if (!userRepository.existsByEmail("hotelmgr@traveleasy.com")) {
                userRepository.save(User.builder()
                        .email("hotelmgr@traveleasy.com")
                        .password(passwordEncoder.encode("manager123"))
                        .fullName("Grand Residency Hotel")
                        .phone("+1987654322")
                        .role(Role.ROLE_HOTEL_MANAGER)
                        .verified(true)
                        .status("ACTIVE")
                        .build());
            }

            // Seed Sample Customer
            if (!userRepository.existsByEmail("customer@traveleasy.com")) {
                userRepository.save(User.builder()
                        .email("customer@traveleasy.com")
                        .password(passwordEncoder.encode("customer123"))
                        .fullName("John Doe")
                        .phone("+1234567890")
                        .role(Role.ROLE_CUSTOMER)
                        .verified(true)
                        .status("ACTIVE")
                        .build());
            }
        };
    }
}

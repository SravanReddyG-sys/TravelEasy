package com.traveleasy.auth.controller;

import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import com.traveleasy.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/role/{role}")
    public ResponseEntity<List<User>> getUsersByRole(@PathVariable Role role) {
        return ResponseEntity.ok(userRepository.findByRole(role));
    }

    @PutMapping("/{id}/verify")
    public ResponseEntity<?> verifyProvider(@PathVariable Long id, @RequestParam boolean verified) {
        return userRepository.findById(id).map(user -> {
            user.setVerified(verified);
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("message", "User verification status updated to " + verified, "user", user));
        }).orElse(ResponseEntity.notFound().build());
    }
}

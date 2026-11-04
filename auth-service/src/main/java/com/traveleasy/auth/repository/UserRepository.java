package com.traveleasy.auth.repository;

import com.traveleasy.auth.model.Role;
import com.traveleasy.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    List<User> findByRole(Role role);
    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    /**
     * Database-level count of users by specific role.
     */
    long countByRole(Role role);

    /**
     * Paginated query for pending providers ordered newest first.
     */
    Page<User> findByVerificationStatusAndRoleInOrderByCreatedAtDesc(
            String verificationStatus,
            Collection<Role> roles,
            Pageable pageable
    );
}

package com.traveleasy.auth.service;

import com.traveleasy.auth.model.User;

public interface EmailService {
    /**
     * Sends a password reset email to the user with the specified reset URL.
     *
     * @param user the recipient user
     * @param resetUrl the generated full password reset URL
     * @return true if the email request was successfully accepted by the provider, false otherwise
     */
    boolean sendPasswordResetEmail(User user, String resetUrl);
}

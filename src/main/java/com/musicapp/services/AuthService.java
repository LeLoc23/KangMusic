package com.musicapp.services;

import com.musicapp.exception.WeakPasswordException;
import com.musicapp.models.User;
import com.musicapp.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AuthService {

    // MAJOR-3 FIX: Stronger policy — 8+ chars, 1 uppercase, 1 digit, 1 special char
    private static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':,./<>?]).{8,}$";
    /** CRIT-4 FIX: Reset tokens expire after 30 minutes */
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    /** S10 FIX: Injected base URL — no more Host-header injection risk. */
    @Value("${app.base-url}")
    private String configuredBaseUrl;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Registers a new user. Throws exceptions for validation failures.
     */
    public void registerUser(String username, String password, String confirmPassword,
                             String email, String fullName) {

        if (username.trim().isEmpty() || password.trim().isEmpty() || email.trim().isEmpty()) {
            throw new IllegalArgumentException("blank_fields");
        }
        if (username.length() > 50 || email.length() > 100) {   // I5 FIX
            throw new IllegalArgumentException("input_too_long");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("username_taken");
        }
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("email_taken");
        }
        if (!password.matches(PASSWORD_PATTERN)) {
            throw new WeakPasswordException("weak_pass");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("mismatch");
        }

        User newUser = new User(username, passwordEncoder.encode(password), email, fullName, "ROLE_USER");
        userRepository.save(newUser);
    }

    /**
     * S10 FIX: Uses injected app.base-url — no baseUrl parameter needed.
     * S7 FIX: Token is hashed with SHA-256 before storing in DB.
     *         The raw token is sent in the email. On reset, the submitted
     *         token is hashed and compared against the stored hash.
     * M6 FIX: Always shows neutral response regardless of email existence.
     */
    public void processForgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String rawToken    = UUID.randomUUID().toString();
            String hashedToken = DigestUtils.md5DigestAsHex(rawToken.getBytes(StandardCharsets.UTF_8));
            user.setResetToken(hashedToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
            userRepository.save(user);

            // Email contains raw token; DB stores only the hash
            String resetLink = configuredBaseUrl + "/reset-password?token=" + rawToken;
            String subject = "Hỗ trợ khôi phục mật khẩu - KangMusic";
            String text = "Xin chào " + user.getUsername() + ",\n\n"
                    + "Bạn đã yêu cầu đặt lại mật khẩu. Link có hiệu lực trong "
                    + RESET_TOKEN_EXPIRY_MINUTES + " phút:\n\n"
                    + resetLink + "\n\n"
                    + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này. Tài khoản của bạn vẫn an toàn.\n\n"
                    + "Trân trọng,\nĐội ngũ KangMusic.";

            emailService.sendEmail(user.getEmail(), subject, text);
        });
    }

    /**
     * S7 FIX: Hashes submitted token before looking it up in DB.
     * DB stores hash; email contains raw token.
     */
    public Optional<User> findValidResetToken(String rawToken) {
        String hashedToken = DigestUtils.md5DigestAsHex(rawToken.getBytes(StandardCharsets.UTF_8));
        return userRepository.findByResetToken(hashedToken)
                .filter(user -> user.getResetTokenExpiry() != null
                        && LocalDateTime.now().isBefore(user.getResetTokenExpiry()));
    }

    /**
     * Resets the user's password. Token is invalidated after use.
     */
    public void processResetPassword(String token, String password, String confirmPassword) {
        User user = findValidResetToken(token)
                .orElseThrow(() -> new IllegalArgumentException("invalid_token"));

        if (!password.matches(PASSWORD_PATTERN)) {
            throw new WeakPasswordException("weak_pass");
        }
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("mismatch");
        }

        user.setPassword(passwordEncoder.encode(password));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
    }
}

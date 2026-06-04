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

@Service
@Transactional
public class AuthService {

    private static final String PASSWORD_PATTERN = "^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':,./<>?]).{8,}$";
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String configuredBaseUrl;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    public User registerUser(String username, String password, String confirmPassword,
                             String email, String fullName, String phoneNumber) {

        if (username.trim().isEmpty() || password.trim().isEmpty() || email.trim().isEmpty()) {
            throw new IllegalArgumentException("blank_fields");
        }
        if (username.length() > 50 || email.length() > 100) {
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
        newUser.setPhoneNumber(phoneNumber);
        newUser.setEmailVerified(false);
        userRepository.save(newUser);

        sendEmailVerification(newUser);
        return newUser;
    }

    public void sendEmailVerification(User user) {
        int codeVal = 100000 + new java.security.SecureRandom().nextInt(900000);
        String code = String.valueOf(codeVal);
        user.setEmailVerificationCode(code);
        user.setEmailVerificationExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        String subject = "Mã xác thực tài khoản - KangMusic";
        String text = "Xin chào " + user.getUsername() + ",\n\n"
                + "Cảm ơn bạn đã đăng ký tài khoản tại KangMusic.\n"
                + "Mã xác thực tài khoản của bạn là: " + code + "\n"
                + "Mã này có hiệu lực trong vòng 24 giờ.\n\n"
                + "Trân trọng,\nĐội ngũ KangMusic.";

        emailService.sendEmail(user.getEmail(), subject, text);
    }

    public boolean verifyEmail(String username, String code) {
        Optional<User> opt = userRepository.findByUsername(username);
        if (opt.isEmpty()) return false;
        User user = opt.get();
        if (user.isEmailVerified()) return true;
        if (code != null && code.equals(user.getEmailVerificationCode())
                && user.getEmailVerificationExpiry() != null
                && LocalDateTime.now().isBefore(user.getEmailVerificationExpiry())) {
            user.setEmailVerified(true);
            user.setEmailVerificationCode(null);
            user.setEmailVerificationExpiry(null);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public void processForgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int codeVal = 100000 + new java.security.SecureRandom().nextInt(900000);
            String rawCode = String.valueOf(codeVal);
            String hashedToken = DigestUtils.md5DigestAsHex(rawCode.getBytes(StandardCharsets.UTF_8));
            user.setResetToken(hashedToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));
            userRepository.save(user);

            // Email contains 6-digit code
            String subject = "Mã xác thực khôi phục mật khẩu - KangMusic";
            String text = "Xin chào " + user.getUsername() + ",\n\n"
                    + "Mã xác thực để đặt lại mật khẩu của bạn là: " + rawCode + "\n"
                    + "Mã này có hiệu lực trong " + RESET_TOKEN_EXPIRY_MINUTES + " phút.\n\n"
                    + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này. Tài khoản của bạn vẫn an toàn.\n\n"
                    + "Trân trọng,\nĐội ngũ KangMusic.";

            emailService.sendEmail(user.getEmail(), subject, text);
        });
    }

    public Optional<User> findValidResetCode(String email, String rawCode) {
        if (email == null || rawCode == null || rawCode.length() != 6) return Optional.empty();
        String hashedCode = DigestUtils.md5DigestAsHex(rawCode.getBytes(StandardCharsets.UTF_8));
        return userRepository.findByEmail(email)
                .filter(user -> hashedCode.equals(user.getResetToken())
                        && user.getResetTokenExpiry() != null
                        && LocalDateTime.now().isBefore(user.getResetTokenExpiry()));
    }

    public String processResetPassword(String email, String code, String password, String confirmPassword) {
        User user = findValidResetCode(email, code)
                .orElseThrow(() -> new IllegalArgumentException("invalid_code"));

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
        return user.getUsername();
    }
}

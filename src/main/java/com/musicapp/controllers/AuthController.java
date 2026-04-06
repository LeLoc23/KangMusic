package com.musicapp.controllers;

import com.musicapp.exception.WeakPasswordException;
import com.musicapp.services.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * M1 FIX: Controller delegates all business logic to AuthService.
 * M6 FIX: Forgot-password always shows a neutral success message (no user enumeration).
 * M8 FIX: Reset link uses dynamic base URL from HttpServletRequest.
 * C4 FIX: Token expiry validated inside AuthService.
 * I1 FIX: Constructor injection.
 */
@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam(required = false, defaultValue = "") String fullName,
            @RequestParam String email,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        try {
            authService.registerUser(username, password, confirmPassword, email, fullName);
            return "redirect:/login?success=true";
        } catch (WeakPasswordException e) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự, 1 chữ hoa, 1 số và 1 ký hiệu đặc biệt!");
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", mapError(e.getMessage()));
        }
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    /**
     * M6 FIX: Always returns neutral message — no user enumeration.
     * S10 FIX: baseUrl now injected in AuthService via @Value, not derived from request.
     */
    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email, Model model) {
        authService.processForgotPassword(email);  // fire-and-forget
        model.addAttribute("success",
                "Nếu email này tồn tại trong hệ thống, chúng tôi đã gửi đường dẫn khôi phục. Vui lòng kiểm tra hộp thư!");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam("token") String token, Model model) {
        // C4 FIX: validates expiry inside AuthService
        return authService.findValidResetToken(token)
                .map(user -> {
                    model.addAttribute("token", token);
                    return "reset-password";
                })
                .orElseGet(() -> {
                    model.addAttribute("error", "Đường dẫn khôi phục không hợp lệ hoặc đã hết hạn!");
                    return "forgot-password";
                });
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        try {
            authService.processResetPassword(token, password, confirmPassword);
            return "redirect:/login?success=true";
        } catch (WeakPasswordException e) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự, 1 chữ hoa, 1 số và 1 ký hiệu đặc biệt!");
            model.addAttribute("token", token);
            return "reset-password";
        } catch (IllegalArgumentException e) {
            if ("mismatch".equals(e.getMessage())) {
                model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
                model.addAttribute("token", token);
                return "reset-password";
            }
            model.addAttribute("error", "Đường dẫn khôi phục không hợp lệ hoặc đã hết hạn!");
            return "forgot-password";
        }
    }

    private String mapError(String code) {
        return switch (code) {
            case "blank_fields" -> "Email, Tên đăng nhập và Mật khẩu không được để trống!";
            case "username_taken" -> "Tên đăng nhập này đã tồn tại. Vui lòng chọn tên khác!";
            case "email_taken" -> "Email này đã được đăng ký cho một tài khoản khác!";
            case "mismatch" -> "Mật khẩu nhập lại không khớp!";
            case "input_too_long" -> "Tên đăng nhập hoặc email không được quá 50/100 ký tự!";
            default -> "Đã có lỗi xảy ra. Vui lòng thử lại!";
        };
    }
}
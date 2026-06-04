package com.musicapp.controllers;

import com.musicapp.exception.WeakPasswordException;
import com.musicapp.services.AuthService;
import com.musicapp.services.ImageCaptchaService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AuthService authService;
    private final com.musicapp.services.CustomUserDetailsService userDetailsService;
    private final ImageCaptchaService imageCaptchaService;

    public AuthController(AuthService authService,
                          com.musicapp.services.CustomUserDetailsService userDetailsService,
                          ImageCaptchaService imageCaptchaService) {
        this.authService = authService;
        this.userDetailsService = userDetailsService;
        this.imageCaptchaService = imageCaptchaService;
    }

    @GetMapping("/login")
    public String loginPage(Model model, HttpSession session) {
        addCaptcha(model, session);
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam(required = false, defaultValue = "") String fullName,
            @RequestParam(required = false, defaultValue = "") String phoneNumber,
            @RequestParam String email,
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            HttpSession session) {

        try {
            com.musicapp.models.User newUser = authService.registerUser(username, password, confirmPassword, email, fullName, phoneNumber);
            
            // Programmatic auto-login
            org.springframework.security.core.userdetails.UserDetails userDetails = 
                    userDetailsService.loadUserByUsername(newUser.getUsername());
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication = 
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            session.setAttribute("SPRING_SECURITY_CONTEXT", org.springframework.security.core.context.SecurityContextHolder.getContext());

            return "redirect:/profile?requireVerification=true";
        } catch (WeakPasswordException e) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự, 1 chữ hoa, 1 số và 1 ký tự đặc biệt.");
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", mapError(e.getMessage()));
        }
        return "register";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model, HttpSession session) {
        addCaptcha(model, session);
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email,
                                        @RequestParam(required = false, defaultValue = "") String captchaCode,
                                        @RequestParam(value = ImageCaptchaService.TOKEN_REQUEST_PARAM, required = false) String captchaToken,
                                        Model model,
                                        HttpSession session) {
        if (!imageCaptchaService.verifyAndConsume(session, captchaCode, captchaToken)) {
            model.addAttribute("error", "Mã xác minh không đúng. Vui lòng nhập lại.");
            addCaptcha(model, session);
            return "forgot-password";
        }

        authService.processForgotPassword(email);
        session.setAttribute("resetEmail", email);
        return "redirect:/reset-password";
    }

    @GetMapping("/reset-password")
    public String showResetPasswordPage(Model model, HttpSession session) {
        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password";
        }
        model.addAttribute("success", "Chúng tôi đã gửi mã xác thực 6 chữ số đến email của bạn. Vui lòng nhập mã để đổi mật khẩu.");
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam("code") String code,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model,
            HttpSession session) {

        String email = (String) session.getAttribute("resetEmail");
        if (email == null) {
            return "redirect:/forgot-password";
        }

        try {
            String username = authService.processResetPassword(email, code, password, confirmPassword);
            
            // Auto-login
            org.springframework.security.core.userdetails.UserDetails userDetails = 
                    userDetailsService.loadUserByUsername(username);
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken authentication = 
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
            session.setAttribute("SPRING_SECURITY_CONTEXT", org.springframework.security.core.context.SecurityContextHolder.getContext());
            
            session.removeAttribute("resetEmail");
            return "redirect:/?login_success=true";
        } catch (WeakPasswordException e) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 8 ký tự, 1 chữ hoa, 1 số và 1 ký tự đặc biệt.");
            return "reset-password";
        } catch (IllegalArgumentException e) {
            if ("mismatch".equals(e.getMessage())) {
                model.addAttribute("error", "Mật khẩu nhập lại không khớp.");
                return "reset-password";
            }
            model.addAttribute("error", "Mã xác thực không hợp lệ hoặc đã hết hạn.");
            return "reset-password";
        }
    }

    private String mapError(String code) {
        return switch (code) {
            case "blank_fields" -> "Email, tên đăng nhập và mật khẩu không được để trống.";
            case "username_taken" -> "Tên đăng nhập này đã tồn tại. Vui lòng chọn tên khác.";
            case "email_taken" -> "Email này đã được đăng ký cho một tài khoản khác.";
            case "mismatch" -> "Mật khẩu nhập lại không khớp.";
            case "input_too_long" -> "Tên đăng nhập hoặc email vượt quá độ dài cho phép.";
            default -> "Đã có lỗi xảy ra. Vui lòng thử lại.";
        };
    }

    private void addCaptcha(Model model, HttpSession session) {
        ImageCaptchaService.CaptchaData captcha = imageCaptchaService.generateCaptcha();
        imageCaptchaService.store(session, captcha);
        model.addAttribute("captchaImage", captcha.imageDataUri());
        model.addAttribute("captchaToken", captcha.token());
    }
}

package com.musicapp.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping; 
import org.springframework.web.bind.annotation.RequestParam;

import com.musicapp.models.User;
import com.musicapp.repositories.UserRepository;
import com.musicapp.services.EmailService; 

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    // THÊM MỚI: Gọi công cụ mã hóa mật khẩu vào làm việc
    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    // ==========================================
    // HÀM XỬ LÝ KHI NGƯỜI DÙNG BẤM NÚT ĐĂNG KÝ
    // ==========================================
    @PostMapping("/register")
    public String registerUser(
            @RequestParam(required = false, defaultValue = "") String fullName, 
            @RequestParam String email, 
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) { 

        if (username.trim().isEmpty() || password.trim().isEmpty() || email.trim().isEmpty()) {
            model.addAttribute("error", "Email, Tên đăng nhập và Mật khẩu không được để trống!");
            return "register";
        }

        if (userRepository.findByUsername(username) != null) {
            model.addAttribute("error", "Tên đăng nhập này đã tồn tại. Vui lòng chọn tên khác!");
            return "register";
        }

        if (userRepository.findByEmail(email) != null) {
            model.addAttribute("error", "Email này đã được đăng ký cho một tài khoản khác!");
            return "register";
        }

        if (!password.matches("^(?=.*[A-Z]).{6,}$")) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 6 ký tự và chứa ít nhất 1 chữ viết hoa!");
            return "register";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            return "register";
        }

        // ĐÃ NÂNG CẤP: Băm mật khẩu (passwordEncoder.encode) trước khi tạo User
        User newUser = new User(username, passwordEncoder.encode(password), email, fullName, "ROLE_USER");
        userRepository.save(newUser);

        return "redirect:/login?success=true"; 
    }

    // ==========================================
    // HÀM XỬ LÝ QUÊN MẬT KHẨU
    // ==========================================
    
    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String email, Model model) {
        User user = userRepository.findByEmail(email);
        
        if (user == null) {
            model.addAttribute("error", "Không tìm thấy tài khoản nào liên kết với email này!");
            return "forgot-password";
        }

        String token = java.util.UUID.randomUUID().toString();
        user.setResetToken(token); 
        userRepository.save(user);

        String resetLink = "http://localhost:8080/reset-password?token=" + token;
        
        String subject = "Hỗ trợ khôi phục mật khẩu - KangMusic";
        String text = "Xin chào " + user.getUsername() + ",\n\n"
                + "Bạn đã yêu cầu đặt lại mật khẩu. Vui lòng nhấn vào đường dẫn bên dưới để đặt mật khẩu mới:\n\n"
                + resetLink + "\n\n"
                + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này. Tài khoản của bạn vẫn an toàn.\n\n"
                + "Trân trọng,\nĐội ngũ KangMusic.";
        
        emailService.sendEmail(user.getEmail(), subject, text);

        model.addAttribute("success", "Chúng tôi đã gửi đường dẫn khôi phục vào email của bạn. Vui lòng kiểm tra hộp thư!");
        return "forgot-password";
    }

    // ===========================
    // HÀM XỬ LÝ ĐẶT LẠI MẬT KHẨU 
    // ===========================

    @GetMapping("/reset-password")
    public String showResetPasswordPage(@RequestParam("token") String token, Model model) {
        User user = userRepository.findByResetToken(token);
        
        if (user == null) {
            model.addAttribute("error", "Đường dẫn khôi phục không hợp lệ hoặc đã hết hạn!");
            return "forgot-password"; 
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {

        User user = userRepository.findByResetToken(token);
        
        if (user == null) {
            model.addAttribute("error", "Thao tác không hợp lệ!");
            return "forgot-password";
        }

        if (!password.matches("^(?=.*[A-Z]).{6,}$")) {
            model.addAttribute("error", "Mật khẩu phải có ít nhất 6 ký tự và 1 chữ hoa!");
            model.addAttribute("token", token); 
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu nhập lại không khớp!");
            model.addAttribute("token", token);
            return "reset-password";
        }

        // ĐÃ NÂNG CẤP: Băm mật khẩu mới trước khi lưu
        user.setPassword(passwordEncoder.encode(password));
        user.setResetToken(null); 
        
        userRepository.save(user);

        return "redirect:/login?success=true";
    }
}
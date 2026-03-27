package com.musicapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ĐÃ NÂNG CẤP: Sử dụng thuật toán băm BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cấu hình bảo vệ các trang web (Giữ nguyên các thiết lập cực chuẩn của bạn)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Tạm tắt csrf để Form gửi dữ liệu bình thường
            .headers(headers -> headers.frameOptions(frame -> frame.disable())) // Cho phép mở H2 Console
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/register", "/forgot-password", "/reset-password", "/css/**", "/media/**", "/h2-console/**").permitAll() 
                .requestMatchers("/admin/**").hasAuthority("ROLE_ADMIN") 
                .requestMatchers("/add").hasAuthority("ROLE_ADMIN") 
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(customSuccessHandler()) 
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            );

        return http.build();
    }

    // LOGIC CHUYỂN HƯỚNG TỰ ĐỘNG SAU KHI ĐĂNG NHẬP
    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            // Kiểm tra xem người vừa đăng nhập có quyền ROLE_ADMIN không?
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin) {
                response.sendRedirect("/admin"); // Là Admin -> Vào trang quản lý
            } else {
                response.sendRedirect("/"); // Là User -> Về trang chủ nghe nhạc
            }
        };
    }
}
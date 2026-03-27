package com.musicapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Tên đăng nhập (Bắt buộc và không được trùng lặp)
    @Column(unique = true, nullable = false)
    private String username;

    // Mật khẩu (Sẽ được mã hóa sau)
    @Column(nullable = false)
    private String password;

    // Email (Bắt buộc và không được trùng lặp)
    @Column(unique = true, nullable = false)
    private String email;

    private String fullName;

    // Quyền hạn
    private String role;

    // Xác định tài khoản có bị khóa hay không
    private boolean locked = false;

    //Mã dùng để đặt lại mật khẩu 
    private String resetToken;

    public User() {}

    // Thêm biến email vào hàm khởi tạo
    public User(String username, String password, String email, String fullName, String role) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
    }

    // --- GETTER & SETTER ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    // Getter & Setter cho Email
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    // Getter & Setter cho Reset Token
    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }
}
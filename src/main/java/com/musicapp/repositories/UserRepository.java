package com.musicapp.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.musicapp.models.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Hàm cực kỳ quan trọng: Tìm người dùng trong DB dựa vào tên đăng nhập
    User findByUsername(String username);

    // THÊM MỚI: Tìm người dùng dựa vào Email (Dùng để kiểm tra trùng lặp hoặc tìm lại mật khẩu)
    User findByEmail(String email);

    // THÊM MỚI: Tìm người dùng dựa vào Mã khôi phục (Dùng ở Giai đoạn gửi mail reset mật khẩu)
    User findByResetToken(String resetToken);
}
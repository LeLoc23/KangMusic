# KangMusic

> Web nghe nhạc và video trực tuyến xây dựng bằng Spring Boot, Thymeleaf và SQL Server. Dự án tập trung vào trải nghiệm nghe nhạc, quản lý thư viện cá nhân, playlist, creator/singer, bình luận theo bài hát và các tính năng AI hỗ trợ gợi ý nội dung.

![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-6DB33F?style=flat-square)
![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-CC2927?style=flat-square)
![Thymeleaf](https://img.shields.io/badge/View-Thymeleaf-005F0F?style=flat-square)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square)

## Tổng Quan

KangMusic là một ứng dụng web nghe nhạc/video với giao diện hiện đại theo phong cách streaming platform. Người dùng có thể tìm kiếm, phát nhạc, tạo playlist, lưu thư viện, bình luận và nhận gợi ý bài hát. Admin quản lý người dùng, media, yêu cầu creator/singer và kiểm duyệt nội dung creator đăng tải.

## Tính Năng Chính

| Nhóm | Chức năng |
| --- | --- |
| Tài khoản | Đăng ký, đăng nhập, đổi mật khẩu, quên mật khẩu, xác thực email |
| Phân quyền | `ROLE_USER`, `ROLE_CREATOR`, `ROLE_ADMIN` |
| Media | Upload audio/video, poster, lyrics, thể loại, album, cảm xúc |
| Player | Phát nhạc/video, queue, lyrics panel, tăng lượt nghe |
| Playlist | Tạo playlist, thư mục playlist, thêm/xóa/đổi tên/di chuyển playlist |
| Thư viện | Lưu bài hát yêu thích vào thư viện cá nhân |
| Creator/Singer | Người dùng gửi yêu cầu creator, admin duyệt, creator đăng media chờ kiểm duyệt |
| Admin | Quản lý user, role, khóa tài khoản, duyệt creator, duyệt media |
| AI | Chat hỏi về bài hát/video, gợi ý nhạc theo mood/yêu cầu, hỗ trợ xử lý lyrics |
| Email | Gửi mã xác thực và reset mật khẩu qua Brevo |

## Công Nghệ

| Layer | Công nghệ |
| --- | --- |
| Backend | Java 21, Spring Boot 3.2.4 |
| Web | Spring MVC, Thymeleaf, HTMX |
| Security | Spring Security, BCrypt, CSRF cookie token |
| Database | SQL Server, Spring Data JPA/HQL, Flyway |
| Frontend | HTML, CSS, JavaScript |
| Email | Brevo Transactional Email API |
| AI | OpenAI API |
| Build/Test | Maven Wrapper, JUnit 5, H2 memory cho test |

## Kiến Trúc Ngắn Gọn

```text
Browser
  -> Spring MVC Controllers
  -> Services
  -> Spring Data JPA Repositories
  -> SQL Server

External APIs:
  -> Brevo: gửi email xác thực/reset mật khẩu
  -> OpenAI: chatbot, recommendation, lyrics/transcription
```

Lưu ý theo yêu cầu môn học: truy cập database nội bộ dùng Spring Data JPA/HQL, không dùng API riêng để truy cập database. Các API bên ngoài như Brevo/OpenAI chỉ dùng cho email và AI, không truy cập trực tiếp database của dự án.

## Yêu Cầu Môi Trường

- JDK 21+
- SQL Server đang chạy local hoặc remote
- Maven Wrapper đã có sẵn trong project
- Brevo API key nếu muốn gửi email thật
- OpenAI API key nếu muốn bật tính năng AI thật

## Cấu Hình

Profile mặc định là `dev`. File cấu hình chính:

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`
- `src/test/resources/application-test.properties`

Khuyến nghị cấu hình bằng biến môi trường thay vì ghi key thật vào code:

Project tự động import file `.env` ở thư mục gốc khi chạy local. Nếu chưa có file này, copy từ `.env.example` rồi điền key thật đã rotate:

```powershell
Copy-Item .env.example .env
```

```properties
DB_URL=jdbc:sqlserver://localhost:1433;databaseName=KangMusic;encrypt=true;trustServerCertificate=true
DB_USERNAME=your_sqlserver_user
DB_PASSWORD=your_sqlserver_password

BREVO_API_KEY=your_brevo_api_key
BREVO_SENDER_EMAIL=your_verified_sender@example.com
BREVO_SENDER_NAME=KangMusic

OPENAI_API_KEY=your_openai_api_key
OPENAI_MODEL=gpt-4o-mini
OPENAI_CHAT_ENABLED=true
OPENAI_RECOMMENDATIONS_ENABLED=true
```

Trong test, project dùng H2 memory riêng để chạy nhanh và không ảnh hưởng SQL Server.

## Chạy Project

```bash
./mvnw clean test
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Trên Windows PowerShell:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Sau khi chạy thành công, mở:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/actuator/health
```

## Luồng Sử Dụng Chính

1. User đăng ký tài khoản và xác thực email.
2. User nghe nhạc/video, tạo playlist, lưu thư viện, bình luận.
3. User vào trang cá nhân để gửi yêu cầu Creator/Singer.
4. Admin vào dashboard để duyệt hoặc từ chối creator.
5. Creator được duyệt có thể upload media.
6. Media của creator chờ admin duyệt trước khi hiển thị công khai.
7. AI chat/recommendation hỗ trợ hỏi đáp và gợi ý nội dung liên quan.

## Cấu Trúc Thư Mục

```text
src/main/java/com/musicapp
  config/        Cấu hình security, async, migration runner
  controllers/   MVC controller và API endpoint
  models/        Entity JPA và enum domain
  repositories/  Spring Data JPA repositories
  services/      Business logic, email, AI, playlist, media

src/main/resources
  templates/     Thymeleaf pages/fragments
  static/        CSS, JS, media assets
  db/migration/  Flyway SQL migrations

src/test
  java/          Unit/integration tests
  resources/     Test profile dùng H2 memory
```

## Kiểm Thử

```powershell
.\mvnw.cmd test
```

Các test hiện có kiểm tra context Spring Boot và một số service xử lý audio. Khi thêm tính năng mới, ưu tiên bổ sung test ở tầng service/repository để đảm bảo luồng nghiệp vụ không bị vỡ.

## Ghi Chú Bảo Mật

- Không commit API key thật lên repository public.
- Không bật H2 console cho runtime chính.
- CSRF đang bật cho form và request thay đổi dữ liệu.
- Password được mã hóa bằng BCrypt.
- Database access đi qua JPA/HQL để đúng yêu cầu môn học.

# KangMusic

> Web nghe nhạc và video trực tuyến xây dựng bằng Spring Boot, Thymeleaf và SQL Server. Dự án tập trung vào trải nghiệm streaming, playlist, thư viện cá nhân, creator/singer, bình luận và các tính năng AI hỗ trợ gợi ý nội dung.

![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-6DB33F?style=flat-square)
![SQL Server](https://img.shields.io/badge/Database-SQL%20Server-CC2927?style=flat-square)
![Thymeleaf](https://img.shields.io/badge/View-Thymeleaf-005F0F?style=flat-square)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square)

## Tổng quan

KangMusic là ứng dụng web nghe nhạc/video theo phong cách streaming platform. Người dùng có thể tìm kiếm, phát nhạc, tạo playlist, lưu thư viện, bình luận theo bài hát và nhận gợi ý bằng AI. Creator/Singer có thể gửi media chờ duyệt, còn Admin quản lý người dùng, quyền hạn, creator request, media và lyrics.

## Tính năng chính

| Nhóm | Chức năng |
| --- | --- |
| Tài khoản | Đăng ký, đăng nhập, xác thực email, quên mật khẩu, reset mật khẩu, CAPTCHA |
| Phân quyền | `ROLE_USER`, `ROLE_CREATOR`, `ROLE_ADMIN` |
| Media | Upload audio/video, poster, lyrics, thể loại, album, cảm xúc |
| Player | Phát nhạc/video, queue, lyrics panel, tăng lượt nghe |
| Playlist | Tạo playlist, đổi tên, xóa, thêm/xóa bài hát, thư mục playlist |
| Thư viện | Lưu bài hát yêu thích vào thư viện cá nhân |
| Creator/Singer | Gửi yêu cầu creator, được admin duyệt, đăng media chờ kiểm duyệt |
| Admin | Quản lý user, role, khóa tài khoản, duyệt creator, duyệt media, quản lý lyrics |
| AI | Chat về bài hát/video, gợi ý nhạc theo mood/yêu cầu, hỗ trợ xử lý lyrics |
| Email | Gửi mã xác thực và reset mật khẩu qua Brevo |

## Công nghệ

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

## Yêu cầu môi trường

- JDK 21+
- SQL Server local hoặc remote
- Maven Wrapper có sẵn trong project
- Brevo API key nếu muốn gửi email thật
- OpenAI API key nếu muốn bật AI thật
- FFmpeg nếu muốn xử lý/chia nhỏ audio lớn cho luồng lyrics/transcription

## Cấu hình local

Project tự import file `.env` ở thư mục gốc khi chạy local. Tạo file `.env` từ mẫu:

```powershell
Copy-Item .env.example .env
```

Các biến thường dùng:

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

Profile mặc định là `dev`. File cấu hình chính:

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`
- `src/main/resources/application-prod.properties`
- `src/test/resources/application-test.properties`

Trong dev, media upload đang lưu vào `src/main/resources/static/media/`. Trong production nên override `app.upload.dir` sang một thư mục ngoài source code, ví dụ `/app/data/media/`.

## Chạy project

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Khi chạy thành công, mở:

```text
http://localhost:8080
```

Health check:

```text
http://localhost:8080/actuator/health
```

## Giữ repo sạch

Không commit các file runtime/local sau lên GitHub:

- `.env` và các file chứa secret thật
- `target/`
- `music_data/`
- `uploads/`
- `src/main/resources/static/media/`
- File database local như `*.mv.db`, `*.trace.db`
- File tạm Office như `~$*.docx`

Nếu cần dữ liệu mẫu, ưu tiên tạo migration/seed script có kiểm soát thay vì commit database local hoặc file upload thật. Các file media upload trên máy vẫn có thể dùng khi chạy local, nhưng Git sẽ bỏ qua chúng.

## Cấu trúc thư mục

```text
src/main/java/com/musicapp
  config/        Cấu hình security, async, migration runner
  controllers/   MVC controller và API endpoint
  models/        Entity JPA và enum domain
  repositories/  Spring Data JPA repositories
  services/      Business logic, email, AI, playlist, media

src/main/resources
  templates/     Thymeleaf pages/fragments
  static/        CSS, JS, runtime media directory
  db/migration/  Flyway SQL migrations

src/test
  java/          Unit/integration tests
  resources/     Test profile dùng H2 memory
```

## Luồng sử dụng chính

1. User đăng ký tài khoản và xác thực email.
2. User nghe nhạc/video, tạo playlist, lưu thư viện và bình luận.
3. User gửi yêu cầu Creator/Singer ở trang cá nhân.
4. Admin duyệt hoặc từ chối yêu cầu creator.
5. Creator được duyệt có thể upload media.
6. Media của creator chờ admin duyệt trước khi hiển thị công khai.
7. AI chat/recommendation hỗ trợ hỏi đáp và gợi ý nội dung liên quan.

## Kiểm thử

```powershell
.\mvnw.cmd test
```

Test hiện có kiểm tra Spring Boot context, cấu hình security và một số service xử lý audio. Khi thêm tính năng mới, ưu tiên bổ sung test ở tầng service/repository để bảo vệ luồng nghiệp vụ.

## Ghi chú bảo mật

- Không commit API key, mật khẩu database hoặc token thật.
- Không bật H2 console cho runtime chính.
- CSRF đang bật cho form và request thay đổi dữ liệu.
- Password được mã hóa bằng BCrypt.
- Database access đi qua JPA/HQL để đúng yêu cầu môn học.

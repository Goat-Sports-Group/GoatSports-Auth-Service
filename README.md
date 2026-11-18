# GoatSports Auth Service

Quản lý tài khoản, đăng nhập, phân quyền và hồ sơ thể thao của người dùng. Kết hợp Keycloak (đăng nhập Google)
với tài khoản nội bộ trong PostgreSQL. Đây cũng là nơi giữ **điểm ELO** của người chơi.

## Chức năng chính

- **Đăng ký, đăng nhập**: email + mật khẩu (mật khẩu được client mã hóa RSA trước khi gửi), OTP xác minh email,
  quên mật khẩu, đăng nhập Google qua Keycloak. Access/refresh token đặt trong cookie HttpOnly;
  mỗi người dùng chỉ giữ một refresh token trong Redis.
- **Giới hạn tần suất**: đăng nhập, gửi lại OTP, đặt lại mật khẩu (cấu hình qua `RATE_LIMIT_*`).
- **Phân quyền RBAC theo permission**: 3 role hệ thống (PLAYER, VENUE_OWNER, ADMIN) và role tùy biến cho nhân viên;
  role bị tạm dừng thì mất toàn bộ quyền.
- **Trạng thái tài khoản**: INACTIVE / BLOCKED chỉ báo sau khi nhập đúng mật khẩu; tài khoản bị khóa gửi được kháng nghị
  (`/api/v1/auth/account-appeals`), admin xử lý ở `/api/v1/admin/account-appeals`.
- **Xóa tài khoản**: 30 ngày ân hạn, đăng nhập lại trong thời gian này thì khôi phục; hết hạn thì
  `AccountDeletionJob` xóa mềm, xóa user Keycloak và phát sự kiện `user-deleted` cho các service khác dọn dữ liệu.
- **Hồ sơ thể thao và ELO**: môn, trình độ, lịch rảnh, vị trí; ghi nhận kết quả trận từ ghép trận và giải đấu
  (`/internal/match-results`) để cập nhật ELO.

## API chính

| Đường dẫn | Mô tả |
|---|---|
| `/api/v1/auth` | Đăng ký, đăng nhập, OTP, refresh, đăng xuất, Google callback |
| `/api/v1/users`, `/api/v1/users/me/sport-profiles`, `/api/v1/users/me/deletion` | Hồ sơ cá nhân, hồ sơ thể thao, yêu cầu xóa tài khoản |
| `/api/v1/admin/users`, `/roles`, `/permissions`, `/stats` | Quản trị người dùng, vai trò, quyền, thống kê |
| `/internal/users`, `/internal/sport-profiles`, `/internal/match-results` | API nội bộ cho service khác (cần `X-Gateway-Secret`) |

## Cấu trúc

```
src/main/java/iuh/fit/auth/
├── domain/          # entity, enum, quy tắc thuần (vd. PasswordPolicy)
├── application/     # service, port, DTO, mapper
├── infrastructure/  # JPA, Redis, Kafka, Keycloak, Feign, mail, demo seeder
└── presentation/    # REST controller
shared-kernel/       # thư viện dùng chung: @AuditLog, Feign interceptor
```

## Tích hợp

- **PostgreSQL** (Liquibase), **Redis** (OTP, refresh token, rate limit), **Keycloak** (admin client), **SMTP** (mail OTP).
- **Kafka phát**: `user-registration`, `user-log`, `user-deleted`. **Kafka nhận**: `user-sync` từ plugin Keycloak (`UserSyncConsumer`).
- **Feign**: storage-service (avatar), notification-service (đá phiên đăng nhập), venue/payment/club (kiểm tra điều kiện xóa tài khoản).

## Chạy và kiểm thử

```bash
mvn -f shared-kernel/pom.xml clean install -DskipTests   # cài thư viện dùng chung trước
mvn spring-boot:run -Dspring-boot.run.profiles=dev       # cần application-dev.properties (bị .gitignore)
mvn test
```

Bật `DEMO_DATA_ENABLED=true` để tạo tài khoản mẫu cho môi trường dev.

## CI/CD

`.gitlab-ci.yml`: test → `jib:build` lên Docker Hub → gọi Jenkins job `GoatSports-CD`.

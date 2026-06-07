# Auth Design

Tài liệu này mô tả thiết kế xác thực và phân quyền hiện tại của project `shoppingweb`.

Project đang có hai bề mặt truy cập song song:

- Web MVC dùng Spring Security session và form login.
- REST API dùng JWT access token và refresh token.

Hai bề mặt này dùng chung `UserService`, `CustomUserDetailsService`, `PasswordEncoder`, role và trạng thái user trong database.

## 1. Security Model

Security được tách thành hai `SecurityFilterChain` trong `SecurityConfig`.

### API chain: `/api/**`

- Stateless.
- Tắt CSRF.
- Đọc JWT từ header `Authorization: Bearer <token>`.
- Dùng `JwtAuthenticationFilter` để validate token và set `Authentication`.
- Lỗi auth/access trả JSON qua `JwtAuthenticationEntryPoint` và `RestAccessDeniedHandler`.

### Web chain

- Stateful session.
- Dùng form login ở `/auth/login`.
- Login thành công redirect theo role bằng `SecurityUtils.resolvePostLoginTarget(...)`.
- `UserStatusEnforcementFilter` kiểm tra session user còn `ACTIVE` hay không.
- Lỗi access của web redirect về `/catalog?denied=true`.

## 2. API Access Matrix

| Endpoint | Method | Access |
| --- | --- | --- |
| `/api/auth/login` | `POST` | Public |
| `/api/auth/register` | `POST` | Public |
| `/api/auth/forgot-password` | `POST` | Public |
| `/api/auth/reset-password` | `POST` | Public |
| `/api/auth/refresh` | `POST` | Public |
| `/api/auth/me` | `GET` | Authenticated |
| `/api/auth/logout` | `POST` | Authenticated |
| `/api/users/profile` | `GET` | Authenticated |
| `/api/users/profile` | `PUT` | Authenticated |
| `/api/users/change-password` | `PUT` | Authenticated |
| `/api/users/account` | `DELETE` | Authenticated |
| `/api/products` | `GET` | Public |
| `/api/products/{slug}` | `GET` | Public |
| `/api/products` | `POST` | `ADMIN` or `SELLER` |
| `/api/products/{id}` | `PUT` | `ADMIN` or `SELLER` |
| `/api/products/{id}` | `DELETE` | `ADMIN` or `SELLER` |
| `/api/saved` | `GET` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/saved` | `POST` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/saved` | `DELETE` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/cart` | `GET` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/cart/items` | `POST` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/cart/items/{variantId}` | `PUT` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/cart/items/{variantId}` | `DELETE` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/orders` | `GET` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/orders/{orderId}` | `GET` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/orders` | `POST` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/api/admin/users` | `GET` | `ADMIN` |
| `/api/admin/users/{id}/role` | `PUT` | `ADMIN` |
| `/api/admin/users/{id}/status` | `PATCH` | `ADMIN` |

## 3. Web Access Matrix

| Route | Access |
| --- | --- |
| `/` | Public, redirects to `/catalog` |
| `/catalog` | Public |
| `/products/{slug}` | Public |
| `/auth/**` | Public |
| `/css/**`, `/js/**`, `/images/**`, `/fonts/**` | Public |
| `/profile` | Authenticated |
| `/account/**` | Authenticated |
| `/supplier/**` | Authenticated |
| `/saved/**` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/cart/**` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/checkout/**` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/orders/**` | `CUSTOMER` or `SELLER`, excluding `ADMIN` |
| `/dashboard/**` | `ADMIN` or `SELLER` |
| `POST /products` | `ADMIN` or `SELLER` |
| `/admin/**` | `ADMIN` |

Shopping business rule: Sellers may use customer shopping features, but they cannot add or checkout products that they own. Administrators are denied access to cart, saved-item, checkout, and personal-order endpoints.

For Web MVC requests, a self-purchase attempt returns `400 Bad Request` with the business-rule message. The catalog interaction script updates the cart badge only after a successful request and displays the server message when the request is rejected.

Lưu ý: dashboard template có phần customer view, nhưng security hiện tại chỉ cho `ADMIN` và `SELLER` vào `/dashboard`. Customer vẫn dùng các route như `/profile`, `/cart`, `/orders`, `/saved`.

## 4. Token Policy

- API login trả `accessToken` và `refreshToken`.
- Refresh token được lưu trong bảng `refresh_tokens`.
- Login xóa refresh token cũ của cùng user rồi tạo token mới.
- Refresh token rotation xóa token cũ và phát access token + refresh token mới.
- Logout xóa refresh token được gửi lên.
- Đổi mật khẩu, reset mật khẩu, block user, delete account đều revoke refresh token liên quan.
- JWT chỉ được chấp nhận nếu user vẫn tồn tại và còn trạng thái `ACTIVE`.

## 5. Current User Rules

- API lấy current user từ JWT đã được set vào `SecurityContext`.
- Web lấy current user từ Spring Security session.
- Controller dùng `SecurityUtils.requireCurrentUsername(...)` khi endpoint cần user hiện tại.
- `CustomUserDetailsService` chỉ load user hợp lệ cho Spring Security.
- `UserStatusEnforcementFilter` xử lý trường hợp user web session bị block/delete sau khi đã login.

## 6. Password Reset

Flow reset password dùng OTP qua email:

```text
POST /api/auth/forgot-password hoặc POST /auth/forgot-password
-> UserService.forgotPassword()
-> xóa token cũ của user
-> tạo OTP 6 số
-> lưu PasswordResetToken
-> gửi mail qua JavaMailSender

POST /api/auth/reset-password hoặc POST /auth/reset-password
-> UserService.resetPassword()
-> kiểm tra OTP tồn tại, chưa dùng, chưa hết hạn
-> đổi password
-> đánh dấu token đã dùng
-> revoke refresh token của user
```

## 7. Error Contract Cho API

API trả lỗi theo shape chuẩn:

```json
{
  "timestamp": "2026-04-18T23:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token has expired",
  "path": "/api/auth/refresh"
}
```

Các nhóm lỗi chính:

| Type | Status |
| --- | --- |
| Validation error | `400` |
| Business rule / invalid request data | `400` |
| Authentication failure | `401` |
| Access denied | `403` |
| Resource not found | `404` |
| Duplicate data | `409` |
| Unexpected server error | `500` |

## 8. Database Notes

Các bảng auth/user chính được map bằng JPA entity:

- `users`
- `roles`
- `user_roles`
- `refresh_tokens`
- `password_resets`

Role bắt buộc của hệ thống:

- `ADMIN`
- `CUSTOMER`
- `SELLER`

`SystemDataInitializer` có thể seed role, demo admin, demo seller, category và product mẫu, nhưng chỉ chạy khi bật:

```properties
app.seed.system-data=true
```

Hiện `application.properties` đang đặt `spring.jpa.hibernate.ddl-auto=none`, nên database schema phải tồn tại trước khi app chạy.

## 9. Production Notes

- Không dùng JWT secret mặc định cho production.
- Không commit DB password hoặc mail password thật.
- Nên thêm migration bằng Flyway hoặc Liquibase.
- Nên thêm rate limit cho login, forgot password và reset password nếu public API được mở rộng.
- Nên bổ sung test cho login, refresh token rotation, logout, password reset, admin block user và các endpoint cần role.

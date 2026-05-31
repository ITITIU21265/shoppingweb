# Manual Auth Checklist

Checklist này dùng để test tay các flow auth, phân quyền và các endpoint phụ thuộc authentication trong project `shoppingweb`.

## 1. Chuẩn Bị

- MySQL đang chạy và có database `clothing_shop`.
- Schema đã được tạo sẵn vì `spring.jpa.hibernate.ddl-auto=none`.
- Có đủ role `ADMIN`, `CUSTOMER`, `SELLER`.
- Nếu muốn seed demo data, chạy app với `app.seed.system-data=true` trên database trống.
- Nếu test reset password thật, cấu hình `MAIL_USERNAME` và `MAIL_PASSWORD`.

Tài khoản demo khi bật seeder:

| Username | Password | Role |
| --- | --- | --- |
| `admin` | `Admin@123` | `ADMIN` |
| `seller` | `Seller@123` | `SELLER` |

## 2. Web Auth

- `GET /auth/login` render trang login/register.
- `POST /auth/register` tạo user mới với role mặc định `CUSTOMER`.
- `POST /auth/register` báo lỗi khi username hoặc email bị trùng.
- `POST /auth/login` với credential đúng tạo session và redirect theo role.
- `POST /auth/login` với credential sai redirect về `/auth/login?error=true`.
- `POST /auth/logout` xóa session và cookie `JSESSIONID`.
- User bị block/delete sau khi đã login bị đẩy về `/auth/login?inactive=true` ở request kế tiếp.

## 3. Web Password Reset

- `GET /auth/forgot-password` render form nhập email.
- `POST /auth/forgot-password` luôn hiển thị thông báo chung, không lộ email có tồn tại hay không.
- `GET /auth/reset-password` render form nhập OTP và mật khẩu mới.
- `POST /auth/reset-password` báo lỗi với OTP sai, hết hạn hoặc đã dùng.
- `POST /auth/reset-password` thành công thì user login được bằng mật khẩu mới.
- Sau reset password, refresh token cũ của user không dùng lại được.

## 4. API Auth Public Endpoints

- `POST /api/auth/login` thành công với credential đúng và trả `accessToken`, `refreshToken`.
- `POST /api/auth/login` trả `401` với password sai.
- `POST /api/auth/register` thành công với dữ liệu hợp lệ.
- `POST /api/auth/register` trả `409` khi username hoặc email bị trùng.
- `POST /api/auth/forgot-password` trả thông báo chung.
- `POST /api/auth/reset-password` trả `400` với OTP sai, hết hạn hoặc đã dùng.
- `POST /api/auth/refresh` trả access token mới và refresh token mới.
- Refresh bằng refresh token cũ sau rotation phải thất bại.

## 5. API Authenticated Endpoints

- `GET /api/auth/me` trả current user khi có access token hợp lệ.
- `GET /api/auth/me` trả `401` khi không có token.
- `POST /api/auth/logout` revoke refresh token được gửi lên.
- `GET /api/users/profile` trả profile của user hiện tại.
- `PUT /api/users/profile` cập nhật full name và email.
- `PUT /api/users/change-password` đổi mật khẩu với current password đúng.
- `PUT /api/users/change-password` trả lỗi với current password sai.
- `DELETE /api/users/account` soft-delete user sau khi xác nhận password.
- Sau khi đổi mật khẩu hoặc delete account, refresh token cũ không dùng lại được.

## 6. API Product, Saved, Cart, Order

- `GET /api/products` public và trả danh sách sản phẩm active.
- `GET /api/products/{slug}` public và trả chi tiết sản phẩm active.
- `POST /api/products` trả `403` với `CUSTOMER`, thành công với `ADMIN` hoặc `SELLER`.
- `PUT /api/products/{id}` chỉ cho `ADMIN` hoặc `SELLER`.
- `DELETE /api/products/{id}` chỉ cho `ADMIN` hoặc `SELLER`.
- `GET /api/saved` trả `401` nếu không có token.
- `POST /api/saved` toggle hoặc thêm product vào saved list cho user hiện tại.
- `DELETE /api/saved` remove product khỏi saved list cho user hiện tại.
- `GET /api/cart` trả cart hiện tại của user.
- `POST /api/cart/items` thêm item vào cart.
- `PUT /api/cart/items/{variantId}` cập nhật quantity.
- `DELETE /api/cart/items/{variantId}` xóa item.
- `POST /api/orders` checkout từ selected cart item.
- `GET /api/orders` trả order history của user.
- `GET /api/orders/{orderId}` chỉ trả order thuộc user hiện tại.

## 7. Admin Endpoints

- `GET /api/admin/users` trả `403` với non-admin.
- `GET /api/admin/users` trả danh sách user với admin token.
- `PUT /api/admin/users/{id}/role` đổi role user với admin token.
- `PATCH /api/admin/users/{id}/status` block/reactivate user với admin token.
- Sau khi admin block user, access token cũ của user đó không còn được JWT auth chấp nhận.
- Sau khi admin đổi role, refresh token cũ của user đó bị revoke.

## 8. Web Protected Pages

- `/catalog` và `/products/{slug}` truy cập public.
- `/cart`, `/checkout`, `/orders`, `/saved`, `/profile`, `/supplier/register` yêu cầu login.
- `/dashboard` chỉ cho `ADMIN` hoặc `SELLER` theo cấu hình security hiện tại.
- `/admin/**` chỉ cho `ADMIN`.
- `POST /products` chỉ cho `ADMIN` hoặc `SELLER`.

## 9. Token Lifecycle

- Login revoke refresh token cũ của cùng user.
- Refresh rotate token: token cũ fail, token mới dùng được.
- Logout revoke token được gửi lên.
- Đổi mật khẩu revoke toàn bộ refresh token của user.
- Reset mật khẩu revoke toàn bộ refresh token của user.
- Block hoặc delete user làm refresh token cũ không dùng được.
- JWT access token của user bị block/delete bị từ chối vì user không còn `ACTIVE`.

## 10. Negative Cases Nên Kiểm Tra

- Gọi endpoint authenticated không có token.
- Gọi endpoint admin bằng customer token.
- Gọi endpoint seller/admin product bằng customer token.
- Checkout với cart rỗng.
- Checkout với selected variant không thuộc cart.
- Checkout với quantity lớn hơn stock.
- Reset password với confirm password không khớp.
- Register với email sai format hoặc password quá ngắn.

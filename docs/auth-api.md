# Auth API

Auth dùng access JWT trong response body và refresh token trong cookie `HttpOnly`. Response thành công của register/login/Google/refresh có cùng schema:

```ts
interface AuthResponse {
  accessToken: string;
  userId: number;
  email: string;
  role: "USER" | "ADMIN";
}
```

Mọi request trong file này nên dùng `credentials: "include"` để browser nhận/gửi refresh cookie.

## Register

```http
POST /api/auth/register
Content-Type: application/json

{
  "fullName": "Nguyễn Văn An",
  "email": "an@example.com",
  "password": "secret123"
}
```

Response `201 Created`, body `AuthResponse`, kèm `Set-Cookie`.

Validation:

| Field | Rule |
|---|---|
| `fullName` | Bắt buộc, tối đa 150 ký tự; backend trim trước khi lưu |
| `email` | Bắt buộc, đúng email, tối đa 150; backend trim và lowercase |
| `password` | Bắt buộc, 8–100 ký tự |

Lỗi riêng: `409 DUPLICATE_EMAIL`.

## Login bằng mật khẩu

```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "an@example.com",
  "password": "secret123"
}
```

Response `200 OK`, body `AuthResponse`, kèm `Set-Cookie`. Email được trim/lowercase. Sai email hoặc mật khẩu trả `401 INVALID_CREDENTIALS`; tài khoản bị khóa trả `401 ACCOUNT_DISABLED`.

## Login bằng Google

Frontend dùng Google Identity Services để lấy ID token từ `response.credential`, sau đó gửi:

```http
POST /api/auth/google
Content-Type: application/json

{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

Response `200 OK`, body `AuthResponse`, kèm `Set-Cookie`.

Backend xác minh chữ ký Google JWKS, thời hạn, issuer, audience đúng `GOOGLE_CLIENT_ID` và `email_verified`. Backend tìm tài khoản theo `googleId`; nếu chưa có thì liên kết tài khoản trùng email; nếu vẫn chưa có thì tạo user mới role `USER`.

| HTTP | Code | Khi nào |
|---:|---|---|
| 400 | `VALIDATION_FAILED` | Thiếu/rỗng `idToken` |
| 401 | `INVALID_GOOGLE_TOKEN` | Token sai, hết hạn, sai issuer/audience hoặc email chưa verify |
| 401 | `ACCOUNT_DISABLED` | Tài khoản bị vô hiệu hóa |
| 503 | `GOOGLE_LOGIN_NOT_CONFIGURED` | Server chưa cấu hình Google Client ID |

Ví dụ callback:

```ts
async function onGoogleCredential(idToken: string) {
  const response = await fetch(`${API_URL}/api/auth/google`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ idToken }),
  });
  // Parse ApiError nếu !response.ok; nếu OK lưu accessToken trong auth store.
}
```

Origin frontend phải xuất hiện trong cả Google Authorized JavaScript origins và backend `CORS_ALLOWED_ORIGINS`.

## Current user

```http
GET /api/auth/me
Authorization: Bearer <accessToken>
```

```json
{
  "id": 7,
  "fullName": "Nguyễn Văn An",
  "email": "an@example.com",
  "avatarUrl": "https://lh3.googleusercontent.com/...",
  "role": "USER",
  "createdAt": "2026-09-01T08:00:00Z",
  "updatedAt": "2026-09-07T08:00:00Z"
}
```

`avatarUrl` có thể là `null`.

## Refresh

```http
POST /api/auth/refresh
Cookie: refresh_token=<HttpOnly cookie>
```

Không có body và không cần Bearer. Response `200 AuthResponse` cùng một refresh cookie mới. Token cũ bị revoke ngay khi rotate; client phải áp dụng refresh single-flight như [api-conventions.md](./api-conventions.md#refresh-interceptor).

Thiếu cookie trả `401 MISSING_REFRESH_TOKEN`; cookie sai/hết hạn/đã dùng/tài khoản disabled trả `401 INVALID_REFRESH_TOKEN`. Cả hai response đều gửi cookie hết hạn để browser xóa.

## Logout thiết bị hiện tại

```http
POST /api/auth/logout
```

Không có body. Response luôn `204 No Content` và clear cookie. Nếu cookie hợp lệ, backend revoke cả token family của thiết bị đó. Frontend xóa access token dù request logout có lỗi mạng.

## Logout mọi thiết bị

```http
POST /api/auth/logout-all
Authorization: Bearer <accessToken>
```

Response `204 No Content`, revoke mọi refresh token của user và clear cookie hiện tại. Frontend xóa access token.

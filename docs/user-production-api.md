# User production APIs

Tài liệu này mô tả các API bổ sung sau MVP. Tất cả API riêng tư dùng
`Authorization: Bearer <accessToken>`. Các API đăng nhập/refresh cần
`credentials: "include"` để gửi refresh cookie.

## 1. Lịch sử và tiến bộ

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/interview-sessions` | Lịch sử có phân trang; lọc bằng `keyword`, `status`, `mode`, `createdFrom`, `createdTo`, `page`, `size` |
| `GET` | `/api/interview-sessions/progress?days=30` | Completion rate, điểm trung bình, thay đổi so với kỳ trước, trend tối đa 30 phiên và 5 nhóm kỹ năng yếu |

Mỗi item lịch sử có `nextAction`: `WAIT_FOR_PREPARATION`, `RETRY_PREPARATION`,
`START`, `CONTINUE`, `WAIT_FOR_SCORING`, `RETRY_SCORING`, `VIEW_REPORT` hoặc
`NONE`. Frontend nên điều hướng theo trường này thay vì tự suy luận từ status.

`completionRate` dùng các session được tạo trong khoảng thống kê làm cohort, vì
vậy giá trị luôn nằm trong `0..100`. Điểm và trend dùng các report hoàn tất trong
khoảng đó.

`days` nằm trong khoảng `7..365`. Thời gian lọc dùng ISO-8601, ví dụ
`2026-09-01T00:00:00Z`.

## 2. Tài khoản, bảo mật và dữ liệu cá nhân

### API public

| Method | Path | Body | Kết quả |
|---|---|---|---|
| `POST` | `/api/auth/email-verification/confirm` | `{ "token": "..." }` | `204` |
| `POST` | `/api/auth/password/forgot` | `{ "email": "an@example.com" }` | Luôn `202` để không lộ email có tồn tại |
| `POST` | `/api/auth/password/reset` | `{ "token": "...", "newPassword": "..." }` | `204`, thu hồi toàn bộ login session |

Sau khi `POST /api/auth/register` thành công, frontend gọi
`POST /api/auth/email-verification/request` bằng access token vừa nhận. Google
account được đánh dấu xác minh ngay từ thông tin token của Google.

### API đã đăng nhập

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/auth/email-verification/request` | Gửi link xác minh; `202` |
| `GET` | `/api/users/me` | Hồ sơ, trạng thái xác minh, preferences và lịch xóa |
| `PATCH` | `/api/users/me` | Cập nhật `fullName`, avatar, ngôn ngữ, múi giờ, email/processing notification |
| `PUT` | `/api/users/me/password` | Đổi mật khẩu, thu hồi mọi refresh token và xóa cookie hiện tại |
| `GET` | `/api/users/me/sessions` | Danh sách thiết bị/IP, lần dùng gần nhất và cờ `current` |
| `DELETE` | `/api/users/me/sessions/{sessionId}` | Thu hồi một họ refresh token; id là chuỗi public an toàn |
| `GET` | `/api/users/me/export` | Tải JSON gồm tài khoản, CV/profile, JD/template, session/conversation/report |
| `GET` | `/api/users/me/deletion-request` | `200` nếu có yêu cầu, `404` nếu chưa có |
| `POST` | `/api/users/me/deletion-request` | Body `{ "confirmation":"DELETE", "currentPassword":"..." }`; `202` |
| `DELETE` | `/api/users/me/deletion-request` | Hủy trong grace period; `204` |

Mật khẩu mới dài `8..100`. Tài khoản Google chưa có mật khẩu có thể dùng luồng
forgot/reset để tạo mật khẩu. Xóa tài khoản mặc định được lên lịch sau 7 ngày;
admin không thể tự xóa qua API này. Worker purge xóa dữ liệu theo khóa ngoại và
đưa file CV/JD vào hàng đợi xóa storage có retry.

## 3. Quản lý và khôi phục phiên

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/interview-sessions/readiness` | Dùng body giống create; kiểm tra template, profile, option, speech và realtime trước khi tạo |
| `POST` | `/api/interview-sessions/{id}/cancel` | Hủy idempotent khi `PREPARING`, `READY` hoặc `PREPARATION_FAILED` |
| `POST` | `/api/interview-sessions/{id}/turns/{turnId}/retry` | Retry đúng candidate turn `FAILED` bằng content và idempotency key cũ |

Readiness trả `ready`, `requestedMode`, `capabilities` và danh sách `checks` có
status `PASS`, `WARNING`, `FAIL`. `ready=false` khi có ít nhất một `FAIL`.
Realtime vẫn hỗ trợ fallback qua endpoint disconnect với
`fallbackToTurnBased=true`.

Phiên bị hủy có `status=CANCELLED` và `endReason=USER_CANCELLED`. Retry turn
không tạo candidate answer thứ hai.

## 6. Khám phá nội dung và thông báo

### Template

`GET /api/interview-templates` hỗ trợ:

- `scope=mine|public|favorites|recent`
- `keyword`: tìm trong title, job title và nội dung phân tích
- `seniority`: khớp chính xác, không phân biệt hoa thường
- `language`: lọc `sourceLanguage`
- `technology`: lọc tên skill trong nội dung template
- `page`, `size` (`size` tối đa 100)

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/interview-templates/{id}/clone` | Body tùy chọn `{ "title":"..." }`; tạo draft riêng, không giữ liên kết JD |
| `POST` | `/api/interview-templates/{id}/favorite` | Idempotent; trả `{templateId,favorite:true}` |
| `DELETE` | `/api/interview-templates/{id}/favorite` | Idempotent; trả `{templateId,favorite:false}` |

Mở `GET /api/interview-templates/{id}` tự cập nhật recent view. Mẫu đã bị
unpublish không còn xuất hiện trong favorites/recent của người không sở hữu.

### Hồ sơ thủ công

`POST /api/profiles` nhận các trường giống `PUT /api/profiles/{id}` nhưng không
có `version`. Các mảng `educations`, `skills`, `projects` bắt buộc và có thể rỗng.
Response có `source=MANUAL`, `cvDocumentId=null`, `cvOriginalFilename=null`.
Sau đó vẫn cần gọi `/confirm` trước khi tạo interview session.

### Notification

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/notifications?unreadOnly=false&page=0&size=20` | Danh sách mới nhất trước |
| `GET` | `/api/notifications/unread-count` | `{ "unreadCount": number }` |
| `PATCH` | `/api/notifications/{id}/read` | Đánh dấu một thông báo thuộc user |
| `POST` | `/api/notifications/read-all` | Đánh dấu tất cả đã đọc |

Backend tạo thông báo cho CV, JD và report ở cả trạng thái thành công/thất bại.
Thông báo in-app luôn được lưu. Email chỉ gửi khi cả `emailNotifications` và
`processingNotifications` đang bật; lỗi email không đổi trạng thái processing.

## 7. Feedback và hỗ trợ

### Feedback

| Method | Path | Mô tả |
|---|---|---|
| `GET` | `/api/interview-sessions/{id}/feedback` | Lấy feedback đã gửi; `404` nếu chưa có |
| `PUT` | `/api/interview-sessions/{id}/feedback` | Tạo hoặc cập nhật feedback của session `COMPLETED` |

Body:

```json
{
  "questionRating": 5,
  "voiceRating": 4,
  "reportRating": 5,
  "comment": "Câu hỏi sát vị trí ứng tuyển."
}
```

Mỗi rating tùy chọn nhưng phải từ 1 đến 5; cần ít nhất một rating hoặc comment.
Mỗi user chỉ có một feedback trên một session.

### Support ticket

| Method | Path | Mô tả |
|---|---|---|
| `POST` | `/api/support-tickets` | Tạo ticket |
| `GET` | `/api/support-tickets?page=0&size=20` | Danh sách ticket của user |
| `GET` | `/api/support-tickets/{id}` | Chi tiết ticket của user |

Body tạo ticket:

```json
{
  "type": "INTERVIEW",
  "subject": "Không retry được câu trả lời",
  "description": "Lượt trả lời vẫn ở trạng thái FAILED.",
  "sessionId": 51,
  "turnId": 63
}
```

`type` gồm `GENERAL`, `TECHNICAL`, `INTERVIEW`, `VOICE`, `REPORT`, `ACCOUNT`.
`sessionId` và `turnId` là tùy chọn nhưng phải thuộc user và phải cùng một
session. Backend lưu snapshot chẩn đoán vào `contextJson` để lỗi/status không bị
mất khi session thay đổi. Ticket mới có status `OPEN` và mã `SUP-...` duy nhất.

# API quản trị production

Các endpoint trong tài liệu này yêu cầu access token của tài khoản có role `ADMIN`.
Tài khoản thường nhận `403 ACCESS_DENIED`; request chưa xác thực nhận `401`.

Backend không cung cấp API nâng role. Tài khoản admin đầu tiên có thể được tạo bằng
bootstrap có kiểm soát khi khởi động ứng dụng.

## Khởi tạo admin đầu tiên

Bootstrap mặc định bị tắt. Cấu hình các biến môi trường sau trong lần khởi động cần
tạo admin:

```env
APP_ADMIN_BOOTSTRAP_ENABLED=true
APP_ADMIN_BOOTSTRAP_EMAIL=admin@example.com
APP_ADMIN_BOOTSTRAP_PASSWORD=a-strong-admin-password
APP_ADMIN_BOOTSTRAP_FULL_NAME=System Administrator
```

Email được chuẩn hóa về chữ thường. Mật khẩu phải dài từ 12 đến 100 ký tự và được
lưu dưới dạng BCrypt. Backend chỉ tạo tài khoản khi hệ thống chưa có admin và email
cấu hình chưa thuộc tài khoản khác. Nếu email đã thuộc một `USER`, ứng dụng dừng
khởi động thay vì tự nâng quyền.

Sau khi tạo thành công, đặt `APP_ADMIN_BOOTSTRAP_ENABLED=false` và xóa
`APP_ADMIN_BOOTSTRAP_PASSWORD` khỏi môi trường triển khai. Những lần khởi động lại
không thay đổi admin, trạng thái tài khoản hoặc mật khẩu đã tồn tại. Khi triển khai
nhiều replica, chỉ bật bootstrap trên một replica trong lần khởi tạo đầu tiên.

## Tổng quan

```http
GET /api/admin/overview?days=7
Authorization: Bearer <admin-access-token>
```

`days` mặc định là `7`, hợp lệ từ `1` đến `90`. Số phiên chỉ tính các phiên được tạo
trong kỳ. `completionRate` bằng `completed / totalInPeriod * 100`, làm tròn hai chữ số.

```json
{
  "generatedAt": "2026-09-14T08:00:00Z",
  "periodDays": 7,
  "users": {
    "total": 120,
    "enabled": 115,
    "newInPeriod": 18
  },
  "sessions": {
    "totalInPeriod": 80,
    "completed": 55,
    "inProgress": 8,
    "preparationFailed": 4,
    "scoringFailed": 3,
    "completionRate": 68.75
  },
  "templates": {
    "published": 12
  }
}
```

## Người dùng

### Danh sách

```http
GET /api/admin/users?keyword=minh&role=USER&enabled=true&page=0&size=20
```

Các filter đều không bắt buộc. `keyword` tìm không phân biệt hoa thường theo tên và
email. `size` hợp lệ từ `1` đến `100`; kết quả mặc định sắp xếp mới nhất trước.

```json
{
  "items": [
    {
      "id": 15,
      "fullName": "Nguyễn Văn Minh",
      "email": "minh@example.com",
      "role": "USER",
      "enabled": true,
      "createdAt": "2026-09-10T08:00:00Z",
      "updatedAt": "2026-09-10T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### Chi tiết

```http
GET /api/admin/users/{userId}
```

Ngoài thông tin tài khoản, response có số CV/JD active, tổng số phiên và số phiên đã
hoàn thành. Backend không trả password hash hoặc Google ID.

### Khóa hoặc mở tài khoản

```http
PATCH /api/admin/users/{userId}/status
Content-Type: application/json

{
  "enabled": false
}
```

Endpoint chỉ thay đổi tài khoản role `USER`. Tài khoản `ADMIN` trả
`409 ADMIN_USER_STATUS_PROTECTED`. Khi khóa user, backend thu hồi mọi refresh token;
access token đã cấp cũng bị từ chối từ request tiếp theo. Gửi lại cùng trạng thái là
idempotent.

## Vận hành phiên phỏng vấn

### Danh sách

```http
GET /api/admin/interview-sessions?keyword=minh@example.com&status=SCORING_FAILED&mode=TURN_BASED&from=2026-09-01T00:00:00Z&to=2026-09-14T23:59:59Z&page=0&size=20
```

`keyword` tìm theo tên hoặc email chủ phiên. `from` và `to` lọc `createdAt` và dùng
ISO-8601. Nếu `from` sau `to`, backend trả `400 VALIDATION_FAILED`.

Danh sách chỉ trả metadata vận hành: owner, trạng thái, mode, tên template/profile,
mã lỗi và thời gian. Không trả CV, snapshot hoặc transcript.

### Chi tiết

```http
GET /api/admin/interview-sessions/{sessionId}
```

Response bổ sung error message, model/prompt/schema version, các mốc lifecycle và
`transitions` theo thứ tự thời gian tăng dần. Dữ liệu raw của template/profile và nội
dung hội thoại không nằm trong contract admin MVP.

### Retry preparation

```http
POST /api/admin/interview-sessions/{sessionId}/preparation/retry
```

Chỉ hợp lệ khi session đang `PREPARATION_FAILED`. Response `202` và session chuyển
sang `PREPARING`.

### Retry scoring

```http
POST /api/admin/interview-sessions/{sessionId}/scoring/retry
```

Chỉ hợp lệ khi session đang `SCORING_FAILED`. Response `202` và session chuyển sang
`SCORING`.

Hai thao tác retry khóa session trong transaction nên request đồng thời không thể
dispatch cùng một công việc hai lần. Transition được lưu với actor `ADMIN`.

### Queue bị treo và chẩn đoán

```http
GET /api/admin/interview-sessions/stale?status=PREPARING&staleMinutes=15&page=0&size=20
GET /api/admin/interview-sessions/{id}/diagnostics
```

Queue stale chỉ nhận `PREPARING`, `IN_PROGRESS` hoặc `SCORING`. Diagnostics trả mã
lỗi, message đã che chuỗi giống token/secret, model/prompt, provider, số lượt retry
của admin và các mốc hoạt động. Snapshot CV/JD và transcript không được trả về.

```http
POST /api/admin/interview-sessions/{id}/force-close
{"reason":"Realtime connection was lost"}

POST /api/admin/interview-sessions/{id}/terminate
{"reason":"Session cannot be recovered"}
```

`force-close` chỉ nhận phiên `IN_PROGRESS`, đóng hội thoại rồi chuyển sang `SCORING`.
`terminate` dừng phiên và chuyển sang `CANCELLED` với end reason
`SYSTEM_TERMINATED`. Có thể terminate phiên `SCORING` bị treo; worker đang chạy sẽ bỏ
kết quả nếu thấy session không còn ở trạng thái `SCORING`.

Hai API bulk nhận tối đa `ADMIN_BULK_RETRY_LIMIT` ID duy nhất, mặc định 25, và trả
kết quả thành công/thất bại riêng cho từng ID:

```http
POST /api/admin/interview-sessions/bulk/preparation/retry
POST /api/admin/interview-sessions/bulk/scoring/retry

{"sessionIds":[101,102,103]}
```

## Support và feedback

```http
GET /api/admin/support/tickets?keyword=SUP-123&status=OPEN&type=INTERVIEW&priority=HIGH&assignedAdminId=3&from=...&to=...&page=0&size=20
GET /api/admin/support/tickets/{id}
PATCH /api/admin/support/tickets/{id}/assignment
PATCH /api/admin/support/tickets/{id}/priority
PATCH /api/admin/support/tickets/{id}/status
POST /api/admin/support/tickets/{id}/messages
GET /api/admin/support/feedback?keyword=minh&rating=2&from=...&to=...&page=0&size=20
```

Ví dụ request:

```json
{"adminId":3}
{"priority":"URGENT"}
{"status":"RESOLVED","resolutionSummary":"Scoring was retried successfully"}
{"message":"We have regenerated your report.","internal":false}
```

`adminId: null` bỏ phân công. Message `internal: true` chỉ admin nhìn thấy; message
công khai tạo notification cho user. State machine cho phép:

- `OPEN` → `IN_REVIEW` hoặc `CLOSED`
- `IN_REVIEW` → `RESOLVED` hoặc `CLOSED`
- `RESOLVED` → `IN_REVIEW` hoặc `CLOSED`
- `CLOSED` → `IN_REVIEW`

`RESOLVED` bắt buộc có `resolutionSummary`. User trả lời ticket đã resolve sẽ tự
mở lại `IN_REVIEW`; ticket `CLOSED` không nhận thêm message công khai nhưng admin
vẫn có thể thêm ghi chú nội bộ.

## Quản lý truy cập user

```http
GET /api/admin/users/advanced?keyword=minh&emailVerified=true&accessStatus=SUSPENDED&deletionStatus=PENDING&lastLoginFrom=...&lastLoginTo=...&page=0&size=20
GET /api/admin/users/{id}/security
PATCH /api/admin/users/{id}/access
POST /api/admin/users/{id}/sessions/revoke
POST /api/admin/users/{id}/verification-email
POST /api/admin/users/{id}/password-reset-email
```

Request thay đổi access:

```json
{
  "status":"SUSPENDED",
  "reason":"Automated traffic investigation",
  "suspendedUntil":"2026-09-20T08:00:00Z"
}
```

`ACTIVE` xóa restriction, `SUSPENDED` yêu cầu reason và thời điểm tương lai,
`DISABLED` yêu cầu reason. Suspension tự hết hạn mà không cần scheduler. Cả
suspension và disable đều thu hồi refresh token; access token cũ bị từ chối khi
filter tải lại trạng thái user. Không endpoint nào trong nhóm này thay role admin.

## Kiểm duyệt template

Owner xác nhận template rồi gửi duyệt:

```http
POST /api/interview-templates/{id}/submit-review
{"expectedVersion":2}
```

Admin quản lý toàn bộ owner qua:

```http
GET /api/admin/templates?keyword=java&ownerId=7&status=PENDING_REVIEW&published=false&featured=false&category=backend&page=0&size=20
GET /api/admin/templates/{id}
POST /api/admin/templates/{id}/review
PATCH /api/admin/templates/{id}/metadata
POST /api/admin/templates/{id}/publish
POST /api/admin/templates/{id}/unpublish
```

```json
{"action":"APPROVE","reason":null,"expectedVersion":2}
```

Action gồm `APPROVE`, `REJECT`, `HIDE`. Reject và hide bắt buộc reason; reject gỡ
publish và mở khóa nội dung để owner sửa. Hide gỡ publish và featured. Publish chỉ
nhận template `APPROVED` khi `TEMPLATE_REVIEW_REQUIRED=true`.

Metadata sử dụng optimistic version:

```json
{
  "category":"Backend",
  "tags":["Java","Spring Boot"],
  "featured":true,
  "displayOrder":10,
  "expectedVersion":3
}
```

## Audit log

```http
GET /api/admin/audit-logs?actorId=3&action=USER_ACCESS_CHANGED&resourceType=USER&resourceId=7&from=...&to=...&page=0&size=20
```

Audit lưu actor, action, resource, before/after JSON, `X-Request-ID`, IP và thời
gian. API chỉ hỗ trợ đọc; không có endpoint sửa hoặc xóa audit log.

## Analytics

```http
GET /api/admin/analytics/summary?from=2026-09-01T00:00:00Z&to=2026-10-01T00:00:00Z
GET /api/admin/analytics/time-series?from=...&to=...
GET /api/admin/analytics/export?from=...&to=...
```

Không truyền thời gian sẽ dùng 30 ngày gần nhất. Khoảng tối đa là 366 ngày, dùng
quy ước `[from, to)` và time-series theo ngày UTC. Summary gồm user acquisition,
session funnel, completion/failure, support SLA, rating và template usage. Export
trả `text/csv`.

## Thông báo hệ thống

```http
GET /api/admin/announcements?status=SCHEDULED&page=0&size=20
GET /api/admin/announcements/audience-preview?audience=ACTIVE_USERS
GET /api/admin/announcements/{id}
POST /api/admin/announcements
PATCH /api/admin/announcements/{id}
POST /api/admin/announcements/{id}/schedule
POST /api/admin/announcements/{id}/cancel
```

Audience gồm `ALL_USERS`, `VERIFIED_USERS`, `ACTIVE_USERS`; active nghĩa là đăng
nhập trong 30 ngày. User disabled, đang suspended hoặc đang chờ xóa không được đưa
vào delivery. Mỗi announcement phải bật ít nhất một trong `inAppEnabled` và
`emailEnabled`. Delivery có unique key theo announcement/user, retry tối đa ba lần
và kết thúc ở `SENT` hoặc `PARTIALLY_FAILED`.

## Cấu hình và vận hành job

```http
GET /api/admin/system-settings
PATCH /api/admin/system-settings/{key}
{"value":"50","expectedVersion":0}
```

Các key hiện có:

- `ANNOUNCEMENTS_ENABLED`: bật worker phát announcement.
- `TEMPLATE_REVIEW_REQUIRED`: bắt buộc approved trước publish.
- `ADMIN_BULK_RETRY_LIMIT`: giới hạn 1–100 session mỗi bulk request.
- `BACKGROUND_JOB_RETENTION_DAYS`: giữ lịch sử job 7–365 ngày.

```http
GET /api/admin/operations/job-runs?jobName=ANNOUNCEMENT_DISPATCH&status=FAILED&from=...&to=...&page=0&size=20
POST /api/admin/operations/jobs/{jobName}/run
GET /api/admin/operations/storage-deletions?page=0&size=20
POST /api/admin/operations/storage-deletions/{id}/retry
GET /api/admin/operations/account-deletions?status=PENDING&page=0&size=20
POST /api/admin/operations/account-deletions/{id}/retry
```

Manual job dùng allowlist: `ACCOUNT_TOKEN_CLEANUP`, `REFRESH_TOKEN_CLEANUP`,
`INTERVIEW_DEADLINE`, `ANNOUNCEMENT_DISPATCH`. Storage/account deletion retry chỉ
đưa task về queue; worker scheduler thực hiện thao tác thật. Job bị ngắt quá một giờ
được đánh dấu `FAILED` khi ứng dụng khởi động và qua lượt quét định kỳ; lịch sử hoàn
tất được dọn theo retention.

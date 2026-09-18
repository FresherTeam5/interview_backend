# Database migrations với Liquibase

Liquibase là nguồn quản lý schema của ứng dụng. Hibernate chỉ kiểm tra entity có khớp schema bằng `ddl-auto: validate` và không tự sửa database.

## Cấu trúc

- `src/main/resources/db/changelog/db.changelog-master.yaml`: khai báo thứ tự changeset.
- `src/main/resources/db/changelog/000-core-schema.sql`: schema tài khoản, token, CV và profile.
- `database_docs/migrations/*.sql`: các migration JD, template và interview; Maven đóng gói chúng vào `db/changelog/sql` trong artifact.

Các changeset baseline dùng precondition `MARK_RAN`. Khi chạy trên database cũ đã có bảng tương ứng, Liquibase ghi nhận changeset mà không tạo lại bảng. Với database trống, Liquibase tạo toàn bộ schema.

## Thêm migration mới

1. Tạo file SQL mới trong `database_docs/migrations`, ví dụ `016-feature-name.sql`.
2. Thêm một changeset mới ở cuối `db.changelog-master.yaml` và trỏ `sqlFile` tới `db/changelog/sql/016-feature-name.sql`.
3. Không sửa nội dung changeset đã chạy trên môi trường dùng chung vì Liquibase kiểm tra checksum.
4. Chạy test hoặc khởi động ứng dụng trên một database trống để kiểm tra cả migration và Hibernate validation.

Có thể tắt Liquibase tạm thời bằng `LIQUIBASE_ENABLED=false`, nhưng không dùng tùy chọn này trong production.

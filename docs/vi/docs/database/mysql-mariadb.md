# <img src="https://skillicons.dev/icons?i=mysql" width="28" height="28" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /><span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;background:#242938;border-radius:8px;vertical-align:middle;margin:0 6px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="20" height="20" alt="MariaDB" style="display:block" /></span> MySQL / MariaDB

Trỏ plugin tới một cơ sở dữ liệu MySQL hoặc MariaDB có sẵn:

**Phù hợp cho:** máy chủ lớn và mạng nhiều máy chủ, đặc biệt nếu cần hỗ trợ [Liên Máy Chủ](/vi/docs/database/cross-server) hoặc đã chạy sẵn MySQL/MariaDB cho các plugin khác. Chịu tải nhiều kết nối cùng lúc tốt.
**Không phù hợp nếu:** bạn chỉ muốn thứ gì đó chạy được ngay không cần thiết lập, [SQLite](/vi/docs/database/sqlite) đơn giản hơn cho việc đó.

Tài liệu: [MySQL](https://dev.mysql.com/doc/) / [MariaDB](https://mariadb.com/kb/en/documentation/)

```yaml
database:
  type: mysql          # hoặc: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  ssl: disable
  pool-size: 10
```

- **Tạo sẵn cơ sở dữ liệu trống trước**, ví dụ `CREATE DATABASE enhancedechest;`. Plugin tự tạo và quản lý các bảng bên trong đó, nhưng bản thân cơ sở dữ liệu phải tồn tại sẵn thì plugin mới kết nối được.
- Đặt `ssl` thành `require` để mã hóa kết nối (thất bại nếu máy chủ không hỗ trợ TLS), hoặc `verify-full` để đồng thời xác minh certificate và hostname của máy chủ. Xem trang [SSL / TLS](/vi/docs/database/ssl-tls).

# <img src="https://skillicons.dev/icons?i=postgres" width="28" height="28" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> PostgreSQL

**Phù hợp cho:** các trường hợp giống [MySQL / MariaDB](/vi/docs/database/mysql-mariadb), máy chủ lớn và mạng cần [Liên Máy Chủ](/vi/docs/database/cross-server), nếu Postgres đã là lựa chọn cơ sở dữ liệu sẵn có của bạn.
**Không phù hợp nếu:** bạn chỉ muốn thứ gì đó chạy được ngay không cần thiết lập, [SQLite](/vi/docs/database/sqlite) đơn giản hơn cho việc đó.

Tài liệu: [postgresql.org](https://www.postgresql.org/docs/)

```yaml
database:
  type: postgres
  host: localhost
  port: 5432
  database: enhancedechest
  username: postgres
  password: "your-password"
  ssl: disable
  pool-size: 10
```

- **Tạo sẵn cơ sở dữ liệu trống trước**, ví dụ `CREATE DATABASE enhancedechest;`. Plugin tự tạo và quản lý các bảng bên trong đó, nhưng bản thân cơ sở dữ liệu phải tồn tại sẵn thì plugin mới kết nối được.
- Port mặc định của PostgreSQL là **5432**, nhớ đổi `port` khỏi giá trị mặc định của MySQL.
- Đặt `ssl` thành `require` hoặc `verify-full` để mã hóa kết nối, xem trang [SSL / TLS](/vi/docs/database/ssl-tls).

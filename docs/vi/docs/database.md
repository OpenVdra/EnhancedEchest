# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender trong một cơ sở dữ liệu. Bạn chọn backend bằng tùy chọn `database.type` trong `config.yml`.

| Backend | Giá trị `type` | Phù hợp nhất cho |
|---------|----------------|------------------|
| **SQLite** | `sqlite` | Máy chủ đơn lẻ, không cần thiết lập, dùng được ngay |
| **MySQL** | `mysql` | Các network dùng chung một cơ sở dữ liệu |
| **MariaDB** | `mariadb` | Các network dùng chung một cơ sở dữ liệu |
| **PostgreSQL** | `postgres` | Các thiết lập đã chạy sẵn Postgres |

::: tip Không cần driver
Tất cả driver cơ sở dữ liệu và connection pool HikariCP đều được đóng gói và relocate bên trong jar của plugin. Bạn không bao giờ cần cài driver trên máy chủ.
:::

## SQLite (mặc định)

SQLite không cần cấu hình gì. Khi khởi động lần đầu, plugin tạo file cơ sở dữ liệu trong thư mục dữ liệu của nó:

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

File được tạo tại `plugins/EnhancedEchest/enderchests.db`. SQLite luôn dùng một kết nối duy nhất, nên `pool-size` bị bỏ qua ở chế độ này.

## MySQL / MariaDB

Trỏ plugin tới một cơ sở dữ liệu MySQL hoặc MariaDB có sẵn:

```yaml
database:
  type: mysql          # hoặc: mariadb
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: "your-password"
  pool-size: 10
```

- Tạo cơ sở dữ liệu (schema) trước, ví dụ `CREATE DATABASE enhancedechest;`
- Plugin tự tạo và quản lý các bảng của riêng nó
- Driver đi kèm tương thích với MySQL 5.7+ và 8.x

## PostgreSQL

```yaml
database:
  type: postgres
  host: localhost
  port: 5432
  database: enhancedechest
  username: postgres
  password: "your-password"
  pool-size: 10
```

Port mặc định của PostgreSQL là **5432**, nên nhớ đổi `port` khỏi giá trị mặc định của MySQL.

## Chia sẻ dữ liệu giữa các máy chủ

Vì mọi dữ liệu nằm trong cơ sở dữ liệu, việc trỏ nhiều máy chủ tới **cùng một** cơ sở dữ liệu MySQL/MariaDB/PostgreSQL cho phép chúng chia sẻ lưu trữ rương Ender. Mô hình tải/lưu chống nhân đôi của plugin (tải mới khi mở và lưu ngay khi đóng) giữ nội dung nhất quán miễn là mỗi người chơi chỉ ở trên một máy chủ tại một thời điểm.

## Chuyển đổi backend

Để chuyển sang backend khác, đổi `database.type` (và các trường kết nối) rồi khởi động lại máy chủ. Lưu ý rằng EnhancedEchest **không** tự động sao chép các hàng dữ liệu hiện có giữa các backend; chỉ việc nhập [chuyển dữ liệu vanilla](/vi/docs/migration) là được tự động hóa.

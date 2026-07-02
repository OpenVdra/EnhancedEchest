# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender vào cơ sở dữ liệu. Chọn backend bằng tùy chọn `database.type` trong `config.yml`.

| Backend | Giá trị `type` | Phù hợp nhất cho |
|---------|----------------|------------------|
| **SQLite** | `sqlite` | Máy chủ đơn lẻ, không cần thiết lập, dùng được ngay |
| **MySQL** | `mysql` | Các network dùng chung một cơ sở dữ liệu |
| **MariaDB** | `mariadb` | Các network dùng chung một cơ sở dữ liệu |
| **PostgreSQL** | `postgres` | Các thiết lập đã chạy sẵn Postgres |

::: tip Không cần cài thêm gì
Tất cả driver cơ sở dữ liệu đã được đóng gói bên trong jar. Bạn không cần cài gì thêm trên máy chủ.
:::

## SQLite (mặc định)

SQLite không cần cấu hình gì. Plugin tự tạo file cơ sở dữ liệu tại `plugins/EnhancedEchest/enderchests.db` khi khởi động lần đầu.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

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

- Tạo cơ sở dữ liệu trước, ví dụ `CREATE DATABASE enhancedechest;`
- Plugin tự tạo và quản lý các bảng của riêng nó

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

Port mặc định của PostgreSQL là **5432**, nhớ đổi `port` khỏi giá trị mặc định của MySQL.

## Các Bảng

Plugin tự tạo và quản lý các bảng cơ sở dữ liệu của riêng nó. Bạn không bao giờ cần tự viết SQL.

| Bảng | Lưu gì |
|------|--------|
| `enderchests` | Nội dung, kích thước, tên và biểu tượng của mọi rương. |
| `players` | Tùy chọn của từng người chơi và tên trong game gần nhất của họ (dùng để tra cứu người chơi ngoại tuyến, xem bên dưới). |
| `schema_meta` | Phiên bản cơ sở dữ liệu, dùng cho việc nâng cấp tự động. |

### Nâng cấp tự động

Khi bạn cập nhật plugin, cơ sở dữ liệu sẵn có sẽ tự động được nâng cấp khi khởi động. Không cần thao tác thủ công, không mất dữ liệu, các rương và nội dung sẵn có luôn được giữ nguyên.

Như thường lệ, hãy giữ một bản sao lưu (SQLite [tự động sao lưu](/vi/docs/configuration), hoặc bản dump MySQL/PostgreSQL của riêng bạn) trước khi nâng cấp lớn, để phòng hờ.

### Tra cứu người chơi ngoại tuyến

`/ee view`, `/ee add`, `/ee resize`, `/ee delete` và `/ee transfer` đều tìm được người chơi qua tên dù họ đang ngoại tuyến, kể cả khi bạn mới gõ tên và đang chờ gợi ý. Tính năng này hoạt động tự động ngay khi người chơi đã mở rương Ender của họ ít nhất một lần. Người chơi mới sẽ được tìm qua danh sách người chơi có sẵn của server cho đến lúc đó.

## Chia Sẻ Dữ Liệu Giữa Các Máy Chủ

Trỏ nhiều máy chủ tới **cùng một** cơ sở dữ liệu MySQL/MariaDB/PostgreSQL cho phép chúng chia sẻ lưu trữ rương Ender. Người chơi thấy cùng một nội dung dù đăng nhập vào máy chủ nào, miễn là họ chỉ ở trên một máy chủ tại một thời điểm.

## Chuyển Đổi Backend

Để chuyển sang backend khác, đổi `database.type` (và các trường kết nối) rồi khởi động lại máy chủ. Dữ liệu hiện có không được sao chép tự động giữa các backend; chỉ việc nhập [chuyển dữ liệu vanilla](/vi/docs/migration) là được tự động hóa.

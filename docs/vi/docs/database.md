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

## Bảng & Schema

Plugin tự tạo và quản lý các bảng của riêng nó — bạn không bao giờ phải tự viết SQL. Ở phiên bản hiện tại, đó là:

| Bảng | Mục đích |
|------|----------|
| `enderchests` | Mỗi hàng là một rương (nội dung, kích thước, tên, biểu tượng, loại, thời hạn) — nơi lưu trữ chính. |
| `players` | Mỗi hàng là một người chơi: tùy chọn (chế độ chỉnh sửa), kích thước rương cơ bản do quyền quản lý, và tên trong game gần nhất của họ (để tra cứu người chơi **ngoại tuyến** — xem bên dưới). |
| `schema_meta` | Ghi lại phiên bản schema mà cơ sở dữ liệu đang dùng, được bộ nâng cấp tự động bên dưới sử dụng. |

### Nâng Cấp Tự Động, Có Phiên Bản

Khi bạn cập nhật plugin, schema cơ sở dữ liệu có thể thay đổi (thêm cột, đổi tên/gộp bảng). EnhancedEchest nâng cấp một cơ sở dữ liệu sẵn có **tự động và an toàn** khi khởi động:

- Một cơ sở dữ liệu mới được tạo thẳng ở schema mới nhất.
- Một cơ sở dữ liệu sẵn có được so sánh với phiên bản ghi trong `schema_meta`, và chỉ những bước nâng cấp mới hơn mới được áp dụng. Mỗi bước đều kiểm tra trước xem thay đổi của nó đã tồn tại chưa (cột đã có, bảng cũ đã mất, v.v.), nên chạy lại — hay một cơ sở dữ liệu nâng cấp dở dang — không bao giờ báo lỗi.
- Mọi bước nâng cấp đều chỉ thêm vào — các hàng và nội dung sẵn có luôn được giữ nguyên. (Khi nâng cấp từ phiên bản trước 1.0.4, bảng `player_settings` cũ được gộp vào `players` rồi xóa đi.)

Không bao giờ cần chuyển dữ liệu thủ công hay `ALTER TABLE`. Như thường lệ, hãy giữ một bản sao lưu (SQLite [tự động sao lưu](/vi/docs/configuration), hoặc bản dump MySQL/Postgres của riêng bạn) trước khi nâng cấp lớn, để phòng hờ.

### Tra Cứu Người Chơi Ngoại Tuyến

Các lệnh quản trị nhận tên người chơi — `/ee view`, `/ee add`, `/ee resize`, `/ee delete`, `/ee transfer` — tra tên đó ra UUID từ chỉ mục tên trong bảng `players`. Chỉ mục này được cập nhật vào lần đầu tiên người chơi mở rương Ender của họ (không phải lúc đăng nhập), và chỉ ghi khi tên của họ thực sự thay đổi so với lần ghi trước — người chơi quay lại với tên không đổi thì không tốn thêm lần ghi nào. Điều này nghĩa là `/ee view <name>` hoạt động với người chơi **ngoại tuyến** đã mở rương Ender ít nhất một lần kể từ khi bạn cài phiên bản này, không phụ thuộc vào usercache của server hay tra cứu Mojang. Một người chơi mới chỉ đăng nhập mà chưa từng mở rương Ender — hoặc lần cuối họ làm vậy là ở một phiên bản cũ hơn, trước khi có tính năng chỉ mục tên — sẽ được tra bằng usercache của server ở lần đầu, và được lập chỉ mục vào lần tiếp theo họ mở rương.

Tính năng gợi ý tên (tab-completion) của các lệnh trên cũng tra theo chỉ mục này, cùng với usercache riêng của server — nên một người chơi mà server chỉ biết qua bảng này (ví dụ được nhập thẳng vào cơ sở dữ liệu thay vì thực sự từng vào server) vẫn hiện ra khi gợi ý, và vẫn tra được đúng, dù server chưa từng thấy họ.

## Chia Sẻ Dữ Liệu Giữa Các Máy Chủ

Trỏ nhiều máy chủ tới **cùng một** cơ sở dữ liệu MySQL/MariaDB/PostgreSQL cho phép chúng chia sẻ lưu trữ rương Ender. Người chơi thấy cùng một nội dung dù đăng nhập vào máy chủ nào, miễn là họ chỉ ở trên một máy chủ tại một thời điểm.

## Chuyển Đổi Backend

Để chuyển sang backend khác, đổi `database.type` (và các trường kết nối) rồi khởi động lại máy chủ. Dữ liệu hiện có không được sao chép tự động giữa các backend; chỉ việc nhập [chuyển dữ liệu vanilla](/vi/docs/migration) là được tự động hóa.

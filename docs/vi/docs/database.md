# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender vào cơ sở dữ liệu. Chọn backend bằng tùy chọn `database.type` trong `config.yml`.

| Backend | Giá trị `type` | Phù hợp nhất cho |
|---------|----------------|------------------|
| <img src="https://skillicons.dev/icons?i=sqlite" width="20" height="20" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **SQLite** | `sqlite` | Máy chủ đơn lẻ, không cần thiết lập, dùng được ngay |
| <img src="https://skillicons.dev/icons?i=mysql" width="20" height="20" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **MySQL** | `mysql` | Các network dùng chung một cơ sở dữ liệu |
| <span style="display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;background:#242938;border-radius:6px;vertical-align:middle;margin:0 4px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="14" height="14" alt="MariaDB" style="display:block" /></span> **MariaDB** | `mariadb` | Các network dùng chung một cơ sở dữ liệu |
| <img src="https://skillicons.dev/icons?i=postgres" width="20" height="20" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **PostgreSQL** | `postgres` | Các thiết lập đã chạy sẵn Postgres |

::: tip Không cần cài thêm gì
Tất cả driver cơ sở dữ liệu đã được đóng gói bên trong jar. Bạn không cần cài gì thêm trên máy chủ.
:::

## <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite (mặc định)

SQLite không cần cấu hình gì. Plugin tự tạo file cơ sở dữ liệu tại `plugins/EnhancedEchest/enderchests.db` khi khởi động lần đầu.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Các file phụ cạnh cơ sở dữ liệu
SQLite chạy ở chế độ write-ahead logging (WAL) để có hiệu năng tốt hơn khi đông người, nên bạn có thể thấy thêm `enderchests.db-wal` và `enderchests.db-shm` cạnh file cơ sở dữ liệu. Đó là file của SQLite — cứ để nguyên, và đừng bao giờ tự copy file `.db` bằng tay khi máy chủ đang chạy (hãy dùng [sao lưu tự động](/vi/docs/configuration) tích hợp sẵn).
:::

Với máy chủ đông người — khoảng 100+ người chơi cùng lúc — hoặc nhiều máy chủ dùng chung một cơ sở dữ liệu, MySQL/MariaDB là lựa chọn tốt hơn.

## <img src="https://skillicons.dev/icons?i=mysql" width="28" height="28" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /><span style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;background:#242938;border-radius:8px;vertical-align:middle;margin:0 6px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="20" height="20" alt="MariaDB" style="display:block" /></span> MySQL / MariaDB

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

## <img src="https://skillicons.dev/icons?i=postgres" width="28" height="28" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> PostgreSQL

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

Bạn có thể chuyển toàn bộ dữ liệu hiện có từ backend này sang backend khác bằng lệnh tích hợp `/ee import` — ví dụ từ SQLite sang MySQL, hoặc từ MySQL sang PostgreSQL.

Ý tưởng rất đơn giản: bạn đặt backend **mới** làm backend đang dùng, rồi nhập backend **cũ** vào đó.

1. Tắt máy chủ và đảm bảo **không có người chơi nào trực tuyến** trong suốt quá trình.
2. Sửa `config.yml` để `database.type` (và các trường kết nối) trỏ tới backend **mới** — cái bạn muốn chuyển sang. Giữ nguyên tệp/cơ sở dữ liệu của backend cũ.
3. Khởi động máy chủ. Plugin tạo các bảng trống trong backend mới.
4. Chạy `/ee import`. Một biểu mẫu mở ra; điền thông tin kết nối của backend **cũ** mà bạn sao chép *từ đó*:
   - **Loại** — bấm nút để chuyển giữa **SQLite** (một tệp) và **Máy chủ**. Ba engine máy chủ (MySQL, MariaDB, PostgreSQL) dùng chung một biểu mẫu nên chỉ là một lựa chọn; engine được chọn theo cổng — dùng **5432** cho PostgreSQL, còn lại sẽ kết nối dạng MySQL/MariaDB.
   - Với SQLite, **tệp SQLite** (đường dẫn tuyệt đối, hoặc tương đối so với `plugins/EnhancedEchest`).
   - Với Máy chủ, **host** (dạng `host` hoặc `host:port`), **tên cơ sở dữ liệu**, **tên đăng nhập**, và **mật khẩu**.
5. Nhấn **Bắt đầu nhập**. Khi hoàn tất, số người chơi và số rương đã sao chép sẽ hiện trong chat.

Việc sao chép là từng byte một, nên nó nhanh và mọi nội dung, kích thước, tên, biểu tượng và thiết lập của rương đều được giữ nguyên chính xác.

::: warning Trước khi nhập, hãy đọc kỹ những điều này
- **Cập nhật backend cũ trước.** Hãy nạp cơ sở dữ liệu cũ bằng *phiên bản plugin này* một lần trước khi nhập, để schema của nó là mới nhất. Nhập từ schema lỗi thời sẽ thất bại với lỗi "source schema outdated" và không sao chép gì cả.
- **Không có người chơi trực tuyến.** Việc nhập sẽ từ chối chạy khi có bất kỳ ai khác ngoài bạn đang kết nối. Nội dung chỉ có thể được sao chép an toàn trên một máy chủ yên tĩnh.
- **Đích phải trống.** Việc nhập chỉ hoạt động vào một cơ sở dữ liệu mới chưa có rương nào. Điều này tránh vô tình gộp hoặc ghi đè dữ liệu hiện có.
:::

::: tip Nếu việc nhập thất bại
Không có gì được ghi trừ khi toàn bộ việc sao chép thành công (nó chạy trong một giao dịch duy nhất). Nếu thất bại giữa chừng, đích được để trống — chỉ cần khắc phục vấn đề được báo, xóa các bảng của đích (hoặc xóa tệp SQLite) để nó lại mới tinh, khởi động lại, và chạy lại `/ee import`.
:::

[Chuyển dữ liệu vanilla](/vi/docs/migration) và các bản nhập từ AxVaults / PlayerVaultsX là các tính năng riêng để kéo dữ liệu vào từ *các plugin khác*.

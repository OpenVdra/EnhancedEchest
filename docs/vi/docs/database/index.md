# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender vào cơ sở dữ liệu. Chọn backend bằng tùy chọn `database.type` trong `config.yml`: [SQLite](/vi/docs/database/sqlite), [MySQL / MariaDB](/vi/docs/database/mysql-mariadb), hoặc [PostgreSQL](/vi/docs/database/postgresql).

| Backend | Giá trị `type` | Phù hợp nhất cho |
|---------|----------------|------------------|
| <img src="https://skillicons.dev/icons?i=sqlite" width="20" height="20" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **SQLite** | `sqlite` | Hầu hết máy chủ, không cần thiết lập, dùng được ngay |
| <img src="https://skillicons.dev/icons?i=mysql" width="20" height="20" alt="MySQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **MySQL** | `mysql` | Các thiết lập muốn giữ dữ liệu trong máy chủ MySQL bên ngoài |
| <span style="display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;background:#242938;border-radius:6px;vertical-align:middle;margin:0 4px 0 0;box-sizing:border-box"><img src="https://cdn.simpleicons.org/mariadb/ffffff" width="14" height="14" alt="MariaDB" style="display:block" /></span> **MariaDB** | `mariadb` | Các thiết lập muốn giữ dữ liệu trong máy chủ MariaDB bên ngoài |
| <img src="https://skillicons.dev/icons?i=postgres" width="20" height="20" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 4px 0 0" /> **PostgreSQL** | `postgres` | Các thiết lập đã chạy sẵn Postgres |

::: tip Không cần cài thêm gì
Tất cả driver cơ sở dữ liệu đã được đóng gói bên trong jar. Bạn không cần cài gì thêm trên máy chủ.
:::

## Cách Dữ Liệu Được Lưu {#cach-du-lieu-duoc-luu}

Plugin giữ dữ liệu rương Ender của **từng người chơi đang trực tuyến** trong bộ nhớ (RAM):

- Khi một người chơi vào máy chủ, rương của họ được nạp từ cơ sở dữ liệu một
  lần duy nhất.
- Mở và đóng rương hoạt động hoàn toàn từ bộ nhớ, không có truy vấn cơ sở dữ liệu nào trong lúc chơi.
- Các thay đổi được ghi ngược về cơ sở dữ liệu tự động mỗi **3 phút** theo mặc định (cấu hình bằng
  `database.autosave-interval` trong `config.yml`), vài giây sau khi người chơi thoát, và một lần cuối
  khi máy chủ tắt.
- Sau khi người chơi thoát và thay đổi của họ đã được lưu, dữ liệu của họ được gỡ khỏi bộ nhớ, nên
  lượng RAM sử dụng tỉ lệ với số người chơi đang trực tuyến, không phải kích thước cơ sở dữ liệu.

Nhờ vậy plugin rất nhanh bất kể bạn chọn backend nào. Đánh đổi là: nếu tiến trình máy chủ bị tắt đột
ngột (crash, mất điện), các thay đổi sau lần ghi gần nhất sẽ mất. Mất nhiều nhất là một khoảng
autosave, và chỉ với những người chơi trực tuyến suốt khoảng đó; người chơi đã thoát được lưu trong
vài giây. Hãy giảm `autosave-interval` nếu bạn muốn thu hẹp khoảng rủi ro này.

```yaml
database:
  # Tần suất ghi các thay đổi trong bộ nhớ về cơ sở dữ liệu. Tối thiểu 30s.
  autosave-interval: 3m
```

## Các Bảng

Plugin tự tạo và quản lý các bảng cơ sở dữ liệu của riêng nó. Bạn không bao giờ cần tự viết SQL.

Mọi tên bảng đều có tiền tố (mặc định `echest_`) để dễ phân biệt dữ liệu của plugin với các plugin khác,
và an toàn khi dùng chung một cơ sở dữ liệu với chúng:

| Bảng | Lưu gì |
|------|--------|
| `echest_enderchests` | Nội dung, kích thước, tên và biểu tượng của mọi rương. |
| `echest_players` | Tùy chọn của từng người chơi và tên trong game gần nhất của họ (dùng để tra cứu người chơi ngoại tuyến, xem bên dưới). |
| `echest_schema_meta` | Phiên bản cơ sở dữ liệu, dùng cho việc nâng cấp tự động. |

Bạn có thể đổi tiền tố bằng `database.table-prefix` trong `config.yml`, ví dụ khi chạy nhiều máy chủ
dùng chung một cơ sở dữ liệu và muốn mỗi máy chủ có một tiền tố riêng:

```yaml
database:
  table-prefix: echest_
```

Nếu bạn đổi tiền tố trên một bản cài đã có sẵn, plugin sẽ tự đổi tên các bảng hiện có cho khớp vào lần
khởi động tiếp theo, không mất dữ liệu. Chỉ chữ cái, chữ số và dấu gạch dưới được giữ lại, ký tự khác sẽ
bị loại bỏ.

### Nâng cấp tự động

Khi bạn cập nhật plugin, cơ sở dữ liệu sẵn có sẽ tự động được nâng cấp khi khởi động. Không cần thao tác thủ công, không mất dữ liệu, các rương và nội dung sẵn có luôn được giữ nguyên.

Như thường lệ, hãy giữ một bản sao lưu (SQLite [tự động sao lưu](/vi/docs/configuration/), hoặc bản dump MySQL/PostgreSQL của riêng bạn) trước khi nâng cấp lớn, để phòng hờ.

### Tra cứu người chơi ngoại tuyến

`/ee view`, `/ee add`, `/ee resize`, `/ee delete` và `/ee transfer` đều tìm được người chơi qua tên dù họ đang ngoại tuyến, kể cả khi bạn mới gõ tên và đang chờ gợi ý. Tính năng này hoạt động tự động ngay khi người chơi đã mở rương Ender của họ ít nhất một lần. Người chơi mới sẽ được tìm qua danh sách người chơi có sẵn của server cho đến lúc đó.

# Cơ Sở Dữ Liệu

EnhancedEchest lưu nội dung của mọi rương Ender vào cơ sở dữ liệu. Chọn backend bằng tùy chọn `database.type` trong `config.yml`.

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
- Các thay đổi được ghi ngược về cơ sở dữ liệu tự động mỗi **5 phút** theo mặc định (cấu hình bằng
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
  autosave-interval: 5m
```

## <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite (mặc định)

SQLite không cần cấu hình gì. Plugin tự tạo file cơ sở dữ liệu tại `plugins/EnhancedEchest/enderchests.db` khi khởi động lần đầu.

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Các file phụ cạnh cơ sở dữ liệu
SQLite chạy ở chế độ write-ahead logging (WAL) để có hiệu năng tốt hơn khi đông người, nên bạn có thể thấy thêm `enderchests.db-wal` và `enderchests.db-shm` cạnh file cơ sở dữ liệu. Đó là file của SQLite: cứ để nguyên, và đừng bao giờ tự copy file `.db` bằng tay khi máy chủ đang chạy (hãy dùng [sao lưu tự động](/vi/docs/configuration) tích hợp sẵn).
:::

SQLite phù hợp với gần như mọi máy chủ: vì toàn bộ gameplay được phục vụ từ bộ nhớ, backend chỉ quyết định nơi dữ liệu được lưu trữ. Chọn MySQL/MariaDB/PostgreSQL nếu bạn muốn giữ dữ liệu trong một máy chủ cơ sở dữ liệu bên ngoài (sao lưu tập trung, công cụ sẵn có).

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
  ssl: disable
  pool-size: 10
```

- Tạo cơ sở dữ liệu trước, ví dụ `CREATE DATABASE enhancedechest;`
- Plugin tự tạo và quản lý các bảng của riêng nó
- Đặt `ssl` thành `require` để mã hóa kết nối (thất bại nếu máy chủ không hỗ trợ TLS), hoặc `verify-full` để đồng thời xác minh certificate và hostname của máy chủ. Xem mục [SSL / TLS](#ssl-tls) bên dưới.

## <img src="https://skillicons.dev/icons?i=postgres" width="28" height="28" alt="PostgreSQL" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> PostgreSQL

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

Port mặc định của PostgreSQL là **5432**, nhớ đổi `port` khỏi giá trị mặc định của MySQL.

## SSL / TLS

Tùy chọn `ssl` điều khiển mã hóa đường truyền cho cơ sở dữ liệu MySQL, MariaDB hoặc PostgreSQL từ xa. Nó không ảnh hưởng đến SQLite. Thay đổi giá trị này cần khởi động lại máy chủ hoàn toàn.

| Giá trị | Hành vi |
| --- | --- |
| `disable` | Không mã hóa (mặc định). |
| `require` | Mã hóa kết nối nhưng **không** xác minh certificate hay hostname của máy chủ. Chặn nghe lén thụ động, nhưng không chặn man-in-the-middle chủ động. |
| `verify-full` | Mã hóa **và** xác minh chuỗi certificate cùng hostname. Chế độ duy nhất chống được man-in-the-middle. |

`verify-full` yêu cầu CA (certificate authority) của máy chủ cơ sở dữ liệu phải được JVM chạy máy chủ Minecraft tin cậy (truststore). Nếu certificate là self-signed hoặc do CA riêng cấp, hãy import nó vào truststore của JVM trước, nếu không kết nối sẽ thất bại lúc khởi động.

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

Như thường lệ, hãy giữ một bản sao lưu (SQLite [tự động sao lưu](/vi/docs/configuration), hoặc bản dump MySQL/PostgreSQL của riêng bạn) trước khi nâng cấp lớn, để phòng hờ.

### Tra cứu người chơi ngoại tuyến

`/ee view`, `/ee add`, `/ee resize`, `/ee delete` và `/ee transfer` đều tìm được người chơi qua tên dù họ đang ngoại tuyến, kể cả khi bạn mới gõ tên và đang chờ gợi ý. Tính năng này hoạt động tự động ngay khi người chơi đã mở rương Ender của họ ít nhất một lần. Người chơi mới sẽ được tìm qua danh sách người chơi có sẵn của server cho đến lúc đó.

## Hỗ Trợ Liên Máy Chủ (Cross-Server) {#cross-server}

Nhiều máy chủ đứng sau proxy (Velocity, BungeeCord) có thể dùng chung một cơ sở dữ liệu, để rương Ender của người chơi đi theo họ giữa các máy chủ. Tính năng này mặc định tắt. Bật bằng `cross-server.enabled` trong `config.yml`.

Mọi máy chủ trong mạng cần hai điều kiện:

1. **Một cơ sở dữ liệu MySQL, MariaDB hoặc PostgreSQL dùng chung**: cấu hình `database` giống hệt nhau ở mọi nơi, kể cả `table-prefix`. SQLite không thể chia sẻ, và plugin sẽ từ chối khởi động ở chế độ liên máy chủ trên SQLite.
2. **Một máy chủ Redis dùng chung.** Redis không lưu dữ liệu rương Ender. Nó chỉ theo dõi máy chủ nào đang giữ dữ liệu của một người chơi, để hai máy chủ không bao giờ ghi đè thay đổi của nhau.

```yaml
cross-server:
  enabled: true
  # Tên riêng cho từng máy chủ ("survival", "skyblock", ...). Để trống thì tự sinh
  # tên mới mỗi lần khởi động. Không bao giờ đặt hai máy chủ trùng tên.
  server-id: "survival"
  redis:
    host: redis.example.com
    port: 6379
    password: ""
    ssl: false
    database: 0
    # Chỉ đổi khi nhiều mạng máy chủ riêng biệt dùng chung một Redis.
    key-prefix: "echest:"
```

Khi một người chơi đang trực tuyến, máy chủ của họ là máy chủ duy nhất được phép đụng vào dữ liệu của họ. Khi họ chuyển máy chủ, máy chủ cũ lưu rương của họ về cơ sở dữ liệu rồi bàn giao trước khi máy chủ mới đọc, nên máy chủ mới luôn thấy vật phẩm mới nhất. Việc chuyển server nhanh không thể làm mất hay nhân đôi vật phẩm. Nếu một máy chủ bị crash, quyền giữ dữ liệu của nó tự hết hạn trong khoảng 30 giây.

Lưu ý và giới hạn:

- Thay đổi trong mục `cross-server` cần **khởi động lại máy chủ hoàn toàn**, giống các cấu hình kết nối `database`.
- Nếu không kết nối được Redis lúc khởi động, plugin tự vô hiệu hóa thay vì chạy thiếu an toàn trên cơ sở dữ liệu dùng chung.
- Lệnh quản trị nhắm vào người chơi đang trực tuyến trên máy chủ **khác** sẽ báo lỗi. Hãy xem hoặc sửa họ trên máy chủ mà họ đang chơi.
- Chỉ chạy `/ee import` khi cả mạng chỉ còn một máy chủ đang bật.

## Chuyển Đổi Backend

Bạn có thể chuyển toàn bộ dữ liệu hiện có từ backend này sang backend khác bằng lệnh tích hợp `/ee import`, ví dụ từ SQLite sang MySQL, hoặc từ MySQL sang PostgreSQL.

Ý tưởng rất đơn giản: bạn đặt backend **mới** làm backend đang dùng, rồi nhập backend **cũ** vào đó.

1. Tắt máy chủ và đảm bảo **không có người chơi nào trực tuyến** trong suốt quá trình.
2. Sửa `config.yml` để `database.type` (và các trường kết nối) trỏ tới backend **mới**, tức backend bạn muốn chuyển sang. Giữ nguyên tệp/cơ sở dữ liệu của backend cũ.
3. Khởi động máy chủ. Plugin tạo các bảng trống trong backend mới.
4. Chạy `/ee import`. Một biểu mẫu mở ra; điền thông tin kết nối của backend **cũ** mà bạn sao chép *từ đó*:
   - **Loại**: bấm nút để chuyển giữa **SQLite** (một tệp) và **Máy chủ**. Ba engine máy chủ (MySQL, MariaDB, PostgreSQL) dùng chung một biểu mẫu nên chỉ là một lựa chọn; engine được chọn theo cổng (dùng **5432** cho PostgreSQL, còn lại sẽ kết nối dạng MySQL/MariaDB).
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
Không có gì được ghi trừ khi toàn bộ việc sao chép thành công (nó chạy trong một giao dịch duy nhất). Nếu thất bại giữa chừng, đích được để trống: chỉ cần khắc phục vấn đề được báo, xóa các bảng của đích (hoặc xóa tệp SQLite) để nó lại mới tinh, khởi động lại, và chạy lại `/ee import`.
:::

[Chuyển dữ liệu vanilla](/vi/docs/migration) và các bản nhập từ AxVaults / PlayerVaultsX là các tính năng riêng để kéo dữ liệu vào từ *các plugin khác*.

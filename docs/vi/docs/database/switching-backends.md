# Chuyển Đổi Backend

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

[Chuyển dữ liệu vanilla](/vi/docs/configuration/migration) và các bản nhập từ AxVaults / PlayerVaultsX là các tính năng riêng để kéo dữ liệu vào từ *các plugin khác*.

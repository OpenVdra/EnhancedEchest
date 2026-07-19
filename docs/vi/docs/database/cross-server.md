# Hỗ Trợ Liên Máy Chủ (Cross-Server)

Nhiều máy chủ đứng sau proxy (Velocity, BungeeCord) có thể dùng chung một cơ sở dữ liệu, để rương Ender của người chơi đi theo họ giữa các máy chủ. Tính năng này mặc định tắt. Bật bằng `enabled` trong mục `cross-server` của `config.yml`.

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

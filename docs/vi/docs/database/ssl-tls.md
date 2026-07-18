# SSL / TLS

Tùy chọn `ssl` điều khiển mã hóa đường truyền cho cơ sở dữ liệu MySQL, MariaDB hoặc PostgreSQL từ xa. Nó không ảnh hưởng đến SQLite. Thay đổi giá trị này cần khởi động lại máy chủ hoàn toàn.

| Giá trị | Hành vi |
| --- | --- |
| `disable` | Không mã hóa (mặc định). |
| `require` | Mã hóa kết nối nhưng **không** xác minh certificate hay hostname của máy chủ. Chặn nghe lén thụ động, nhưng không chặn man-in-the-middle chủ động. |
| `verify-full` | Mã hóa **và** xác minh chuỗi certificate cùng hostname. Chế độ duy nhất chống được man-in-the-middle. |

`verify-full` yêu cầu CA (certificate authority) của máy chủ cơ sở dữ liệu phải được JVM chạy máy chủ Minecraft tin cậy (truststore). Nếu certificate là self-signed hoặc do CA riêng cấp, hãy import nó vào truststore của JVM trước, nếu không kết nối sẽ thất bại lúc khởi động.

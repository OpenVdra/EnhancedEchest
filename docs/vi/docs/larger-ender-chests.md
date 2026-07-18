# Rương Ender Lớn Hơn

EnhancedEchest thay rương Ender vanilla 27 ô bằng một kho đồ có thể cấu hình lên tới **54 ô**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

## Cùng Khối, Nhiều Không Gian Hơn

Người chơi mở rương Ender đúng theo cách họ vẫn làm, bằng cách chuột phải vào khối rương Ender, và nhận được kho đồ lớn hơn thay vì màn hình vanilla.

- Mở bằng chuột phải hoặc qua `/ec`
- Khối rương Ender vẫn giữ hiệu ứng đóng/mở nắp
- Kích thước cấu hình theo bội số của 9, từ 9 đến 54

## Kích Thước Tùy Chỉnh

Kích thước mặc định cho rương đầu tiên của người chơi được đặt bằng `enderchest.default-size` trong `config.yml`. Quản trị viên cũng có thể đổi kích thước từng rương bằng `/ee resize`, và bạn có thể ghi đè kích thước rương cơ bản **theo từng rank** bằng quyền `enhancedechest.default_size.<size>`.

- Kích thước hợp lệ: `9`, `18`, `27`, `36`, `45`, `54`
- Giá trị không hợp lệ được làm tròn về kích thước gần nhất
- Mặc định là `54` (rương đôi đầy đủ)
- Ghi đè theo từng người chơi bằng quyền, xem trang [Rương Theo Quyền](/vi/docs/permission-chests#default-size-permission)

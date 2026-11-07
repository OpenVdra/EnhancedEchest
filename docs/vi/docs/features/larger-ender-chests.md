# Rương Ender Tùy Chỉnh

Mỗi rương có thể chứa **9, 18, 27, 36, 45 hoặc 54 ô**, thay vì cố định 27 ô như rương Ender vanilla.

<img class="feature-shot" alt="Sáu rương Ender xếp chồng, từ 54 ô xuống 9 ô" src="/screenshots/chest-sizes.webp" />

## Cảm Giác Vanilla

Người chơi vẫn dùng rương Ender vanilla như cũ. Chuột phải vào rương hoặc dùng `/ec` để mở, nắp rương vẫn đóng mở như thường.

## Kích Thước Theo Rank

Kích thước rương đầu tiên của người chơi được đặt bằng `default-size` trong `config.yml`. Mặc định là `54`.

- Kích thước hợp lệ: `9`, `18`, `27`, `36`, `45`, `54`. Giá trị khác được làm tròn về kích thước gần nhất.
- Đặt kích thước theo từng rank bằng quyền `enhancedechest.default_size.<size>`. Xem [Rương Theo Quyền](/vi/docs/access/permission-chests#default-size-permission).
- Quản trị viên có thể đổi kích thước từng rương bằng `/ee resize`.

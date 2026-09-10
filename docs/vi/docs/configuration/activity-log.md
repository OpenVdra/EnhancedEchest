# Nhật ký hoạt động

EnhancedEchest có thể ghi lại mỗi lần một rương ender được mở và đóng, kèm ảnh chụp nội dung rương ở mỗi lần. Bản ghi được xem ngay trong game bằng lệnh `/ee log <player>`.

Đây là bằng chứng để điều tra mất đồ. Nó **không** khôi phục vật phẩm. Muốn khôi phục, xem [Sao lưu](/vi/docs/configuration/#backup).

Mặc định tính năng này tắt. Bật bằng thiết lập `enabled` trong mục `activity-log` của `config.yml`, rồi chạy `/ee reload`.

Nhật ký được lưu trong tệp riêng, `plugins/EnhancedEchest/log.db`, tách khỏi dữ liệu rương chính. Đây là một cơ sở dữ liệu, không phải tệp văn bản, nên được đọc qua trình xem trong game chứ không mở bằng trình soạn thảo.

## Xem nhật ký của một người chơi

Chạy `/ee log <player>` để mở trình xem cho các rương ender của người chơi đó. Cần quyền `enhancedechest.admin.log`.

Mỗi lần mở và đóng là một ô, mới nhất trước:

- Ô **xanh lá** là một lần mở. Ô **đỏ** là một lần đóng.
- Tên ô là hành động và thời điểm xảy ra.
- Phần mô tả cho biết ai đã làm và số thứ tự rương, và ở một lần đóng nó liệt kê thay đổi: dòng `+` xanh cho mỗi vật phẩm được bỏ vào rương, dòng `-` đỏ cho mỗi vật phẩm được lấy ra.
- Hàng dưới cùng dùng để lật sang các mục cũ hơn và mới hơn.

## Xem rương tại một thời điểm

Nhấn vào một ô bất kỳ để xem chính xác nội dung rương ở thời điểm đó. Có thể nhặt và di chuyển vật phẩm trong bản xem này để kiểm tra, nhưng không thể lấy ra khỏi nó, và đóng lại không thay đổi gì. Bản ghi đã lưu không bao giờ bị sửa đổi.

## Khi ai đó mở rương của người khác

Khi một quản trị viên mở một rương không phải của mình, mục ghi lại quản trị viên là người thực hiện, nên việc truy cập của quản trị viên xuất hiện trong nhật ký của chủ rương cùng với các lần chủ rương tự mở.

## Những lần không thay đổi gì

Đa số người chơi mở rương, nhìn một lượt rồi đóng lại. Với những lần đó chỉ lần mở được giữ, nên nhật ký đủ ngắn để đọc. Một rương chỉ bị sắp xếp lại vật phẩm cũng tính là không đổi: không được thêm cũng không mất gì.

Để giữ thêm mục đóng cho những lần không thay đổi gì, đặt `log-unchanged` thành `true` trong mục `activity-log` của `config.yml`.

## Giữ bao nhiêu

Các mục cũ được tự động dọn để nhật ký không phình mãi:

- Mục cũ hơn `retention-days` ngày sẽ bị xoá.
- Mỗi người chơi giữ tối đa `max-entries-per-player` mục, nên một người chơi rất tích cực, hay một bot, không thể làm đầy nhật ký.
- `prune-interval` đặt tần suất chạy việc dọn dẹp này.

::: tip Thay đổi thiết lập
`enabled`, `log-unchanged`, `retention-days`, `max-entries-per-player` và `prune-interval` áp dụng khi chạy `/ee reload`. `queue-capacity` chỉ được đọc một lần khi máy chủ khởi động, nên đổi nó cần khởi động lại toàn bộ.
:::

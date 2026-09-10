# Nhật ký hoạt động

EnhancedEchest có thể ghi lại mỗi lần một rương ender được mở và đóng, kèm ảnh chụp nội dung rương ở mỗi lần. Bản ghi được xem ngay trong game bằng lệnh `/ee log <player>`.

Đây là bằng chứng để điều tra mất đồ. Nó **không** khôi phục vật phẩm. Muốn khôi phục, xem [Sao lưu](/vi/docs/configuration/#backup).

Mặc định tính năng này tắt. Bật bằng thiết lập `enabled` trong mục `activity-log` của `config.yml`, rồi chạy `/ee reload`.

Nhật ký được lưu trong tệp riêng, `plugins/EnhancedEchest/log.db`, tách khỏi dữ liệu rương chính. Đây là một cơ sở dữ liệu, không phải tệp văn bản, nên được đọc qua trình xem trong game chứ không mở bằng trình soạn thảo.

## Xem nhật ký của một người chơi

Chạy `/ee log <player>` để mở trình xem cho các rương ender của người chơi đó. Cần quyền `enhancedechest.admin.log`.

Mỗi lượt là một ô, một rương ender, mới nhất trước:

- Tên ô là ai đã mở rương và đóng lúc nào.
- Phần mô tả cho biết rương mở trong bao lâu, số thứ tự rương, và những gì đã thay đổi khi mở: dòng `+` cho mỗi vật phẩm được bỏ vào rương, dòng `-` cho mỗi vật phẩm được lấy ra, cùng trên một mô tả.
- Hàng dưới cùng dùng để lật sang các lượt cũ hơn và mới hơn.

## Xem rương tại một thời điểm

Nhấn vào một ô bất kỳ để xem chính xác nội dung rương khi lượt đó kết thúc. Có thể nhặt và di chuyển vật phẩm trong bản xem này để kiểm tra, nhưng không thể lấy ra khỏi nó, và đóng lại không thay đổi gì. Bản ghi đã lưu không bao giờ bị sửa đổi. Bấm Esc hoặc E sẽ quay về đúng trang nhật ký lúc nãy.

## Khi ai đó mở rương của người khác

Khi một quản trị viên mở một rương không phải của mình, mục ghi lại quản trị viên là người thực hiện, nên việc truy cập của quản trị viên xuất hiện trong nhật ký của chủ rương cùng với các lần chủ rương tự mở.

## Những lần không thay đổi gì

Đa số người chơi mở rương, nhìn một lượt rồi đóng lại. Những lượt không lấy ra cũng không bỏ vào thứ gì sẽ không được ghi, nên nhật ký chỉ chứa các lượt thực sự có đồ di chuyển. Một rương chỉ bị sắp xếp lại vật phẩm cũng tính là không đổi: không được thêm cũng không mất gì.

## Giữ bao nhiêu

Các mục cũ được tự động dọn để nhật ký không phình mãi:

- Mục cũ hơn `retention-days` ngày sẽ bị xoá.
- Mỗi người chơi giữ tối đa `max-entries-per-player` mục, nên một người chơi rất tích cực, hay một bot, không thể làm đầy nhật ký.
- `prune-interval` đặt tần suất chạy việc dọn dẹp này.

::: tip Thay đổi thiết lập
`enabled`, `retention-days`, `max-entries-per-player` và `prune-interval` áp dụng khi chạy `/ee reload`. `queue-capacity` chỉ được đọc một lần khi máy chủ khởi động, nên đổi nó cần khởi động lại toàn bộ.
:::

# Nhật Ký Hoạt Động

EnhancedEchest ghi lại ai đã mở rương Ender nào và bỏ vào hay lấy ra thứ gì. Kết quả là một file văn bản thường, mở được bằng mọi trình soạn thảo, nằm tại `plugins/EnhancedEchest/logs/echest-latest.log`.

Đây là bằng chứng để điều tra khi có người mất đồ. Nó **không** khôi phục lại đồ. Muốn khôi phục, xem [Sao lưu](/vi/docs/configuration/#backup).

Tính năng mặc định tắt. Bật bằng thiết lập `enabled` trong nhóm `activity-log` của `config.yml`, rồi chạy `/ee reload`.

## Đọc Một Mục Nhật Ký

Mỗi lượt mở có thay đổi tạo ra một mục: lúc mở rương, những gì được bỏ vào và lấy ra trong lúc mở, và lúc đóng rương.

```
[2026-07-28 15:10:23.913 ICT] OPEN player=Steve uuid=925c51aa-... chest=2 size=54
  ADD   minecraft:redstone x24
  TAKE  minecraft:stone x32
[2026-07-28 15:12:41.002 ICT] CLOSE player=Steve uuid=925c51aa-... chest=2 size=54
```

- `ADD` liệt kê mọi thứ được bỏ vào trong lượt đó, `TAKE` là mọi thứ bị lấy ra. Vật phẩm giống nhau được cộng dồn, nên lấy đá từ năm ô khác nhau vẫn chỉ ra một dòng.
- Đồ có tên riêng, có phù phép hoặc dữ liệu tùy biến khác được ghi đầy đủ trên dòng của nó.
- `chest=2` là số rương của người chơi, đúng số họ thấy trong `/eclist`. `size=54` là số ô của rương đó.
- Vị trí đồ trong rương không được ghi lại. Nhật ký cho biết thứ gì đã dịch chuyển, không cho biết nó nằm ở ô nào.

### Khi Ai Đó Mở Rương Của Người Khác

Khi quản trị viên mở rương không phải của mình, cả hai dòng đều được đánh dấu và ghi rõ chủ rương:

```
[2026-07-28 15:23:25.085 ICT] OPEN player=Notch uuid=... chest=1 size=54 access=ADMIN_ACCESS owner=5ef5f7b2-...
```

## Những Lượt Mở Không Thay Đổi Gì

Phần lớn người chơi chỉ mở rương ra nhìn rồi đóng lại. Những lượt đó không được ghi, nhờ vậy nhật ký đủ ngắn để thật sự đọc được. Rương chỉ bị sắp xếp lại cũng tính là không thay đổi, vì không mất cũng không thêm gì.

Muốn ghi lại mọi lượt mở, đặt `log-unchanged` thành `true` trong nhóm `activity-log` của `config.yml`.

## File Nhật Ký Và Dung Lượng Đĩa

Khi `echest-latest.log` vượt quá kích thước đặt ở `max-file-size-mb`, nó được đổi tên và một file mới bắt đầu. File vừa đổi tên sau đó được nén lại, còn khoảng một phần năm mươi, nên nhật ký cũ tốn rất ít chỗ.

| File | Là gì |
|------|-------|
| `echest-latest.log` | File đang được ghi. Luôn mang tên này, không bao giờ bị xóa. |
| `echest-20260728-151023-913.log.gz` | File cũ đã nén. Phần tên là ngày giờ file đó được đóng lại. |
| `echest-20260728-151023-913.log` | File cũ chưa kịp nén. |

Vì phần ngày giờ chạy từ năm xuống tới mili giây, sắp xếp thư mục theo tên cũng chính là sắp theo thời gian. Giờ hiển thị là giờ địa phương của máy chủ.

File đã nén cũ hơn `retention-days` sẽ tự động bị xóa. File đang được ghi không bao giờ bị xóa.

Để đọc file đã nén, mở bằng 7-Zip, WinRAR hoặc bất kỳ công cụ nào hỗ trợ `.gz`.

::: tip Đổi các thiết lập file
`enabled` và `log-unchanged` có hiệu lực khi chạy `/ee reload`. Ba thiết lập còn lại chỉ được đọc lúc server khởi động, nên đổi chúng thì phải khởi động lại toàn bộ server.
:::

## Chưa Hỗ Trợ

**Xem đồ bên trong shulker box.** Shulker box được ghi như một vật phẩm đơn lẻ, giống mọi món khác. Nhật ký cho thấy có một cái được bỏ vào hay lấy ra khỏi rương, nhưng không cho biết bên trong đựng gì, nên đồ bị chuyển đi bằng cách nhồi vào shulker sẽ không được liệt kê từng món.

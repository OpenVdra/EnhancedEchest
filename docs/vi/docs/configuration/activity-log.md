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

### Toàn Bộ Đồ Trong Rương

Mỗi header đều kèm một dòng `HAVE` liệt kê rương đang có gì tại thời điểm đó:

```
[2026-08-03 15:10:23.913 ICT] OPEN player=Steve uuid=925c51aa-... chest=2 size=54
  HAVE  minecraft:stone x128, minecraft:redstone x8, minecraft:oak_log x64
  ADD   minecraft:redstone x24
  TAKE  minecraft:stone x32
[2026-08-03 15:12:41.002 ICT] CLOSE player=Steve uuid=925c51aa-... chest=2 size=54
  HAVE  minecraft:stone x96, minecraft:redstone x32, minecraft:oak_log x64
```

Đồ được liệt kê theo đúng thứ tự nằm trong rương, nên có thể đối chiếu dòng này với ảnh chụp màn hình hoặc với `/ee view`. Vật phẩm giống nhau vẫn cộng dồn thành một mục, đặt ở vị trí của cái đầu tiên. Rương không có gì sẽ ghi `HAVE  (empty)`.

Những dòng này làm mỗi mục nhật ký phình lên vài lần, và phình thêm nữa khi liệt kê cả nội dung shulker, nên file nhật ký chạm giới hạn kích thước sớm hơn nhiều. Muốn bỏ chúng đi, đặt `chest-contents` thành `false` trong nhóm `activity-log` của `config.yml`.

### Shulker Box

Shulker box vẫn tính là một vật phẩm, nhưng thứ nó đang đựng được liệt kê ngay sau đó, nên đồ mang ra mang vào bên trong shulker vẫn nhìn thấy được.

```
[2026-08-03 09:41:12.507 ICT] OPEN player=Steve uuid=925c51aa-... chest=1 size=27
  TAKE  minecraft:shulker_box{meta=8f31c2,contents=[minecraft:diamond x192, minecraft:netherite_ingot x7]} x1
[2026-08-03 09:41:58.140 ICT] CLOSE player=Steve uuid=925c51aa-... chest=1 size=27
```

Đồ bên trong cũng được cộng dồn như vậy, nên ba chồng kim cương chỉ ra một mục. Chỉ liệt kê một lớp: món đồ nằm trong shulker được ghi bằng tên của chính nó, không mở tiếp xem nó đựng gì.

Sắp xếp lại shulker khi nó đang nằm trong rương làm nó khác đi, nên nhật ký ghi thành lấy cái cũ ra và bỏ cái mới vào. So hai danh sách `contents` sẽ thấy thứ gì đã dịch chuyển.

Muốn giữ shulker box chỉ là một vật phẩm không kèm danh sách, đặt `shulker-contents` thành `false` trong nhóm `activity-log` của `config.yml`.

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
`enabled`, `log-unchanged`, `shulker-contents` và `chest-contents` có hiệu lực khi chạy `/ee reload`. Ba thiết lập còn lại chỉ được đọc lúc server khởi động, nên đổi chúng thì phải khởi động lại toàn bộ server.
:::

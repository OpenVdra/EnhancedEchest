# Hệ Thống Nhiều Rương

Người chơi không còn bị giới hạn ở một rương Ender. Mỗi người chơi có thể sở hữu nhiều rương, quản lý qua menu trong game.

<figure class="feature-figure">
  <img alt="The chest list menu showing several owned ender chests" src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" />
  <figcaption>Với hai rương trở lên, mở rương Ender sẽ bật lên menu liệt kê mọi rương bạn sở hữu, kèm số ô của từng rương.</figcaption>
</figure>

## Menu Danh Sách Rương

Chạy `/eclist` để mở menu liệt kê mọi rương người chơi sở hữu, kèm số ô của từng rương. Ô tích **Chế độ chỉnh sửa** thay đổi điều xảy ra khi bấm vào rương: khi tắt (mặc định) rương mở ngay; khi bật, bấm vào rương sẽ mở màn hình quản lý để đổi tên, gán biểu tượng, hoặc đặt làm rương chính. Ô tích chuyển trạng thái tại chỗ, không mở lại menu.

## Rương Chính

Với nhiều rương, người chơi có thể chọn một rương làm **rương chính**, rương được mở trực tiếp bằng `/ec` và bằng chuột phải vào rương Ender. Cho đến khi chọn rương chính, những thao tác đó sẽ mở menu quản lý. Rương mới không bao giờ tự động trở thành rương chính; người chơi tự đặt từ menu (và luôn vào menu được bằng `/eclist`).

Một ngoại lệ: khi người chơi đang có **rương tạm** chứa vật phẩm bị dồn (ví dụ sau khi rương bị thu nhỏ hoặc rương cấp theo rank bị thu hồi), mở rương Ender sẽ luôn hiện menu, kể cả khi đã đặt rương chính. Điều này bảo đảm người chơi thấy được số vật phẩm bị dồn. Khi lấy hết đồ, rương tạm tự biến mất và rương chính lại mở trực tiếp như thường.

## Tùy Chỉnh Từng Rương

Cá nhân hóa rương ngay từ menu trong game, không cần lệnh:

- **Đổi tên**: đặt tên hiển thị cho rương, có thể tô màu
- **Chọn biểu tượng**: chọn vật phẩm bất kỳ để hiện trong danh sách
- **Sắp xếp**: gộp cụm và xếp lại theo loại chỉ với một cú bấm

Có thể tắt từng mục cho toàn máy chủ trong phần `features` thuộc mục `enderchest` của `config.yml`. Xem [Cấu hình](/vi/docs/configuration/).

<div class="placeholder-row">
  <figure>
    <img width="1162" height="1067" alt="A chest's management menu with rename, icon, and set-as-main options" src="https://github.com/user-attachments/assets/76bc97fa-1dcb-4e39-8bde-9504ebc4d768" />
    <figcaption>Màn hình quản lý của một rương: đổi tên, chọn biểu tượng, hoặc đặt làm rương chính.</figcaption>
  </figure>
  <figure>
    <img width="1013" height="1067" alt="The rename prompt for an ender chest" src="https://github.com/user-attachments/assets/573814dd-6f58-4e9c-b65a-58842e3ba2a2" />
    <figcaption>Đổi tên một rương; tên bạn nhập sẽ trở thành tiêu đề kho đồ của nó.</figcaption>
  </figure>
</div>

<figure class="feature-figure">
  <img width="1802" height="1068" alt="The searchable item picker for choosing a chest icon" src="https://github.com/user-attachments/assets/ce6b235b-980c-4403-86d3-503c25f32d77" />
  <figcaption>Chọn bất kỳ vật phẩm nào làm biểu tượng cho rương với bộ chọn vật phẩm có tìm kiếm.</figcaption>
</figure>

## Quản Lý Bởi Quản Trị Viên

Quản trị viên có thể thêm, đổi kích thước và xóa rương cho bất kỳ người chơi nào bằng `/ee add`, `/ee resize` và `/ee delete`. Xóa rương chính khiến người chơi không có rương chính cho đến khi họ chọn rương mới từ menu.

## Rương Cấp Theo Quyền

Phát rương theo rank thay vì bằng lệnh. Quyền `enhancedechest.additional_amount.<count>.slot.<size>` cấp ngần ấy rương với kích thước đó. Các node cộng dồn, việc cấp đồng bộ khi mở rương, và xóa một node sẽ xóa các rương đó, vật phẩm được dồn sang một rương tạm để người chơi lấy lại. Rương cơ bản của người chơi luôn được bảo vệ. Xem trang [Rương Theo Quyền](/vi/docs/access/permission-chests#permission-granted-chests).

## Xem Rương Của Người Chơi Khác

Với `/ee view <player>` quản trị viên mở rương của một người chơi, dù trực tuyến hay ngoại tuyến, ngay trong menu quản lý mà chủ rương thấy. Một rương mở thẳng menu của nó; với nhiều rương, menu lựa chọn sẽ hiện ra. Cấp `admin.view` để xem chỉ-đọc, thêm `admin.edit` để lấy/thêm vật phẩm và để đổi tên, đổi biểu tượng, hoặc sắp xếp rương, và thêm `admin.clear` để có nút **Dọn rương** (kèm xác nhận) làm trống rương.

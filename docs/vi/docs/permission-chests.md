# Rương Theo Quyền

Hai nhóm quyền phát đặc quyền rương Ender theo rank, không cần lệnh và không cần cấu hình gì ngoài một công tắc duy nhất. Cả hai đều đồng bộ ở lần mở rương Ender tiếp theo của người chơi, nên không cần đăng nhập lại.

## Kích Thước Rương Cơ Bản Theo Quyền {#default-size-permission}

Mỗi người chơi có một rương Ender **cơ bản** (rương đầu tiên của họ, số `#1`). Kích thước của nó bình thường là `enderchest.default-size` toàn cục. Quyền `enhancedechest.default_size.<size>` ghi đè kích thước đó **theo từng người chơi**, để một rank có thể nhận rương khởi đầu lớn hơn (hoặc nhỏ hơn) mà không cần dùng lệnh nào.

- **`<size>`**: số ô, bội số của 9 từ 9 đến 54.
- **Lớn nhất thắng**: nếu người chơi vô tình giữ cả `...27` và `...54`, họ sẽ nhận 54. (Khác với các rương *bổ sung*, vốn cộng dồn, vì người chơi chỉ có một rương cơ bản, nên chỉ có một kích thước để chọn.)
- **Cấp thì tăng, thu hồi thì giảm**: nhận quyền sẽ đổi kích thước rương cơ bản theo đó. Tăng kích thước giữ nguyên mọi vật phẩm. Giảm kích thước (quyền nhỏ hơn, hoặc mất quyền hoàn toàn) sẽ dồn phần dư sang một rương tạm khôi phục được, giống hệt quyền rương bổ sung. Mất quyền sẽ đưa rương cơ bản về lại `enderchest.default-size`.
- **Được quyền quản lý khi đang áp dụng**: khi một quyền `default_size` đang áp dụng cho người chơi, rương cơ bản của họ hoạt động như một rương được cấp theo quyền đối với quản trị viên. `/ee resize` từ chối thay đổi nó, vì kích thước thuộc quyền sở hữu của quyền đó. Điều này đúng cả với người chơi ngoại tuyến.
- **Luôn khả dụng**: đây là tính năng thuần theo quyền, không có công tắc cấu hình, chỉ cần cấp (hoặc không cấp) node.
- Đồng bộ ở lần mở rương tiếp theo của người chơi, không cần đăng nhập lại.

::: tip Tương tác với rương bổ sung
Node này định kích thước rương **cơ bản**; `additional_amount` cấp các rương **bổ sung**. Chúng độc lập với nhau: một người chơi có thể có rương cơ bản 54 ô từ `default_size.54` *và* thêm hai rương nữa từ `additional_amount.2.slot.54`.
:::

## Rương Cấp Theo Quyền {#permission-granted-chests}

Dùng `enhancedechest.additional_amount.<count>.slot.<size>` để gắn đặc quyền rương vào rank mà không cần dùng lệnh.

- **`<count>`**: số rương cần cấp.
- **`<size>`**: số ô, bội số của 9 từ 9 đến 54.
- **Node cộng dồn**: cấp `...1.slot.9` và `...2.slot.9` sẽ cho người chơi tổng cộng ba rương 9 ô.
- **Thu hồi sạch sẽ**: hạ rank thì xóa đúng các rương đó; vật phẩm dồn sang rương tạm khôi phục được từ `/eclist`.
- **Rương cơ bản luôn được bảo vệ**: người chơi luôn giữ ít nhất một rương thường.
- Việc cấp đồng bộ ở lần mở rương tiếp theo, không cần đăng nhập lại.

::: warning
Việc cấp theo quyền chỉ hoạt động khi `permission-chests.enabled: true` trong `config.yml`. Tắt nó dừng đồng bộ nhưng giữ nguyên các rương đã cấp.
:::

## Rương theo quyền so với rương `/ee add`

Một người chơi có thể nhận thêm rương theo hai cách, và chúng hoạt động khác nhau:

- **Rương theo quyền** (`additional_amount`) đến và đi theo rank của người chơi. Chúng xuất hiện ngay khi quyền được áp dụng và bị xóa ngay khi mất quyền (đổi rank, hoặc chỉnh sửa node); rương bị xóa sẽ dồn vật phẩm sang một rương tạm khôi phục được. Quản trị viên không thể đổi kích thước hay xóa chúng bằng `/ee resize` hoặc `/ee delete`, vì quyền sở hữu chúng.
- **Rương lệnh** (`/ee add`) là vĩnh viễn. Quản trị viên cấp và chúng ở lại cho đến khi quản trị viên đổi kích thước (`/ee resize`) hoặc xóa (`/ee delete`). Đổi rank không bao giờ ảnh hưởng đến chúng.

Với người chơi, cả hai trông và hoạt động y hệt nhau trong menu `/eclist`: mở, đổi tên, chọn biểu tượng, sắp xếp, và đặt làm rương chính. Khác biệt thực sự duy nhất là ai kiểm soát chúng, nên rương theo quyền có thể biến mất khi rank thay đổi còn rương lệnh thì không. Vì rương theo quyền bị xóa sẽ mất luôn, nên tên và biểu tượng tùy chỉnh của nó cũng mất theo; rương lệnh giữ chúng cho đến khi quản trị viên xóa.

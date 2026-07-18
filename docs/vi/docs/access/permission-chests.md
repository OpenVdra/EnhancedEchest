# Rương Theo Quyền

Rương của người chơi có hai loại: một rương **cơ bản** duy nhất (`#1`, tự tạo) và các rương **bổ sung** ngoài nó. Hai nhóm quyền độc lập điều khiển kích thước và cấp phát chúng theo rank, không cần lệnh và không cần cấu hình gì ngoài một công tắc duy nhất. Cả hai đều đồng bộ ở lần mở rương Ender tiếp theo của người chơi, không cần đăng nhập lại.

## Kích Thước Rương Cơ Bản Theo Quyền {#default-size-permission}

Rương cơ bản của mỗi người chơi bình thường có kích thước theo `enderchest.default-size` toàn cục. Quyền `enhancedechest.default_size.<size>` ghi đè kích thước đó **theo từng người chơi**, để một rank có thể nhận rương khởi đầu lớn hơn (hoặc nhỏ hơn) mà không cần dùng lệnh nào.

- **`<size>`**: số ô, bội số của 9 từ 9 đến 54.
- **Lớn nhất thắng**: người chơi giữ cả `...27` và `...54` sẽ nhận 54, vì chỉ có một rương cơ bản và một kích thước để chọn.
- **Cấp thì tăng, thu hồi thì giảm**: nhận quyền sẽ đổi kích thước rương cơ bản theo đó, giữ nguyên mọi vật phẩm. Giảm kích thước (quyền nhỏ hơn, hoặc mất quyền hoàn toàn) sẽ dồn phần dư sang một rương tạm khôi phục được. Mất quyền sẽ đưa rương cơ bản về lại `enderchest.default-size`.
- **Được quyền quản lý khi đang áp dụng**: `/ee resize` từ chối thay đổi rương cơ bản khi một quyền `default_size` đang sở hữu kích thước của nó, kể cả với người chơi ngoại tuyến.

::: tip Tương tác với rương bổ sung
Node này định kích thước rương **cơ bản**; `additional_amount` cấp các rương **bổ sung**. Chúng độc lập với nhau: một người chơi có thể có rương cơ bản 54 ô từ `default_size.54` *và* thêm hai rương nữa từ `additional_amount.2.slot.54`.
:::

## Rương Cấp Theo Quyền {#permission-granted-chests}

Dùng `enhancedechest.additional_amount.<count>.slot.<size>` để cấp rương **bổ sung** theo rank, ngoài rương cơ bản.

- **`<count>`**: số rương cần cấp. **`<size>`**: số ô, bội số của 9 từ 9 đến 54.
- **Node cộng dồn**: cấp `...1.slot.9` và `...2.slot.9` sẽ cho người chơi tổng cộng ba rương 9 ô.
- **Thu hồi sạch sẽ**: mất một node sẽ xóa đúng các rương đó, dồn vật phẩm sang rương tạm khôi phục được. Rương cơ bản không bao giờ bị ảnh hưởng.

::: warning
Việc cấp theo quyền chỉ hoạt động khi `permission-chests.enabled: true` trong `config.yml`. Tắt nó dừng đồng bộ nhưng giữ nguyên các rương đã cấp.
:::

## Rương Theo Quyền So Với Rương `/ee add`

Một người chơi có thể nhận thêm rương theo hai cách:

- **Rương theo quyền** (`additional_amount`) đến và đi theo rank của người chơi: xuất hiện ngay khi quyền được áp dụng và bị xóa ngay khi mất quyền, dồn vật phẩm sang rương tạm khôi phục được. Quản trị viên không thể đổi kích thước hay xóa chúng, vì quyền sở hữu chúng.
- **Rương lệnh** (`/ee add`) là vĩnh viễn. Chúng ở lại cho đến khi quản trị viên đổi kích thước (`/ee resize`) hoặc xóa (`/ee delete`); đổi rank không bao giờ ảnh hưởng đến chúng.

Với người chơi, cả hai hoạt động y hệt nhau trong `/eclist`: mở, đổi tên, chọn biểu tượng, sắp xếp, đặt làm rương chính. Rương theo quyền bị xóa sẽ mất luôn tên và biểu tượng tùy chỉnh; rương lệnh giữ chúng cho đến khi quản trị viên xóa.

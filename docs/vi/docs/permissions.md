# Quyền

Mọi node quyền mặc định là `op`. Hãy cấp chúng qua plugin quyền của bạn (LuckPerms, v.v.) để mở cho các rank khác.

## Người Chơi

**`enhancedechest.command.open`**
Cho phép dùng `/ec` và `/eclist` bằng lệnh, và đặt rương chính. Chuột phải vào khối rương Ender không bao giờ cần quyền này.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Cấp thêm rương theo rank. Ví dụ, `enhancedechest.additional_amount.2.slot.54` cho người chơi hai rương 54 ô. Nhiều node cộng dồn. Xóa node sẽ xóa các rương đó; vật phẩm dồn sang rương tạm có thể khôi phục từ `/eclist`.

Xem [Rương Cấp Theo Quyền](#permission-granted-chests) bên dưới để biết chi tiết.

**`enhancedechest.default_size.<size>`**
Ghi đè kích thước rương Ender **cơ bản** (rương đầu tiên) của người chơi theo rank, độc lập với `enderchest.default-size` toàn cục. Ví dụ, `enhancedechest.default_size.54` cho rương cơ bản của người chơi đó 54 ô. Nếu người chơi giữ nhiều node, node có kích thước **lớn nhất** sẽ thắng.

Xem [Kích Thước Rương Cơ Bản Theo Quyền](#default-size-permission) bên dưới để biết chi tiết.

## Quản Trị Viên

Mỗi lệnh `/ee` chỉ cần đúng node riêng của lệnh đó. Không còn node cơ sở chung:

**`enhancedechest.admin.add`** - `/ee add`: cấp cho người chơi một rương mới.

**`enhancedechest.admin.resize`** - `/ee resize`: thay đổi số ô của một rương. Từ chối trên rương được cấp theo quyền, và trên rương cơ bản có kích thước do quyền [`default_size`](#default-size-permission) đặt.

**`enhancedechest.admin.delete`** - `/ee delete`: xóa các rương mới nhất của người chơi.

**`enhancedechest.admin.view`** - `/ee view`: mở rương của người chơi khác (chỉ-đọc).

**`enhancedechest.admin.edit`** - kết hợp với `admin.view`, cho phép di chuyển vật phẩm.

**`enhancedechest.admin.clear`** - hiện nút đỏ **(Admin) Dọn rương** trong menu `/ee view` và cho phép làm trống rương bằng nút đó.

**`enhancedechest.admin.transfer`** - `/ee transfer`: chuyển rương của một người chơi sang tài khoản khác.

**`enhancedechest.admin.reload`** - `/ee reload`: tải lại file cấu hình và ngôn ngữ.

**`enhancedechest.admin.migrate`** - `/ee migrate vanilla`, `/ee migrate axvaults` và `/ee migrate playervaultsx`: nhập dữ liệu từ rương Ender vanilla, plugin AxVaults hoặc plugin PlayerVaultsX.

::: tip
Để cấp toàn quyền quản trị một lần, cấp `enhancedechest.admin.*` (nếu plugin quyền của bạn hỗ trợ wildcard).
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

# Quyền

Mọi node quyền mặc định là `op`. Hãy cấp chúng qua plugin quyền của bạn (LuckPerms, v.v.) để mở cho các rank khác.

## Người Chơi

**`enhancedechest.command.open`**
Cho phép dùng `/ec` và `/eclist` bằng lệnh, và đặt rương chính. Chuột phải vào khối rương Ender không bao giờ cần quyền này.

**`enhancedechest.additional_amount.<count>.slot.<size>`**
Cấp thêm rương theo rank. Ví dụ, `enhancedechest.additional_amount.2.slot.54` cho người chơi hai rương 54 ô. Nhiều node cộng dồn. Xóa node sẽ xóa các rương đó; vật phẩm dồn sang rương tạm có thể khôi phục từ `/eclist`.

Xem [Rương Cấp Theo Quyền](/vi/docs/permission-chests#permission-granted-chests) để biết chi tiết.

**`enhancedechest.default_size.<size>`**
Ghi đè kích thước rương Ender **cơ bản** (rương đầu tiên) của người chơi theo rank, độc lập với `enderchest.default-size` toàn cục. Ví dụ, `enhancedechest.default_size.54` cho rương cơ bản của người chơi đó 54 ô. Nếu người chơi giữ nhiều node, node có kích thước **lớn nhất** sẽ thắng.

Xem [Kích Thước Rương Cơ Bản Theo Quyền](/vi/docs/permission-chests#default-size-permission) để biết chi tiết.

## Quản Trị Viên

Mỗi lệnh `/ee` chỉ cần đúng node riêng của lệnh đó. Không còn node cơ sở chung:

**`enhancedechest.admin.add`** - `/ee add`: cấp cho người chơi một rương mới.

**`enhancedechest.admin.resize`** - `/ee resize`: thay đổi số ô của một rương. Từ chối trên rương được cấp theo quyền, và trên rương cơ bản có kích thước do quyền [`default_size`](/vi/docs/permission-chests#default-size-permission) đặt.

**`enhancedechest.admin.delete`** - `/ee delete`: xóa các rương mới nhất của người chơi.

**`enhancedechest.admin.view`** - `/ee view`: mở rương của người chơi khác (chỉ-đọc).

**`enhancedechest.admin.edit`** - kết hợp với `admin.view`, cho phép di chuyển vật phẩm.

**`enhancedechest.admin.clear`** - hiện nút đỏ **(Admin) Dọn rương** trong menu `/ee view` và cho phép làm trống rương bằng nút đó.

**`enhancedechest.admin.transfer`** - `/ee transfer`: chuyển rương của một người chơi sang tài khoản khác.

**`enhancedechest.admin.reload`** - `/ee reload`: tải lại file cấu hình và ngôn ngữ.

**`enhancedechest.admin.migrate`** - `/ee migrate vanilla`, `/ee migrate axvaults` và `/ee migrate playervaultsx`: nhập dữ liệu từ rương Ender vanilla, plugin AxVaults hoặc plugin PlayerVaultsX.

**`enhancedechest.admin.import`** - `/ee import`: sao chép toàn bộ dữ liệu từ một backend cơ sở dữ liệu cũ vào backend đang dùng.

::: tip
Để cấp toàn quyền quản trị một lần, cấp `enhancedechest.admin.*` (nếu plugin quyền của bạn hỗ trợ wildcard).
:::

Hai quyền cấp rương theo rank (`default_size` và `additional_amount`) có trang riêng: xem [Rương Theo Quyền](/vi/docs/permission-chests).

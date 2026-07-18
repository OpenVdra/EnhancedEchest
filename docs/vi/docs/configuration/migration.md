# Chuyển Dữ Liệu

EnhancedEchest có thể nhập dữ liệu rương Ender sẵn có từ bốn nguồn: rương Ender **vanilla**, **AxVaults**, **PlayerVaultsX** và **CustomEnderChest**. Mọi lệnh `/ee migrate` đều cần quyền `enhancedechest.admin.migrate`, chạy nền và báo số người chơi đã nhập khi hoàn tất, và luôn an toàn khi chạy lại: không bao giờ ghi đè lên rương đã có vật phẩm, chỉ đơn giản là bỏ qua.

## Từ Rương Ender Vanilla

Nếu người chơi của bạn đã có vật phẩm trong rương Ender vanilla, EnhancedEchest nhập dữ liệu đó vào **rương #1** của họ. Mỗi người chơi chỉ được chuyển đúng một lần và bị bỏ qua trong các lần vào sau.

### Tự Động Chuyển Khi Vào

Bật trong `config.yml` để nhập rương Ender vanilla của mọi người chơi chưa được chuyển ngay khoảnh khắc họ vào:

```yaml
migration:
  enabled: true
```

Sau khi mọi người đã đăng nhập, bạn có thể tắt lại.

### Chuyển Thủ Công

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate vanilla <player>` | Chuyển một người chơi đang trực tuyến |
| `/ee migrate vanilla all` | Chuyển mọi người chơi hiện đang trực tuyến |

::: warning Chỉ người chơi trực tuyến
Việc chuyển đọc rương Ender vanilla trực tiếp của người chơi, nên chỉ hoạt động với người **đang trực tuyến**. Người chơi ngoại tuyến sẽ được chuyển tự động ở lần vào tiếp theo nếu `migration.enabled` là `true`.
:::

### Purpur (và các bản fork của Paper) {#purpur}

Rương Ender mở rộng của Purpur (`ender-chest.six-rows` / `purpur.enderchest.rows.<n>`) được hỗ trợ mà không cần cấu hình thêm, lấy được toàn bộ số hàng người chơi đã có, chứ không chỉ 27 ô đầu.

## Từ AxVaults {#axvaults}

Nhập kho từ [AxVaults](https://modrinth.com/plugin/axvaults), bao gồm mọi vật phẩm cùng tên tùy chỉnh, lore và phù phép. Đã kiểm thử với **AxVaults 2.15.0**. Kho #1 thành rương #1, kho #2 thành rương #2, v.v., mỗi rương có kích thước đủ chứa hết vật phẩm.

### Trước Khi Bắt Đầu

- **Lưu AxVaults trước.** Hãy chạy `/vaultadmin save` một lần để mọi kho đang mở được ghi xuống đĩa trước khi chuyển.
- **AxVaults phải dùng SQLite.** EnhancedEchest đọc thẳng tệp `data.db` của AxVaults. Nếu AxVaults của bạn đang dùng cơ sở dữ liệu khác, hãy đặt `database.type: sqlite` trong `AxVaults/config.yml` rồi khởi động lại server nguồn để nó tạo `data.db`.

### Cách Chạy

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate axvaults` | Nhập kho cho mọi người chơi trong cơ sở dữ liệu AxVaults |
| `/ee migrate axvaults <player>` | Nhập kho cho một người chơi (trực tuyến hoặc ngoại tuyến) |

## Từ CustomEnderChest {#customenderchest}

Nhập rương Ender từ [CustomEnderChest](https://modrinth.com/plugin/custom-ender-chest), bao gồm mọi vật phẩm cùng tên tùy chỉnh, lore và phù phép, vào **rương #1** của người chơi. Đã kiểm thử với **CustomEnderChest 2.1.2**.

### Trước Khi Bắt Đầu

- **CustomEnderChest phải dùng lưu trữ YAML.** EnhancedEchest đọc các tệp theo từng người chơi trong `CustomEnderChest/playerdata/`, chỉ tồn tại khi `storage.type: yml` được đặt trong `CustomEnderChest/config.yml` (mặc định plugin dùng cơ sở dữ liệu `h2` tích hợp sẵn). Hãy chuyển sang `yml` và khởi động lại server nguồn trước; backend `mysql` cũng không được đọc.

### Cách Chạy

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate customenderchest` | Nhập rương Ender cho mọi người chơi có dữ liệu CustomEnderChest |
| `/ee migrate customenderchest <player>` | Nhập rương Ender cho một người chơi (trực tuyến hoặc ngoại tuyến) |

## Từ PlayerVaultsX {#playervaultsx}

Nhập kho từ [PlayerVaultsX](https://www.spigotmc.org/resources/playervaultsx.45741/), bao gồm mọi vật phẩm cùng tên tùy chỉnh, lore và phù phép. Đã kiểm thử với **PlayerVaultsX 4.4.13**. Kho #1 thành rương #1, kho #2 thành rương #2, v.v., mỗi rương có kích thước đủ chứa hết vật phẩm.

### Trước Khi Bắt Đầu

- **Kho được lưu khi đóng.** PlayerVaultsX ghi một kho xuống đĩa khi người chơi đóng nó, nên hãy chắc chắn không ai đang mở kho trong lúc chuyển dữ liệu (khởi động lại server nguồn, hoặc đơn giản là cho người chơi đăng xuất, sẽ ghi mọi thứ xuống đĩa).
- **Chạy trên server Paper hiện đại.** Dữ liệu kho tạo trên server từ 1.20.6+ nhập trơn tru; dữ liệu ghi từ lâu bởi server Spigot cũ có thể không giải mã được.

EnhancedEchest tìm dữ liệu trong `plugins/PlayerVaults` (tên jar thật của plugin); thư mục `plugins/PlayerVaultsX` cũng được chấp nhận làm phương án dự phòng.

### Cách Chạy

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate playervaultsx` | Nhập kho cho mọi người chơi có dữ liệu PlayerVaultsX |
| `/ee migrate playervaultsx <player>` | Nhập kho cho một người chơi (trực tuyến hoặc ngoại tuyến) |

# Ngôn Ngữ

Mọi văn bản hiển thị cho người chơi trong EnhancedEchest nằm trong các file ngôn ngữ có thể chỉnh sửa. File được tải từ:

```
plugins/EnhancedEchest/language/<locale>/
```

Plugin đi kèm `en_US` (English) và `vi_VN` (Tiếng Việt).

## Tự Động Theo Ngôn Ngữ Từng Người Chơi

Mặc định, mỗi người chơi thấy tin nhắn và menu theo **ngôn ngữ máy khách Minecraft của chính họ**, miễn là có bản dịch tương ứng (đóng gói sẵn, hoặc do bạn thêm). Người dùng game tiếng Việt thấy tiếng Việt; người dùng tiếng Anh thấy tiếng Anh, cùng lúc, trên cùng một server. Đổi ngôn ngữ trong Options của Minecraft rồi mở lại menu sẽ cập nhật, không cần thoát ra vào lại.

Điều này do hai tùy chọn trong [`config.yml`](/vi/docs/configuration) điều khiển:

```yaml
# Ngôn ngữ dự phòng cho máy khách có ngôn ngữ chưa được dịch.
language: en_US
# Tự động phát hiện ngôn ngữ máy khách của từng người chơi (mặc định). Tắt để hiện 'language' ở trên cho mọi người.
language-auto-detect: true
```

- Máy khách có ngôn ngữ **đã** được dịch → hiện ngôn ngữ đó.
- Máy khách có ngôn ngữ **chưa** được dịch → dùng `language` dự phòng ở trên.
- Với `language-auto-detect: false` → mọi người chơi thấy đúng một locale `language`, bất kể máy khách của họ.

## Các File

| File | Nội dung |
|------|----------|
| `messages.yml` | Prefix của plugin và mọi tin nhắn chat (lệnh, lỗi, phản hồi quản trị, thông báo cập nhật) |
| `gui.yml` | Tiêu đề kho đồ và các nhãn dùng trong menu quản lý `/eclist` |

## Định Dạng

Màu được viết bằng mã `&` cũ, bao gồm màu hex dạng `&#RRGGBB`. Các placeholder như `{prefix}`, `{player}`, `{index}` và `{size}` được thay thế lúc chạy.

```yaml
prefix: '&#9B59B6EɴʜᴀɴᴄᴇᴅEᴄʜᴇsᴛ &8⏩ &r'

admin:
  chest-added: '{prefix}&aAdded Ender Chest &e{index}&a (&e{size}&a slots) to &e{player}&a.'
```

Tin nhắn mặc định dùng bảng màu đơn giản:

| Màu | Dùng cho |
|-----|----------|
| `&#FF4444` | Lỗi |
| `&#F0C857` | Cảnh báo |
| `&a` | Thành công |
| `&e` / `&f` | Giá trị được làm nổi bật |
| `&7` / `&8` | Văn bản mờ |

::: tip MiniMessage cũng được hỗ trợ
Bất kỳ tin nhắn nào chứa `<` sẽ được phân tích theo [MiniMessage](https://docs.advntr.dev/minimessage/format) thay vì mã cũ.
:::

## Tiêu Đề Rương

`gui.yml` điều khiển cách hiển thị tiêu đề kho đồ của rương:

```yaml
enderchest:
  title: 'Ender Chest'
  title-numbered: 'Ender Chest {index}'
```

- Rương **#1** hiển thị `title`
- Rương **#2 trở lên** hiển thị `title-numbered` kèm số thứ tự
- Rương có **tên tùy chỉnh** (đặt qua `/eclist`) hiển thị tên đó thay thế

## Thêm Một Bản Dịch

1. Sao chép thư mục `en_US` bên trong `language/`
2. Đổi tên bản sao thành locale của bạn (ví dụ `de_DE` hoặc `fr_FR`)
3. Dịch văn bản bên trong `messages.yml` và `gui.yml`
4. Chạy `/ee reload`

Với auto-detect bật (mặc định), người chơi dùng ngôn ngữ đó sẽ tự động thấy bản dịch, không cần đổi `language`. Chỉ đặt `language: <locale-của-bạn>` nếu bạn muốn nó cũng là bản **dự phòng** cho các máy khách có ngôn ngữ bạn chưa dịch (hoặc khi bạn tắt auto-detect).

## Tên Vật Phẩm Trong Bộ Chọn Biểu Tượng {#icon-picker-item-names}

Tên vật phẩm trong bộ chọn biểu tượng rương hoạt động khác với các tin nhắn ở trên. Chúng lấy trực tiếp từ máy khách Minecraft của từng người chơi và luôn hiển thị theo ngôn ngữ mà máy khách đó đang dùng, không cần cấu hình gì ở đây.

Tìm kiếm theo tên trong bộ chọn biểu tượng hiện hoạt động tốt nhất với tiếng Anh hoặc tiếng Việt. Các ngôn ngữ máy khách khác vẫn hiển thị đúng tên vật phẩm, nhưng từ khóa tìm kiếm có thể chỉ khớp theo tên tiếng Anh.

### Thêm Tìm Kiếm Biểu Tượng Cho Ngôn Ngữ Khác

Bạn có thể thêm hỗ trợ tìm kiếm biểu tượng cho một ngôn ngữ chưa được đóng gói sẵn, hoặc ghi đè tiếng Anh/tiếng Việt, mà không cần cập nhật plugin. Trong lúc server đang chạy, tạo file tại:

```
plugins/EnhancedEchest/icons/lang/<locale>.json
```

với `<locale>` là locale Minecraft viết thường, ví dụ `de_de.json` hoặc `fr_fr.json`. Mỗi mục ánh xạ một translation key vật phẩm hoặc khối của Minecraft sang tên bạn muốn tìm kiếm khớp:

```json
{
  "item.minecraft.diamond": "Diamant",
  "block.minecraft.oak_planks": "Eichenbohlen"
}
```

Sau đó chạy `/ee reload` để áp dụng, không cần khởi động lại server. Tên vật phẩm hiển thị trong danh sách chọn vốn đã tự động khớp đúng ngôn ngữ máy khách của người chơi; file này chỉ ảnh hưởng đến việc từ khóa tìm kiếm khớp với gì.

### Lấy Dữ Liệu Từ Mojang

Mojang cung cấp miễn phí tên vật phẩm chính thức, nằm trong các file JSON dành cho chương trình đọc. Đây là cách lấy chúng, dùng phiên bản `26.2` làm ví dụ (làm tương tự với phiên bản khác):

1. Mở [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), tìm (Ctrl+F) phiên bản của bạn trong dấu ngoặc kép (ví dụ `"26.2"`), rồi bấm vào link `url` bên cạnh.
2. Trên trang đó, tìm `assetIndex` và bấm vào `url` của nó. Trang này liệt kê mọi asset của game nên hãy tìm tiếp `minecraft/lang/` theo sau bởi locale của bạn (ví dụ `minecraft/lang/vi_vn.json`) và ghi lại giá trị `hash` ngay sau nó.
3. Tải file từ `https://resources.download.minecraft.net/<2 ký tự đầu của hash>/<hash đầy đủ>`. Với tiếng Việt (`vi_vn`) trên `26.2`, hash `06fd8f3fcfc2c75f874f69e720d574be140b1261` cho ra [link này](https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261).

::: tip Tiếng Anh (`en_us`) hoạt động khác
Tiếng Anh không phải file riêng trong asset index, nó nằm sẵn trong client của game. Ở trang từ bước 1, tải `client` jar bên dưới `downloads`, mở nó bằng công cụ giải nén bất kỳ (đổi tên bản sao thành đuôi `.zip` trước nếu cần), rồi lấy ra file `assets/minecraft/lang/en_us.json`.
:::

File tải về là một dòng văn bản chưa định dạng. Hãy mở nó bằng [VS Code](https://code.visualstudio.com/) hoặc Notepad++ và dùng **Format Document** để dàn nó dễ đọc. File có hàng nghìn mục khác ngoài vật phẩm, nhưng không cần xóa gì cả: bộ chọn biểu tượng chỉ đọc các key bắt đầu bằng `item.minecraft.` hoặc `block.minecraft.`.

4. Lưu file hoàn chỉnh tại `plugins/EnhancedEchest/icons/lang/<locale>.json` (chữ thường, ví dụ `de_de.json`) rồi chạy `/ee reload`.

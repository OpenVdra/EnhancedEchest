# Ngôn Ngữ

Mọi văn bản hiển thị cho người chơi trong EnhancedEchest nằm trong các file ngôn ngữ có thể chỉnh sửa. File được tải từ:

```
plugins/EnhancedEchest/language/<locale>/
```

Plugin đi kèm `en_US` (English) và `vi_VN` (Tiếng Việt).

## Tự Động Theo Ngôn Ngữ Từng Người Chơi

Mặc định, mỗi người chơi thấy tin nhắn và menu theo **ngôn ngữ máy khách Minecraft của chính họ**, miễn là có bản dịch tương ứng (đóng gói sẵn, hoặc do bạn thêm). Người dùng game tiếng Việt thấy tiếng Việt; người dùng tiếng Anh thấy tiếng Anh — cùng lúc, trên cùng một server. Đổi ngôn ngữ trong Options của Minecraft rồi mở lại menu sẽ cập nhật, không cần thoát ra vào lại.

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

Với auto-detect bật (mặc định), người chơi dùng ngôn ngữ đó sẽ tự động thấy bản dịch — không cần đổi `language`. Chỉ đặt `language: <locale-của-bạn>` nếu bạn muốn nó cũng là bản **dự phòng** cho các máy khách có ngôn ngữ bạn chưa dịch (hoặc khi bạn tắt auto-detect).

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

Gõ tay tên của từng vật phẩm sẽ tốn khá nhiều công sức. Mojang cung cấp miễn phí tên chính thức, nhưng chúng nằm trong các file JSON lớn dành cho chương trình đọc, không phải cho con người đọc, nên dưới đây là hướng dẫn từng bước với các đường link thật để bạn bấm vào, dùng phiên bản `26.2` (bản mới nhất tại thời điểm viết bài) làm ví dụ. Với phiên bản khác, làm tương tự để lấy link mới.

**Bước 1: Tìm phiên bản của bạn.** Mở version manifest:

[https://piston-meta.mojang.com/mc/game/version_manifest_v2.json](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)

Hầu hết trình duyệt hiển thị JSON dưới dạng trang có thể tìm kiếm được. Nhấn Ctrl+F (Cmd+F trên Mac), tìm số phiên bản trong dấu ngoặc kép, ví dụ `"26.2"`, rồi bấm vào link `url` hiện ngay bên cạnh nó. Với `26.2`, link đó là:

[https://piston-meta.mojang.com/v1/packages/c8eb00be8a1f9fb9adf70ee415b7e1f746b636e8/26.2.json](https://piston-meta.mojang.com/v1/packages/c8eb00be8a1f9fb9adf70ee415b7e1f746b636e8/26.2.json)

**Bước 2: Tìm asset index.** Trên trang bạn vừa mở, tìm `assetIndex` và bấm vào `url` ngay bên cạnh. Với `26.2`, link đó là:

[https://piston-meta.mojang.com/v1/packages/49da57a9512de46382d2fe4b68af047fea7a16f9/32.json](https://piston-meta.mojang.com/v1/packages/49da57a9512de46382d2fe4b68af047fea7a16f9/32.json)

Trang này liệt kê mọi asset của game nên khá lớn. Ô tìm kiếm sẽ rất cần thiết ở bước này.

**Bước 3: Tìm file ngôn ngữ của bạn.** Trên trang asset index đó, tìm `minecraft/lang/` theo sau bởi locale của bạn, ví dụ `minecraft/lang/vi_vn.json`. Ngay sau nó là giá trị `hash`, một chuỗi chữ và số dài. Ghép thành link tải:

```
https://resources.download.minecraft.net/<2 ký tự đầu của hash>/<hash đầy đủ>
```

Ví dụ, tiếng Việt (`vi_vn`) trên `26.2` có hash là `06fd8f3fcfc2c75f874f69e720d574be140b1261`, nên link tải là:

[https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261](https://resources.download.minecraft.net/06/06fd8f3fcfc2c75f874f69e720d574be140b1261)

Bấm vào link đó sẽ tải về (hoặc hiển thị) chính file ngôn ngữ. Lưu nó thành `plugins/EnhancedEchest/icons/lang/vi_vn.json` (đổi thành locale của bạn) rồi chuyển thẳng xuống bước cuối bên dưới.

::: tip Tiếng Anh (`en_us`) hoạt động khác
Tiếng Anh không phải file riêng trong asset index, nó nằm sẵn trong chính client của game. Ở trang từ bước 1, tìm `client` bên dưới `downloads` rồi bấm vào `url` của nó để tải client jar (file khá lớn, vài chục megabyte). Mở nó bằng công cụ giải nén bất kỳ (7-Zip, WinRAR, hoặc "Extract All" có sẵn trong Windows, nếu Windows không nhận diện được `.jar` thì đổi tên bản sao thành đuôi `.zip` trước) rồi lấy ra file `assets/minecraft/lang/en_us.json` bên trong.
:::

**Đọc và lưu file.** File tải về là một dòng văn bản khổng lồ không có khoảng trắng nào cả, không thể đọc được trong trình soạn thảo văn bản thường như Notepad. Hãy mở nó bằng một trình soạn thảo code miễn phí như [Visual Studio Code](https://code.visualstudio.com/) hoặc Notepad++. Trong VS Code, bấm chuột phải vào bất kỳ đâu trong văn bản rồi chọn **Format Document** để dàn nó thành các dòng có thụt lề dễ đọc, sau đó dùng Ctrl+F để tìm kiếm bên trong.

File này có hàng nghìn mục cho menu, thành tựu, và nhiều thứ khác, không chỉ vật phẩm, nhưng bạn không cần xóa bớt gì cả. Bộ chọn biểu tượng chỉ đọc các key bắt đầu bằng `item.minecraft.` hoặc `block.minecraft.`, nên lưu nguyên file như đã tải về vẫn hoạt động tốt, các mục thừa đơn giản là không bao giờ được đọc tới.

**Bước 4: Lưu file.** Đặt file hoàn chỉnh tại `plugins/EnhancedEchest/icons/lang/<locale>.json` (chữ thường, khớp với locale id ở bước 3, ví dụ `de_de.json`) rồi chạy `/ee reload`.

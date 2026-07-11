# Ngôn Ngữ

Mọi văn bản hiển thị cho người chơi trong EnhancedEchest nằm trong các file ngôn ngữ có thể chỉnh sửa. File được tải từ:

```
plugins/EnhancedEchest/language/<locale>/
```

Locale đang dùng được đặt bởi tùy chọn `language` trong [`config.yml`](/vi/docs/configuration) (mặc định: `en_US`). Plugin đi kèm `en_US` (English) và `vi_VN` (Tiếng Việt).

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
4. Đặt `language: <locale-của-bạn>` trong `config.yml`
5. Chạy `/ee reload`

## Tên Vật Phẩm Trong Bộ Chọn Biểu Tượng {#icon-picker-item-names}

Tên vật phẩm trong bộ chọn biểu tượng rương hoạt động khác với các tin nhắn ở trên. Chúng lấy trực tiếp từ máy khách Minecraft của từng người chơi và luôn hiển thị theo ngôn ngữ mà máy khách đó đang dùng, không cần cấu hình gì ở đây.

Tìm kiếm theo tên trong bộ chọn biểu tượng hiện hoạt động tốt nhất với tiếng Anh hoặc tiếng Việt. Các ngôn ngữ máy khách khác vẫn hiển thị đúng tên vật phẩm, nhưng từ khóa tìm kiếm có thể chỉ khớp theo tên tiếng Anh.

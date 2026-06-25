# Chuyển Dữ Liệu

Nếu người chơi của bạn đã có vật phẩm trong rương Ender **vanilla**, EnhancedEchest có thể nhập dữ liệu đó vào kho lưu trữ của riêng nó để không mất gì khi bạn cài plugin.

## Cách hoạt động

Khi một người chơi được chuyển, 27 ô rương Ender vanilla của họ được sao chép vào **rương #1** của EnhancedEchest, và rương Ender vanilla được xóa sạch. Toàn bộ thao tác diễn ra trong một tick máy chủ duy nhất:

1. Rương #1 được tạo ở kích thước đầy đủ nếu chưa tồn tại
2. Nội dung vanilla được ghi vào các ô đầu của rương #1
3. Dữ liệu được lưu vào cơ sở dữ liệu, rương Ender vanilla được xóa, và người chơi được đánh dấu là đã chuyển, tất cả cùng một lúc

::: info Đảm bảo chỉ tồn tại một nơi
Không bao giờ có khoảnh khắc nào mà vật phẩm tồn tại đồng thời trong cả rương Ender vanilla lẫn cơ sở dữ liệu của plugin. Điều này khiến việc chuyển dữ liệu an toàn trước nhân đôi.
:::

Mỗi người chơi chỉ được chuyển **đúng một lần**. Người chơi đã được đánh dấu là đã chuyển sẽ bị bỏ qua.

## Tự động chuyển khi vào

Để chuyển người chơi tự động ngay lần đầu họ vào sau khi plugin được cài, hãy bật nó trong `config.yml`:

```yaml
migration:
  enabled: true
```

Khi bật, bất kỳ người chơi chưa được chuyển nào sẽ có rương Ender vanilla nhập ngay khoảnh khắc họ vào. Khi mọi người bạn quan tâm đã đăng nhập, bạn có thể tắt nó lại.

## Chuyển thủ công

Quản trị viên có thể kích hoạt việc chuyển theo yêu cầu cho những người chơi đang trực tuyến:

| Lệnh | Tác dụng |
|------|----------|
| `/ee migrate run <player>` | Chuyển một người chơi đang trực tuyến |
| `/ee migrate run all` | Chuyển mọi người chơi hiện đang trực tuyến |

Cả hai đều cần quyền `enhancedechest.admin.migrate.run`. Người chơi đã được chuyển sẽ được báo là bị bỏ qua.

::: warning Chỉ người chơi trực tuyến
Việc chuyển đọc rương Ender vanilla trực tiếp của người chơi, nên nó chỉ hoạt động với người chơi **đang trực tuyến**. Người chơi ngoại tuyến sẽ được chuyển tự động ở lần vào tiếp theo nếu `migration.enabled` là `true`.
:::

# Cài Đặt

## Yêu cầu

Trước khi cài EnhancedEchest, hãy đảm bảo máy chủ của bạn đáp ứng các yêu cầu sau:

| Yêu cầu | Thông số |
|---------|----------|
| **Phiên bản Minecraft** | Chỉ 26.1.x |
| **Phần mềm máy chủ** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) hoặc các bản fork tương thích Paper |
| **Phiên bản Java** | Java 25 |

::: warning Cần Paper 26.1.x
EnhancedEchest dựa vào các API chỉ-có-ở-Paper (bootstrap plugin, lệnh Brigadier và Dialog API) và được build trên API **26.1.x**. Nó cần Paper hoặc một bản fork tương thích Paper (Folia, Purpur); nó sẽ **không** chạy trên CraftBukkit, cũng như trên các phiên bản Minecraft khác.
:::

::: warning Cần Java 25
Plugin được biên dịch cho Java 25. Hãy đảm bảo máy chủ của bạn chạy trên môi trường Java 25 (hoặc mới hơn), nếu không nó sẽ không tải được.
:::

## Tải về

Chọn nguồn tải bạn ưa thích:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/enhancedechest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://www.spigotmc.org/resources/enhancedechest-double-echest-plugin-%E2%9C%A8-26-1-2-26-2-%EF%B8%8F.136442/" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg" alt="Spigot" style="height: 24px;">
    Spigot
  </a>
  <a href="https://hangar.papermc.io/Nighter/EnhancedEchest" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

## Các bước cài đặt

### 1. Cài plugin

1. **Dừng máy chủ** của bạn hoàn toàn
2. Tải file `.jar` mới nhất từ một nguồn ở trên
3. Đặt nó vào thư mục `plugins/` của máy chủ
4. **Khởi động máy chủ** (tránh dùng `/reload`, nó có thể gây lỗi)

::: tip Không cần phụ thuộc bên ngoài
Mọi driver cơ sở dữ liệu, connection pool và bộ lập lịch đều được đóng gói bên trong jar. Bạn **không** cần cài gì khác trên máy chủ.
:::

### 2. Kiểm tra cài đặt

Chạy lệnh sau trong console máy chủ hoặc trong game để xác nhận plugin đã tải:

```
/plugins
```

EnhancedEchest sẽ xuất hiện trong danh sách với trạng thái màu xanh. Mặc định nó chạy trên **SQLite**, nên hoạt động ngay không cần thiết lập thêm.

### 3. Các file được tạo

Plugin tự động tạo các file của nó trong `plugins/EnhancedEchest/`:

| File | Mô tả |
|------|-------|
| `config.yml` | Cấu hình chính: kích thước rương, cơ sở dữ liệu, chuyển dữ liệu |
| `enderchests.db` | Cơ sở dữ liệu SQLite (backend lưu trữ mặc định) |
| `language/<locale>/messages.yml` | Tin nhắn hiển thị cho người chơi và prefix của plugin |
| `language/<locale>/gui.yml` | Tiêu đề kho đồ và nhãn menu rương |

## Cập nhật

1. **Tải** phiên bản mới
2. **Dừng** máy chủ
3. **Thay** file `.jar` cũ bằng file mới
4. **Khởi động** máy chủ

Cơ sở dữ liệu và cấu hình của bạn được giữ nguyên qua các lần cập nhật.

## Nhận trợ giúp

Nếu bạn gặp sự cố:

1. Kiểm tra **log console** để tìm thông báo lỗi
2. Báo lỗi trên **[GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues)**

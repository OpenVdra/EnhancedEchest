# Bắt Đầu

<img class="page-banner" src="/banner.png" alt="Banner EnhancedEchest" />

Khám phá **EnhancedEchest** và mọi tính năng plugin mang đến cho máy chủ Minecraft của bạn.

<CardGrid>

<DocCard icon="Package" title="Rương Ender Lớn Hơn" link="/vi/docs/larger-ender-chests" desc="Tối đa 54 ô, cấu hình theo bội số của chín." />

<DocCard icon="Archive" title="Hệ Thống Nhiều Rương" link="/vi/docs/multi-chest-system" desc="Sở hữu nhiều rương, mỗi rương quản lý từ menu trong game." />

<DocCard icon="ArrowRightLeft" title="Chuyển Dữ Liệu" link="/vi/docs/migration" desc="Nhập dữ liệu rương Ender vanilla sẵn có của người chơi." />

<DocCard icon="Layers" title="Hỗ Trợ Bedrock" link="/vi/docs/bedrock-support" desc="Menu hiển thị dưới dạng form Bedrock native qua Geyser." />

<DocCard icon="Globe" title="Đa Ngôn Ngữ" link="/vi/docs/language" desc="Mọi văn bản hiển thị cho người chơi đều có thể chỉnh sửa và dịch." />

</CardGrid>

Xem mục [Tính Năng](/vi/docs/features) để tìm hiểu chi tiết hơn về từng mục trên.

## Yêu Cầu

| Yêu cầu | Thông số |
|---------|----------|
| **Phiên bản Minecraft** | 1.21.11 - 26.2 |
| **Phần mềm máy chủ** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) hoặc các bản fork tương thích Paper |
| **Phiên bản Java** | Java 21 |

::: tip Đang dùng Folia?
Mọi thứ hoạt động giống hệt Paper, chỉ trừ một điều dành cho quản trị viên: nếu quản trị viên mở rương của người chơi bằng `/ee view` trong khi người đó đang mở chính rương đó, Paper cho phép cả hai cùng xem; Folia yêu cầu quản trị viên chờ đến khi người chơi đóng rương. Không có vật phẩm nào bị mất trong cả hai trường hợp.
:::

::: warning Đang dùng plugin chống exploit LPX? Hãy cập nhật lên 3.8.4 trở lên
Menu rương của EnhancedEchest dùng tính năng Dialog có sẵn của Minecraft, mà các phiên bản cũ của [LPX](https://builtbybit.com/resources/lpx-antipacketexploit.15709/) chặn lại. Hãy cập nhật lên 3.8.4 trở lên nếu bạn dùng nó.
:::

## Tải Về {#download}

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

::: info Bản phát hành trên Modrinth
Trang Modrinth đang được chuẩn bị và có thể chưa công khai. Cho đến khi nó hoạt động, hãy lấy bản build mới nhất từ **Spigot** hoặc **Hangar** ở trên.
:::

## Cài Đặt

1. **Dừng máy chủ** của bạn hoàn toàn
2. Tải file `.jar` mới nhất từ một nguồn ở trên
3. Đặt nó vào thư mục `plugins/` của máy chủ
4. **Khởi động máy chủ** (đừng dùng `/reload`)

::: tip Không cần cài thêm gì
Mọi thứ plugin cần, kể cả driver cơ sở dữ liệu, đã được đóng gói sẵn trong file jar. Mặc định plugin dùng SQLite, hoạt động ngay không cần thiết lập thêm. Xem [Cấu hình](/vi/docs/configuration) và [Cơ sở dữ liệu](/vi/docs/database) để biết những gì được tạo ra và cách đổi.
:::

## Cập Nhật

Tải phiên bản mới, dừng máy chủ, thay file `.jar` cũ bằng file mới, rồi khởi động lại. Dữ liệu và cấu hình của bạn được giữ nguyên qua các lần cập nhật.

## Cần Trợ Giúp?

Kiểm tra **log console** để tìm thông báo lỗi, báo lỗi trên [GitHub Issues](https://github.com/OpenVdra/EnhancedEchest/issues), hoặc hỏi trên [Discord](https://discord.com/invite/FJN7hJKPyb) của chúng tôi.

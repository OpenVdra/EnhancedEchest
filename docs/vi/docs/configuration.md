# Cấu Hình Chính

File `config.yml` nằm trong `plugins/EnhancedEchest/`. Nó điều khiển ngôn ngữ, kích thước rương, backend cơ sở dữ liệu và hành vi chuyển dữ liệu.

Bấm vào bất kỳ tùy chọn hoặc nhóm nào để xem thêm thông tin.

::: tip Áp dụng thay đổi mà không cần khởi động lại
Sau khi sửa `config.yml`, chạy `/ee reload` trong game hoặc từ console để áp dụng thay đổi.
:::

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Thư mục ngôn ngữ để tải từ <code>plugins/EnhancedEchest/language/</code>. Plugin đi kèm <code>en_US</code> (English) và <code>vi_VN</code> (Tiếng Việt). Để thêm một bản dịch, sao chép thư mục <code>en_US</code>, đổi tên nó, dịch các file bên trong, rồi đặt tùy chọn này thành tên thư mục mới.<br><br>
Xem trang <a href="/vi/docs/language">Ngôn ngữ</a> để biết danh sách đầy đủ các key tin nhắn.

</ConfigProperty>

<ConfigGroup name="enderchest">
<template #info>
Điều khiển chính các rương Ender.
</template>

<ConfigProperty name="default-size" value="54" type="number">
Số ô của rương được tự động tạo trong lần đầu tiên người chơi mở rương Ender. Phải là bội số của <code>9</code>, từ <code>9</code> đến <code>54</code>. Giá trị không hợp lệ được làm tròn về kích thước hợp lệ gần nhất.<br><br>

| Giá trị | Số hàng |
|---------|---------|
| <code>9</code> | 1 |
| <code>18</code> | 2 |
| <code>27</code> | 3 (kích thước vanilla) |
| <code>36</code> | 4 |
| <code>45</code> | 5 |
| <code>54</code> | 6 (rương đôi) |

</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="permission-chests">
<template #info>
Điều khiển các rương Ender được cấp tự động từ quyền. Xem trang Quyền để biết định dạng node và hành vi.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Khi <code>true</code>, người chơi được cấp các rương Ender từ quyền <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> (ví dụ <code>enhancedechest.additional_amount.2.slot.54</code> → hai rương 54 ô). Các node khớp sẽ <strong>cộng dồn</strong>. Việc cấp được đồng bộ mỗi lần người chơi mở rương Ender; mất một node sẽ xóa các rương đó, dồn mọi vật phẩm sang một rương tạm khôi phục được. Người chơi luôn giữ rương cơ bản của mình. Đặt thành <code>false</code> sẽ dừng đồng bộ nhưng giữ nguyên các rương đã cấp.<br><br>
Xem trang <a href="/vi/docs/permissions#permission-granted-chests">Quyền</a> để biết đầy đủ chi tiết.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Cấu hình nơi lưu nội dung rương Ender. SQLite dùng được ngay không cần thiết lập. Xem trang Cơ sở dữ liệu để biết cách thiết lập MySQL, MariaDB và PostgreSQL.
</template>

<ConfigProperty name="type" value="sqlite" type="string">
Backend lưu trữ. Giá trị được hỗ trợ: <code>sqlite</code>, <code>mysql</code>, <code>mariadb</code>, <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="sqlite-file" value="enderchests.db" type="string">
File cơ sở dữ liệu SQLite, tương đối với thư mục dữ liệu của plugin. Chỉ dùng khi <code>type</code> là <code>sqlite</code>.
</ConfigProperty>

<ConfigProperty name="host" value="localhost" type="string">
Host cơ sở dữ liệu. Dùng bởi <code>mysql</code>, <code>mariadb</code> và <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Port cơ sở dữ liệu. Mặc định <code>3306</code> cho MySQL/MariaDB, <code>5432</code> cho PostgreSQL.
</ConfigProperty>

<ConfigProperty name="database" value="enhancedechest" type="string">
Tên cơ sở dữ liệu (schema) để kết nối.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Tên người dùng cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Mật khẩu cơ sở dữ liệu. Để trống nếu không có mật khẩu.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Số kết nối tối đa trong pool. SQLite luôn dùng một kết nối duy nhất bất kể giá trị này.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="migration">
<template #info>
Điều khiển việc nhập tự động dữ liệu rương Ender vanilla sẵn có. Xem trang Chuyển dữ liệu để biết toàn bộ quy trình.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Khi <code>true</code>, bất kỳ người chơi nào chưa được chuyển sẽ có nội dung rương Ender vanilla nhập tự động khi họ vào. Việc chuyển chỉ chạy một lần cho mỗi người chơi.
</ConfigProperty>

</ConfigGroup>

</div>

## Ví Dụ Đầy Đủ

```yaml
# Cấu hình EnhancedEchest

# Locale ngôn ngữ để tải từ language/<locale>/
language: en_US

enderchest:
  # Số ô của rương được tự động tạo trong lần đầu người chơi mở rương Ender.
  # Phải là bội số của 9, từ 9 đến 54. Giá trị không hợp lệ được làm tròn.
  default-size: 54

permission-chests:
  # Cấp rương Ender từ các quyền có dạng:
  #   enhancedechest.additional_amount.<count>.slot.<size>
  #   ví dụ enhancedechest.additional_amount.2.slot.54  -> hai rương 54 ô.
  # Các quyền khớp sẽ CỘNG DỒN (cộng theo từng kích thước). Mất một quyền sẽ xóa các rương đó,
  # dồn mọi vật phẩm sang một rương tạm. Người chơi luôn giữ rương cơ bản của mình.
  enabled: true

database:
  # Backend lưu trữ: sqlite | mysql | mariadb | postgres
  type: sqlite
  # SQLite: đường dẫn tương đối với thư mục dữ liệu của plugin
  sqlite-file: enderchests.db
  # Port mặc định MySQL/MariaDB: 3306 | Port mặc định Postgres: 5432
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  pool-size: 10

migration:
  # Khi true: người chơi chưa được chuyển sẽ có rương Ender vanilla nhập khi vào
  enabled: false
```

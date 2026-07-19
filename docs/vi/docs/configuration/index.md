# Cấu Hình Chính

File `config.yml` nằm trong `plugins/EnhancedEchest/`. Nó điều khiển ngôn ngữ, kích thước rương, rương tạm, backend cơ sở dữ liệu, chế độ liên máy chủ, sao lưu tự động và hành vi chuyển dữ liệu.

Bấm vào bất kỳ tùy chọn hoặc nhóm nào để xem thêm thông tin.

::: tip Áp dụng thay đổi mà không cần khởi động lại
Sau khi sửa `config.yml`, chạy `/ee reload` trong game hoặc từ console để áp dụng thay đổi.
:::

::: tip Sửa ngay trong game
`/ee config` mở menu thiết lập với mỗi mục bên dưới là một trang riêng. Khi lưu một trang, giá trị được ghi vào `config.yml`, giữ nguyên mọi dòng chú thích, và áp dụng ngay nên bạn không cần `/ee reload`. Riêng thiết lập kết nối vẫn cần khởi động lại server, và menu sẽ báo khi bạn vừa đổi một thiết lập như vậy.
:::

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Thư mục ngôn ngữ để tải từ <code>plugins/EnhancedEchest/language/</code>. Plugin đi kèm <code>en_US</code> (English) và <code>vi_VN</code> (Tiếng Việt). Khi <code>language-auto-detect</code> bật, đây là ngôn ngữ <strong>dự phòng</strong> cho các máy khách có ngôn ngữ chưa được dịch.<br><br>
Xem trang <a href="/vi/docs/configuration/language">Ngôn ngữ</a> để biết danh sách đầy đủ các key tin nhắn.

</ConfigProperty>

<ConfigProperty name="language-auto-detect" value="true" type="boolean">

Khi bật (mặc định), mỗi người chơi thấy tin nhắn và menu theo <strong>ngôn ngữ máy khách Minecraft của chính họ</strong> nếu có bản dịch tương ứng (đóng gói sẵn, hoặc thêm dưới <code>language/</code>), nếu không thì quay về <code>language</code> ở trên. Tắt để hiện cho mọi người chơi đúng một locale <code>language</code>.

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

Bạn cũng có thể ghi đè kích thước rương cơ bản <strong>theo từng người chơi</strong> bằng quyền <code>enhancedechest.default_size.&lt;size&gt;</code> (luôn khả dụng, không cần cấu hình gì). Xem trang <a href="/vi/docs/access/permission-chests#default-size-permission">Rương Theo Quyền</a>.

</ConfigProperty>

<ConfigProperty name="list-menu" value="dialog" type="string">
Cách hiển thị danh sách rương của <code>/eclist</code> cho người chơi. Đây cũng là danh sách mà <code>/ec</code> và chuột phải mở khi người chơi có 2 rương trở lên và chưa đặt rương chính. Hai giá trị:<br><br>

<code>dialog</code> (mặc định): menu hộp thoại của Paper, với đầy đủ thao tác <strong>Chế độ chỉnh sửa</strong> (đổi tên, đặt rương chính, chọn biểu tượng, sắp xếp).<br><br>

<code>inventory</code>: một GUI rương đơn giản chỉ <strong>liệt kê</strong> các rương. Bấm vào biểu tượng rương sẽ mở rương đó ngay, không có đổi tên, rương chính, biểu tượng hay sắp xếp. <strong>Chế độ này hoạt động với người chơi có tối đa 28 rương.</strong> Người chơi sở hữu hơn 28 rương luôn nhận menu <code>dialog</code>, bất kể cấu hình này.

</ConfigProperty>

<ConfigProperty name="shift-click-list" value="true" type="boolean">
Cho phép giữ <strong>shift</strong> khi chuột phải vào khối rương ender để mở danh sách rương (cùng menu với <code>/eclist</code>) thay vì mở một rương. Chuột phải bình thường vẫn mở rương như cũ, nên người chơi đã đặt rương chính vẫn tới được các rương khác mà không cần gõ lệnh. Tắt tùy chọn này để shift + chuột phải hoạt động y hệt chuột phải thường.
</ConfigProperty>

<ConfigProperty name="features.rename" value="true" type="boolean">
Cho phép người chơi đặt tên hiển thị tùy chỉnh cho rương từ menu <strong>Chế độ chỉnh sửa</strong>. Tắt sẽ ẩn nút <strong>Đổi tên</strong>; rương đã có tên vẫn giữ tên. Đây là công tắc <strong>toàn cục</strong>, áp dụng cho mọi người chơi như nhau.
</ConfigProperty>

<ConfigProperty name="features.icon" value="true" type="boolean">
Cho phép người chơi chọn một vật phẩm làm biểu tượng cho rương trong danh sách. Tắt sẽ ẩn nút <strong>Chọn biểu tượng</strong>; rương đã có biểu tượng vẫn giữ. Công tắc toàn cục.
</ConfigProperty>

<ConfigProperty name="features.sort" value="false" type="boolean">
Cho phép người chơi tự động sắp xếp rương từ menu <strong>Chế độ chỉnh sửa</strong>. Khi bật, nút <strong>Sắp xếp</strong> sẽ xuất hiện, gộp các vật phẩm giống nhau thành cụm đầy và sắp xếp lại toàn bộ rương theo loại vật phẩm. Tắt theo mặc định. Công tắc toàn cục.
</ConfigProperty>

<ConfigProperty name="features.sort-cooldown" value="10s" type="string">
Khoảng cách nhỏ nhất giữa hai lần sắp xếp của cùng một người chơi, để nút <strong>Sắp xếp</strong> không bị spam (mỗi lần sắp xếp đọc và ghi lại rương). Định dạng thời gian: <code>20s</code>, <code>5m</code>, <code>1h</code>, … Đặt <code>0s</code> để bỏ thời gian chờ. Chỉ dùng khi <code>features.sort</code> được bật.
</ConfigProperty>

<ConfigProperty name="features.rename-blacklist" :value="['server', 'admin', 'staff', 'owner']" type="list">
Các từ người chơi không được dùng trong tên tùy chỉnh của rương. So khớp <strong>không phân biệt hoa/thường</strong> và theo <strong>chuỗi con</strong>, nên <code>admin</code> cũng chặn <code>iAmAdmin</code> và <code>ADMIN</code>. Một lần đổi tên chứa bất kỳ từ nào trong danh sách sẽ bị từ chối trước khi lưu và người chơi được yêu cầu chọn tên khác. Việc kiểm tra dựa trên phần văn bản <em>hiển thị</em>, nên không thể dùng mã màu để giấu từ bị cấm. Để trống danh sách để cho phép mọi tên; xóa tên rương (lưu để trống) luôn được phép.
</ConfigProperty>

<ConfigProperty name="features.rename-colors" value="true" type="boolean">
Cho phép người chơi tô màu tên rương hay không. Khi <code>true</code>, tên chấp nhận mã màu <code>&amp;</code> kiểu cũ, mã hex <code>&amp;#RRGGBB</code>, và các thẻ <a href="https://docs.advntr.dev/minimessage/format.html" target="_blank">MiniMessage</a> trang trí như <code>&lt;red&gt;</code>, <code>&lt;gradient&gt;</code>, <code>&lt;rainbow&gt;</code>, và <code>&lt;bold&gt;</code>. Các thẻ tương tác (<code>&lt;click&gt;</code>, <code>&lt;hover&gt;</code>, <code>&lt;insertion&gt;</code>, …) <strong>luôn bị loại bỏ</strong>, nên tên không bao giờ có thể chạy lệnh hay giả mạo tooltip. Khi <code>false</code>, tên hiển thị đúng như đã nhập và mọi mã màu hiện ra dưới dạng văn bản thường. Công tắc toàn cục.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="permission-chests">
<template #info>
Điều khiển các rương Ender được cấp tự động từ quyền. Xem trang Quyền để biết định dạng node và hành vi.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Khi <code>true</code>, người chơi được cấp các rương Ender từ quyền <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> (ví dụ <code>enhancedechest.additional_amount.2.slot.54</code> → hai rương 54 ô). Các node khớp sẽ <strong>cộng dồn</strong>. Việc cấp được đồng bộ mỗi lần người chơi mở rương Ender; mất một node sẽ xóa các rương đó, dồn mọi vật phẩm sang một rương tạm để người chơi lấy lại. Người chơi luôn giữ rương cơ bản của mình. Đặt thành <code>false</code> sẽ dừng đồng bộ nhưng giữ nguyên các rương đã cấp.<br><br>
Xem trang <a href="/vi/docs/access/permission-chests#permission-granted-chests">Rương Theo Quyền</a> để biết đầy đủ chi tiết.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="temp-enderchest">
<template #info>
Điều khiển rương tạm. Khi một rương bị thu nhỏ, bị xóa (không kèm <code>force</code>), hoặc hết hạn mà vẫn còn vật phẩm bên trong, những vật phẩm đó được chuyển vào một rương tạm; rương tạm biến mất khi được lấy hết đồ hoặc khi nó hết hạn. Rương tạm chỉ cho lấy ra: người chơi lấy được vật phẩm ra nhưng không bỏ thêm vào được.
</template>

<ConfigProperty name="expiry" value="7d" type="string">
Thời gian tồn tại của một rương tạm trước khi nó hết hạn, cùng với mọi vật phẩm còn bên trong. Định dạng thời gian: <code>20s</code>, <code>5m</code>, <code>1h</code>, hoặc kết hợp như <code>1d_2h_30m</code>. Đơn vị: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="check-interval" value="5m" type="string">
Bao lâu plugin quét rương hết hạn một lần. Giá trị thấp hơn xóa rương hết hạn sớm hơn; giá trị mặc định phù hợp với hầu hết máy chủ.
</ConfigProperty>

<ConfigProperty name="deny-sound.enabled" value="true" type="boolean">
Có phát âm thanh khi người chơi cố bỏ vật phẩm vào rương tạm chỉ-cho-lấy-ra hay không. Đặt thành <code>false</code> để tắt âm thanh.
</ConfigProperty>

<ConfigProperty name="deny-sound.key" value="minecraft:entity.villager.no" type="string">
Âm thanh sẽ phát. Chấp nhận mọi id âm thanh Minecraft; mặc định là tiếng "hừ" từ chối của dân làng.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Cấu hình nơi lưu nội dung rương Ender. SQLite dùng được ngay không cần thiết lập. Xem trang Cơ sở dữ liệu để biết cách thiết lập MySQL, MariaDB và PostgreSQL.
</template>

<ConfigProperty name="type" value="sqlite" type="string">
Backend lưu trữ. Giá trị được hỗ trợ: <code>sqlite</code>, <code>mysql</code>, <code>mariadb</code>, <code>postgres</code>.
</ConfigProperty>

<ConfigProperty name="table-prefix" value="echest_" type="string">
Được thêm vào trước mọi tên bảng mà plugin tạo ra (ví dụ <code>echest_enderchests</code>), giúp dễ phân biệt dữ liệu của plugin với các plugin khác và an toàn khi dùng chung cơ sở dữ liệu với chúng. Chỉ chữ cái, chữ số và dấu gạch dưới. Đổi giá trị này trên bản cài đã có sẵn sẽ khiến plugin tự đổi tên các bảng hiện có cho khớp vào lần khởi động tiếp theo. Xem <a href="/vi/docs/database/#cac-bang">Các Bảng</a>.
</ConfigProperty>

<ConfigProperty name="autosave-interval" value="3m" type="string">
Tần suất ghi các thay đổi trong bộ nhớ về cơ sở dữ liệu (dữ liệu của mỗi người chơi trực tuyến được giữ trong bộ nhớ; cũng được lưu vài giây sau khi họ thoát và một lần khi tắt máy chủ). Tối thiểu <code>30s</code>. Xem <a href="/vi/docs/database/#cach-du-lieu-duoc-luu">Cách dữ liệu được lưu</a>.
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

<ConfigProperty name="ssl" value="disable" type="string">
Chế độ TLS cho kết nối MySQL, MariaDB hoặc PostgreSQL từ xa. Một trong các giá trị:

- **`disable`**: không mã hóa (mặc định).
- **`require`**: mã hóa kết nối, nhưng **không** xác minh certificate hay hostname của máy chủ. Chặn được nghe lén thụ động, nhưng không chặn được tấn công man-in-the-middle chủ động.
- **`verify-full`**: mã hóa **và** xác minh chuỗi certificate cùng hostname. Đây là chế độ duy nhất chống được man-in-the-middle; CA của máy chủ cơ sở dữ liệu phải được JVM của máy chủ Minecraft tin cậy (truststore).

Cần khởi động lại máy chủ hoàn toàn.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Số kết nối tối đa trong pool. Chỉ áp dụng cho MySQL, MariaDB và PostgreSQL.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="cross-server">
<template #info>
Cho phép nhiều máy chủ sau proxy dùng chung một cơ sở dữ liệu, để rương Ender của người chơi đi theo họ giữa các máy chủ. Cần một cơ sở dữ liệu MySQL/MariaDB/PostgreSQL dùng chung và một máy chủ Redis dùng chung. Xem <a href="/vi/docs/database/cross-server">Hỗ Trợ Liên Máy Chủ</a>. Thay đổi trong mục này cần khởi động lại máy chủ hoàn toàn.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Bật chế độ liên máy chủ. Yêu cầu <code>database.type</code> là <code>mysql</code>, <code>mariadb</code> hoặc <code>postgres</code>; plugin từ chối khởi động ở chế độ này trên SQLite.
</ConfigProperty>

<ConfigProperty name="server-id" value="" type="string">
Tên của máy chủ này, không trùng với máy chủ nào khác trong mạng (ví dụ <code>survival</code>). Để trống thì tự sinh tên mới mỗi lần khởi động. Không bao giờ đặt hai máy chủ trùng tên.
</ConfigProperty>

<ConfigProperty name="redis.host" value="localhost" type="string">
Địa chỉ máy chủ Redis, mọi máy chủ trong mạng đều phải kết nối được.
</ConfigProperty>

<ConfigProperty name="redis.port" value="6379" type="number">
Cổng Redis.
</ConfigProperty>

<ConfigProperty name="redis.password" value="" type="string">
Mật khẩu Redis. Để trống nếu Redis không có mật khẩu.
</ConfigProperty>

<ConfigProperty name="redis.ssl" value="false" type="boolean">
Kết nối Redis qua TLS. Khi bật, kết nối được mã hóa và chuỗi certificate **cùng** hostname của máy chủ được xác minh theo JVM truststore, tương đương chế độ `verify-full` của cơ sở dữ liệu. Certificate self-signed hoặc do CA riêng cấp phải được JVM tin cậy trước, nếu không kết nối sẽ thất bại lúc khởi động.
</ConfigProperty>

<ConfigProperty name="redis.database" value="0" type="number">
Số thứ tự database của Redis (0 đến 15 trên bản cài Redis mặc định).
</ConfigProperty>

<ConfigProperty name="redis.key-prefix" value="echest:" type="string">
Tiền tố cho các khóa Redis mà plugin sử dụng. Chỉ đổi khi nhiều mạng máy chủ riêng biệt dùng chung một Redis.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="backup">
<template #info>
Tự động lưu bản sao toàn bộ dữ liệu rương Ender theo định kỳ. <strong>Chỉ hỗ trợ SQLite</strong> - nếu dùng MySQL/MariaDB/PostgreSQL, hãy dùng công cụ sao lưu của chính máy chủ cơ sở dữ liệu.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Bật hoặc tắt sao lưu tự động.
</ConfigProperty>

<ConfigProperty name="interval" value="6h" type="string">
Bao lâu sao lưu một lần. Ví dụ: <code>30m</code> (mỗi 30 phút), <code>6h</code> (mỗi 6 giờ), <code>1d</code> (mỗi ngày một lần). Đơn vị: <code>s m h d w mo y</code>.
</ConfigProperty>

<ConfigProperty name="keep" value="10" type="number">
Giữ bao nhiêu bản sao lưu. Khi vượt quá số này, các bản <strong>cũ nhất</strong> sẽ tự động bị xóa để thư mục không phình to mãi. Dùng <code>0</code> để giữ tất cả và không bao giờ xóa.
</ConfigProperty>

<ConfigProperty name="on-startup" value="false" type="boolean">
Khi <code>true</code>, tạo thêm một bản sao lưu ngay khi server khởi động, bên cạnh lịch định kỳ.
</ConfigProperty>

<ConfigProperty name="folder" value="backups" type="string">
Thư mục (bên trong <code>plugins/EnhancedEchest/</code>) nơi lưu các file sao lưu. Mỗi file có tên dạng <code>enderchests-20260625-143000.db</code> (ngày và giờ tạo), nên chúng sắp xếp từ cũ đến mới.
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
language-auto-detect: true

enderchest:
  # Số ô của rương được tự động tạo trong lần đầu người chơi mở rương Ender.
  # Phải là bội số của 9, từ 9 đến 54. Giá trị không hợp lệ được làm tròn.
  # Có thể ghi đè theo từng người chơi qua quyền enhancedechest.default_size.<size> (không cần cấu hình).
  default-size: 54

  # Cách hiển thị /eclist: "dialog" (mặc định, menu Chế độ chỉnh sửa đầy đủ) hoặc "inventory" (GUI rương
  # đơn giản chỉ liệt kê rương; bấm để mở). Menu inventory lớn dần 27 -> 36 -> 45 -> 54 ô và chứa tối đa
  # 28 rương; người chơi có hơn 28 rương luôn nhận menu dialog.
  list-menu: dialog

  # Mở danh sách rương (cùng menu với /eclist) bằng shift + chuột phải vào khối rương ender.
  shift-click-list: true

  # Công tắc toàn cục cho các nút trong "Chế độ chỉnh sửa" (Đổi tên / Chọn biểu tượng / Sắp xếp).
  features:
    rename: true
    icon: true
    sort: false
    sort-cooldown: 10s
    rename-blacklist:
      - server
      - admin
      - staff
      - owner
    rename-colors: true

permission-chests:
  # Cấp rương Ender từ các quyền có dạng:
  #   enhancedechest.additional_amount.<count>.slot.<size>
  #   ví dụ enhancedechest.additional_amount.2.slot.54  -> hai rương 54 ô.
  # Các quyền khớp sẽ CỘNG DỒN (cộng theo từng kích thước). Mất một quyền sẽ xóa các rương đó,
  # dồn mọi vật phẩm sang một rương tạm. Người chơi luôn giữ rương cơ bản của mình.
  enabled: true

temp-enderchest:
  # Thời gian tồn tại của rương tạm được tạo khi thu nhỏ/xóa/hết hạn còn đồ.
  expiry: 7d
  # Bao lâu plugin quét rương hết hạn một lần.
  check-interval: 5m
  # Âm thanh phát cho người chơi cố bỏ đồ vào rương tạm chỉ-cho-lấy-ra.
  deny-sound:
    enabled: true
    key: minecraft:entity.villager.no

database:
  # Backend lưu trữ: sqlite | mysql | mariadb | postgres
  type: sqlite
  # Được thêm vào trước mọi tên bảng mà plugin tạo ra. Chỉ chữ cái, chữ số và dấu gạch dưới.
  table-prefix: echest_
  # Tần suất ghi các thay đổi trong bộ nhớ về cơ sở dữ liệu. Tối thiểu 30s.
  autosave-interval: 3m
  # SQLite: đường dẫn tương đối với thư mục dữ liệu của plugin
  sqlite-file: enderchests.db
  # Port mặc định MySQL/MariaDB: 3306 | Port mặc định Postgres: 5432
  host: localhost
  port: 3306
  database: enhancedechest
  username: root
  password: ""
  # Bắt buộc kết nối TLS mã hóa cho cơ sở dữ liệu từ xa. Cần khởi động lại.
  ssl: false
  pool-size: 10

cross-server:
  # Nhiều máy chủ sau proxy dùng chung một cơ sở dữ liệu. Cần mysql/mariadb/postgres
  # và một máy chủ Redis dùng chung. Xem trang Cơ Sở Dữ Liệu để biết cách thiết lập.
  enabled: false
  # Tên riêng cho từng máy chủ. Để trống thì tự sinh mỗi lần khởi động.
  server-id: ""
  redis:
    host: localhost
    port: 6379
    password: ""
    ssl: false
    database: 0
    key-prefix: "echest:"

backup:
  # Sao lưu tự động toàn bộ dữ liệu rương Ender (chỉ SQLite). Diễn ra an toàn khi server đang chạy.
  enabled: true
  # Bao lâu sao lưu một lần: 30m, 6h, 1d, ... (đơn vị: s m h d w mo y)
  interval: 6h
  # Giữ bao nhiêu bản gần nhất; các bản cũ bị tự xóa. 0 = giữ tất cả.
  keep: 10
  # Sao lưu thêm một lần khi server khởi động.
  on-startup: false
  # Thư mục (trong plugins/EnhancedEchest/) chứa file sao lưu.
  folder: backups

migration:
  # Khi true: người chơi chưa được chuyển sẽ có rương Ender vanilla nhập khi vào
  enabled: false
```

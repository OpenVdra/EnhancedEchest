# Tính năng ✨

Đây là tổng quan mọi thứ mà **EnhancedEchest** mang đến cho máy chủ Minecraft của bạn. Nhảy thẳng tới phần bạn quan tâm:

<CardGrid>

<DocCard icon="📦" title="Rương Ender Lớn Hơn" link="#larger-ender-chests" desc="Tối đa 54 ô, cấu hình theo bội số của chín." />

<DocCard icon="🗂️" title="Hệ Thống Nhiều Rương" link="#multi-chest-system" desc="Sở hữu nhiều rương, mỗi rương quản lý từ menu trong game." />

<DocCard icon="💾" title="Lưu Bằng Cơ Sở Dữ Liệu" link="#database-storage" desc="SQLite, MySQL, MariaDB hoặc PostgreSQL; bất đồng bộ và dùng pool." />

<DocCard icon="🛡️" title="Không Nhân Đôi Vật Phẩm" link="#no-item-duplication" desc="Chống nhân đôi theo thiết kế, kể cả khi có người xem chung và quản trị viên." />

<DocCard icon="🔄" title="Chuyển Dữ Liệu" link="#migration" desc="Nhập dữ liệu rương Ender vanilla sẵn có của người chơi." />

<DocCard icon="🌿" title="Đa Nền Tảng" link="#cross-platform-support" desc="Một file jar cho Paper, Folia và các bản fork Purpur." />

</CardGrid>

## 📦 Rương Ender Lớn Hơn {#larger-ender-chests}

EnhancedEchest thay rương Ender vanilla 27 ô bằng một GUI có thể cấu hình lên tới **54 ô**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

<CardGrid>

<FeatureCard icon="🖱️" title="Cùng Khối, Nhiều Không Gian Hơn">

Người chơi mở rương Ender đúng theo cách họ vẫn luôn làm, bằng cách chuột phải vào khối rương Ender, và nhận được kho đồ lớn hơn thay vì màn hình vanilla.

- Mở bằng chuột phải hoặc qua <code>/ec</code>
- Khối rương Ender thật vẫn giữ hiệu ứng đóng/mở nắp
- Kích thước cấu hình theo bội số của 9, từ 9 đến 54

</FeatureCard>

<FeatureCard icon="🎚️" title="Kích Thước Tùy Chỉnh">

Kích thước mặc định cho rương đầu tiên của người chơi được đặt bằng <code>enderchest.default-size</code> trong <code>config.yml</code>. Quản trị viên cũng có thể đổi kích thước từng rương riêng lẻ bằng <code>/ee resize</code>.

- Kích thước hợp lệ: <code>9</code>, <code>18</code>, <code>27</code>, <code>36</code>, <code>45</code>, <code>54</code>
- Giá trị không hợp lệ được làm tròn về kích thước hợp lệ gần nhất
- Mặc định là <code>54</code> (một rương đôi đầy đủ)

</FeatureCard>

</CardGrid>

---

## 🗂️ Hệ Thống Nhiều Rương {#multi-chest-system}

Người chơi không còn bị giới hạn ở một rương Ender. Mỗi người chơi có thể sở hữu nhiều rương, quản lý qua một menu trong game.

<figure class="feature-figure">
  <img alt="The chest list menu showing several owned ender chests" src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" />
  <figcaption>Với hai rương trở lên, mở rương Ender sẽ bật lên menu này liệt kê mọi rương bạn sở hữu, kèm số ô của từng rương.</figcaption>
</figure>

<CardGrid>

<FeatureCard icon="📋" title="Menu Danh Sách Rương">
Chạy <code>/eclist</code> để mở một hộp thoại liệt kê mọi rương người chơi sở hữu, mỗi rương hiển thị số ô của nó. Ô tích <strong>Chế độ chỉnh sửa</strong> thay đổi điều xảy ra khi bấm vào một rương: khi tắt (mặc định), rương mở ngay; khi bật, bấm vào rương sẽ mở menu quản lý của nó, nơi người chơi có thể đổi tên, gán biểu tượng tùy chỉnh, hoặc chọn rương nào là rương chính. Ô tích chuyển trạng thái tại chỗ nên việc bật/tắt không bao giờ mở lại hộp thoại.
</FeatureCard>

<FeatureCard icon="⭐" title="Rương Chính">
Với nhiều rương, người chơi có thể chọn một rương làm <strong>rương chính</strong>, rương được mở trực tiếp bằng <code>/ec</code> và bằng chuột phải vào khối rương Ender. Cho đến khi chọn rương chính, những thao tác đó sẽ mở menu quản lý thay thế. Một rương mới không bao giờ tự động trở thành rương chính; người chơi tự đặt nó từ menu (và luôn có thể vào menu bằng <code>/eclist</code>).
</FeatureCard>

<FeatureCard icon="🎨" title="Tùy Chỉnh Từng Rương">
Người chơi cá nhân hóa rương của mình ngay từ menu trong game — không cần lệnh. Mở màn hình quản lý của một rương để:

- <strong>Đổi tên</strong> — một rương đã đặt tên sẽ hiển thị tên đó làm tiêu đề kho đồ (rương chưa đặt tên dùng <em>Rương Ender</em> hoặc <em>Rương Ender {index}</em>)
- <strong>Chọn biểu tượng</strong> — chọn bất kỳ vật phẩm nào đại diện cho rương trong danh sách, với bộ chọn vật phẩm có tìm kiếm, hoặc đặt lại về biểu tượng rương Ender mặc định

</FeatureCard>

<FeatureCard icon="🛠️" title="Quản Lý Bởi Quản Trị Viên">
Quản trị viên có thể thêm, đổi kích thước và xóa rương cho bất kỳ người chơi nào bằng <code>/ee add</code>, <code>/ee resize</code> và <code>/ee delete</code>. Xóa rương chính sẽ khiến người chơi không có rương chính cho đến khi họ chọn rương mới từ menu.
</FeatureCard>

<FeatureCard icon="🎫" title="Rương Cấp Theo Quyền">
Phát rương theo rank thay vì bằng lệnh. Quyền <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> cấp ngần ấy rương với kích thước đó, ví dụ <code>...2.slot.54</code> cho hai rương 54 ô. Các node khớp <strong>cộng dồn</strong>, việc cấp được đồng bộ khi mở, và xóa một node sẽ xóa các rương đó (vật phẩm bên trong được dồn sang một rương tạm có thể khôi phục). Rương cơ bản của người chơi luôn được bảo vệ. Xem trang <a href="/vi/docs/permissions#permission-granted-chests">Quyền</a>.
</FeatureCard>

<FeatureCard icon="👁️" title="Xem Rương Của Người Chơi Khác">
Với <code>/ee view &lt;player&gt;</code> quản trị viên có thể mở rương của bất kỳ người chơi nào, dù trực tuyến hay ngoại tuyến. Một rương sẽ mở trực tiếp; với nhiều rương, một <strong>menu lựa chọn</strong> cho phép bạn chọn (hoặc dùng <code>/ee view &lt;player&gt; list</code> để luôn hiển thị nó, hoặc <code>&lt;index&gt;</code> cho một rương cụ thể). Cấp <code>admin.view</code> để xem chỉ-đọc, hoặc thêm <code>admin.edit</code> để lấy và thêm vật phẩm. Quản trị viên tham gia vào <strong>cùng một kho đồ trực tiếp</strong> mà chủ rương thấy, nên nội dung không bao giờ có thể bị nhân đôi (trên Paper cả hai thậm chí có thể chỉnh sửa cùng lúc).
</FeatureCard>

</CardGrid>

<div class="placeholder-row">
  <figure>
    <img width="1162" height="1067" alt="A chest's management menu with rename, icon, and set-as-main options" src="https://github.com/user-attachments/assets/76bc97fa-1dcb-4e39-8bde-9504ebc4d768" />
    <figcaption>Màn hình quản lý của một rương: đổi tên, chọn biểu tượng, hoặc đặt làm rương chính.</figcaption>
  </figure>
  <figure>
    <img width="1013" height="1067" alt="The rename prompt for an ender chest" src="https://github.com/user-attachments/assets/573814dd-6f58-4e9c-b65a-58842e3ba2a2" />
    <figcaption>Đổi tên một rương; tên bạn nhập sẽ trở thành tiêu đề kho đồ của nó.</figcaption>
  </figure>
</div>

<figure class="feature-figure">
  <img width="1802" height="1068" alt="The searchable item picker for choosing a chest icon" src="https://github.com/user-attachments/assets/ce6b235b-980c-4403-86d3-503c25f32d77" />
  <figcaption>Chọn bất kỳ vật phẩm nào làm biểu tượng cho rương với bộ chọn vật phẩm có tìm kiếm.</figcaption>
</figure>

---

## 💾 Lưu Bằng Cơ Sở Dữ Liệu {#database-storage}

Nội dung của mọi rương được tuần tự hóa và lưu vào cơ sở dữ liệu, không phải trong các file người chơi phẳng.

<CardGrid>

<FeatureCard icon="🗃️" title="Nhiều Backend">

- **SQLite**: tích hợp sẵn, không cần thiết lập, hoàn hảo cho máy chủ đơn lẻ
- **MySQL** / **MariaDB**: lưu trữ dùng chung cho các network
- **PostgreSQL**: cho các thiết lập đã chạy sẵn Postgres

</FeatureCard>

<FeatureCard icon="🚀" title="Bất Đồng Bộ & Dùng Pool">
Mọi tác vụ cơ sở dữ liệu chạy ngoài luồng chính trên một executor chuyên dụng, với connection pool HikariCP. Việc lưu không bao giờ chặn tick của máy chủ.
</FeatureCard>

</CardGrid>

Xem trang [Cơ sở dữ liệu](/vi/docs/database) để biết cách thiết lập kết nối.

---

## 🛡️ Không Nhân Đôi Vật Phẩm {#no-item-duplication}

EnhancedEchest được xây dựng sao cho nội dung rương Ender không bao giờ có thể bị nhân đôi qua các lỗi reload.

- Nội dung được **tải mới** từ cơ sở dữ liệu mỗi lần rương được mở
- Nội dung được **lưu ngay lập tức** khi rương đóng hoặc người chơi thoát
- Một **chuỗi chờ lưu (pending-save)** đảm bảo lần mở tiếp theo luôn chờ mọi tác vụ lưu đang diễn ra hoàn tất trước khi tải, nên người chơi không bao giờ có thể mở lại và đọc dữ liệu cũ
- Khi hai người cùng xem một rương (ví dụ quản trị viên qua `/ee view` và chủ rương), họ chia sẻ **một kho đồ trực tiếp**, nên ngay cả chỉnh sửa đồng thời cũng không thể nhân đôi vật phẩm (chỉnh sửa song song trên Paper; mỗi lần một người xem trên Folia)

---

## 🔄 Chuyển Dữ Liệu {#migration}

Đã có người chơi với dữ liệu rương Ender vanilla? EnhancedEchest có thể nhập nó.

- Khi <code>migration.enabled</code> là <code>true</code>, rương Ender vanilla của người chơi chưa được chuyển sẽ được nhập tự động khi họ vào
- Quản trị viên có thể kích hoạt chuyển dữ liệu thủ công cho một người chơi hoặc tất cả người đang trực tuyến bằng <code>/ee migrate run</code>
- Mỗi người chơi chỉ được chuyển một lần và được đánh dấu là đã xong sau đó

Xem trang [Chuyển dữ liệu](/vi/docs/migration) để biết chi tiết.

---

## 🌿 Hỗ Trợ Đa Nền Tảng {#cross-platform-support}

EnhancedEchest dùng một bộ lập lịch nhận biết vùng (FoliaLib) bên dưới, nên cùng một file jar chạy được trên:

| Nền tảng | Hỗ trợ |
|----------|--------|
| **Paper** | ✅ |
| **Folia** | ✅ |
| **Purpur / các bản fork Paper** | ✅ |

Được xây dựng và kiểm thử trên **Minecraft 26.1.x** với **Java 25**; các phiên bản Minecraft khác không được hỗ trợ.

Tất cả thư viện bên thứ ba (driver cơ sở dữ liệu, connection pool, bộ lập lịch) đều được shade và relocate vào trong jar, nên **không cần tải thêm hay cài driver phía máy chủ**.

---

## 🪨 Hỗ Trợ Bedrock (Geyser)

Các menu của EnhancedEchest được xây dựng trên **Dialog API** hiện đại của Paper, và [Geyser](https://geysermc.org/)
tự động chuyển các dialog Java thành **form Bedrock** native. Điều đó nghĩa là người chơi Bedrock vào
máy chủ của bạn qua proxy Geyser sẽ thấy menu rương `/eclist` được hiển thị dưới dạng form Bedrock đúng chuẩn,
nút bấm, ô nhập, và tất cả, mà **không cần cấu hình thêm** từ phía bạn.

- Danh sách rương, hộp thoại đổi tên, và thao tác "Đặt làm rương chính" đều hiện ra dưới dạng giao diện Bedrock
- Không cần cài gì ở phía EnhancedEchest: việc chuyển đổi diễn ra trong Geyser
- Bản thân kho đồ rương là một container thông thường nên hoạt động bình thường trên Bedrock

::: tip Geyser đang đảm nhận việc này
Hỗ trợ này đến từ tính năng chuyển đổi form Java-sang-Bedrock có sẵn của Geyser, không phải từ một luồng mã
riêng cho Bedrock trong plugin. Hãy giữ bản build Geyser của bạn cập nhật hợp lý để việc chuyển đổi dialog
mượt mà nhất.
:::

---

## 🔔 Thông Báo Cập Nhật

EnhancedEchest kiểm tra bản phát hành mới khi khởi động và lặng lẽ thông báo cho quản trị viên trong game khi có bản cập nhật, kèm một liên kết tải về bấm được.

---

## 🌐 Đa Ngôn Ngữ

Mọi văn bản hiển thị cho người chơi nằm trong các file ngôn ngữ có thể chỉnh sửa. Hãy tạo một bản dịch bằng cách sao chép thư mục <code>en_US</code>, dịch nó, và trỏ <code>language</code> tới locale mới của bạn. Tin nhắn hỗ trợ đầy đủ định dạng MiniMessage. Xem trang [Ngôn ngữ](/vi/docs/language).

---

## 📊 Thống Kê Sử Dụng

EnhancedEchest gửi dữ liệu sử dụng ẩn danh tới [bStats](https://bstats.org/plugin/bukkit/EnhancedEchest/32142),
nên bạn có thể xem có bao nhiêu máy chủ chạy plugin cùng các phân tích như backend lưu trữ và ngôn ngữ.
Việc thu thập là ẩn danh và có thể tắt toàn cục trong `plugins/bStats/config.yml`.

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/EnhancedEchest/32142" target="_blank" rel="noreferrer">
    <img src="https://bstats.org/signatures/bukkit/EnhancedEchest.svg" alt="EnhancedEchest bStats charts" style="max-width: 100%;">
  </a>
</p>

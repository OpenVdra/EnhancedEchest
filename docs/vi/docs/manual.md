# Giới Thiệu

<img class="page-banner" src="/banner.png" alt="Banner EnhancedEchest" />

Khám phá **EnhancedEchest** và mọi tính năng plugin mang đến cho máy chủ Minecraft của bạn.

<CardGrid>

<DocCard icon="Package" title="Rương Ender Lớn Hơn" link="#larger-ender-chests" desc="Tối đa 54 ô, cấu hình theo bội số của chín." />

<DocCard icon="Archive" title="Hệ Thống Nhiều Rương" link="#multi-chest-system" desc="Sở hữu nhiều rương, mỗi rương quản lý từ menu trong game." />

<DocCard icon="ArrowRightLeft" title="Chuyển Dữ Liệu" link="#migration" desc="Nhập dữ liệu rương Ender vanilla sẵn có của người chơi." />

<DocCard icon="Layers" title="Hỗ Trợ Bedrock" link="#bedrock-support" desc="Menu hiển thị dưới dạng form Bedrock native qua Geyser." />

<DocCard icon="Globe" title="Đa Ngôn Ngữ" link="#localization" desc="Mọi văn bản hiển thị cho người chơi đều có thể chỉnh sửa và dịch." />

</CardGrid>

## Rương Ender Lớn Hơn {#larger-ender-chests}

EnhancedEchest thay rương Ender vanilla 27 ô bằng một kho đồ có thể cấu hình lên tới **54 ô**.

<img class="feature-shot" alt="An enhanced ender chest with 54 slots" src="https://github.com/user-attachments/assets/a1f8a60e-5f31-4a30-b91b-07c5ba9243bf" />

<CardGrid>

<FeatureCard icon="MousePointer2" title="Cùng Khối, Nhiều Không Gian Hơn">

Người chơi mở rương Ender đúng theo cách họ vẫn làm, bằng cách chuột phải vào khối rương Ender, và nhận được kho đồ lớn hơn thay vì màn hình vanilla.

- Mở bằng chuột phải hoặc qua <code>/ec</code>
- Khối rương Ender vẫn giữ hiệu ứng đóng/mở nắp
- Kích thước cấu hình theo bội số của 9, từ 9 đến 54

</FeatureCard>

<FeatureCard icon="Sliders" title="Kích Thước Tùy Chỉnh">

Kích thước mặc định cho rương đầu tiên của người chơi được đặt bằng <code>enderchest.default-size</code> trong <code>config.yml</code>. Quản trị viên cũng có thể đổi kích thước từng rương bằng <code>/ee resize</code>, và bạn có thể ghi đè kích thước rương cơ bản <strong>theo từng rank</strong> bằng quyền <code>enhancedechest.default_size.&lt;size&gt;</code>.

- Kích thước hợp lệ: <code>9</code>, <code>18</code>, <code>27</code>, <code>36</code>, <code>45</code>, <code>54</code>
- Giá trị không hợp lệ được làm tròn về kích thước gần nhất
- Mặc định là <code>54</code> (rương đôi đầy đủ)
- Ghi đè theo từng người chơi bằng quyền, xem trang <a href="/vi/docs/permission-chests#default-size-permission">Rương Theo Quyền</a>

</FeatureCard>

</CardGrid>

## Hệ Thống Nhiều Rương {#multi-chest-system}

Người chơi không còn bị giới hạn ở một rương Ender. Mỗi người chơi có thể sở hữu nhiều rương, quản lý qua menu trong game.

<figure class="feature-figure">
  <img alt="The chest list menu showing several owned ender chests" src="https://github.com/user-attachments/assets/f693c05c-7427-489b-aa41-b68f3341cda1" />
  <figcaption>Với hai rương trở lên, mở rương Ender sẽ bật lên menu liệt kê mọi rương bạn sở hữu, kèm số ô của từng rương.</figcaption>
</figure>

<CardGrid>

<FeatureCard icon="List" title="Menu Danh Sách Rương">
Chạy <code>/eclist</code> để mở menu liệt kê mọi rương người chơi sở hữu, kèm số ô của từng rương. Ô tích <strong>Chế độ chỉnh sửa</strong> thay đổi điều xảy ra khi bấm vào rương: khi tắt (mặc định) rương mở ngay; khi bật, bấm vào rương sẽ mở màn hình quản lý để đổi tên, gán biểu tượng, hoặc đặt làm rương chính. Ô tích chuyển trạng thái tại chỗ, không mở lại menu.
</FeatureCard>

<FeatureCard icon="Star" title="Rương Chính">
Với nhiều rương, người chơi có thể chọn một rương làm <strong>rương chính</strong>, rương được mở trực tiếp bằng <code>/ec</code> và bằng chuột phải vào khối rương Ender. Cho đến khi chọn rương chính, những thao tác đó sẽ mở menu quản lý. Rương mới không bao giờ tự động trở thành rương chính; người chơi tự đặt từ menu (và luôn vào menu được bằng <code>/eclist</code>).

Một ngoại lệ: khi người chơi đang có <strong>rương tạm</strong> chứa vật phẩm bị dồn (ví dụ sau khi rương bị thu nhỏ hoặc rương cấp theo rank bị thu hồi), mở rương Ender sẽ luôn hiện menu, kể cả khi đã đặt rương chính. Điều này bảo đảm người chơi thấy được số vật phẩm bị dồn. Khi lấy hết đồ, rương tạm tự biến mất và rương chính lại mở trực tiếp như thường.
</FeatureCard>

<FeatureCard icon="Palette" title="Tùy Chỉnh Từng Rương">
Cá nhân hóa rương ngay từ menu trong game, không cần lệnh:

- <strong>Đổi tên</strong>: đặt tên hiển thị cho rương, có thể tô màu
- <strong>Chọn biểu tượng</strong>: chọn vật phẩm bất kỳ để hiện trong danh sách
- <strong>Sắp xếp</strong>: gộp cụm và xếp lại theo loại chỉ với một cú bấm

Mỗi mục là một công tắc toàn máy chủ ở <code>enderchest.features</code>. Xem [Cấu hình](/vi/docs/configuration).

</FeatureCard>

<FeatureCard icon="Wrench" title="Quản Lý Bởi Quản Trị Viên">
Quản trị viên có thể thêm, đổi kích thước và xóa rương cho bất kỳ người chơi nào bằng <code>/ee add</code>, <code>/ee resize</code> và <code>/ee delete</code>. Xóa rương chính khiến người chơi không có rương chính cho đến khi họ chọn rương mới từ menu.
</FeatureCard>

<FeatureCard icon="Key" title="Rương Cấp Theo Quyền">
Phát rương theo rank thay vì bằng lệnh. Quyền <code>enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;</code> cấp ngần ấy rương với kích thước đó. Các node cộng dồn, việc cấp đồng bộ khi mở rương, và xóa một node sẽ xóa các rương đó (vật phẩm dồn sang rương tạm có thể khôi phục). Rương cơ bản của người chơi luôn được bảo vệ. Xem trang <a href="/vi/docs/permission-chests#permission-granted-chests">Rương Theo Quyền</a>.
</FeatureCard>

<FeatureCard icon="Eye" title="Xem Rương Của Người Chơi Khác">
Với <code>/ee view &lt;player&gt;</code> quản trị viên mở rương của một người chơi, dù trực tuyến hay ngoại tuyến, ngay trong menu quản lý mà chủ rương thấy. Một rương mở thẳng menu của nó; với nhiều rương, menu lựa chọn sẽ hiện ra. Cấp <code>admin.view</code> để xem chỉ-đọc, thêm <code>admin.edit</code> để lấy/thêm vật phẩm và để đổi tên, đổi biểu tượng, hoặc sắp xếp rương, và thêm <code>admin.clear</code> để có nút <strong>Dọn rương</strong> (kèm xác nhận) làm trống rương.
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

## Chuyển Dữ Liệu {#migration}

Đã có người chơi với dữ liệu rương Ender? EnhancedEchest có thể nhập từ rương Ender vanilla, AxVaults, PlayerVaultsX hoặc CustomEnderChest.

- Khi <code>migration.enabled</code> là <code>true</code>, rương Ender vanilla của người chơi chưa được chuyển sẽ được nhập tự động khi họ vào
- Quản trị viên có thể kích hoạt chuyển dữ liệu vanilla thủ công bằng <code>/ee migrate vanilla</code>
- Nhập từ AxVaults bằng <code>/ee migrate axvaults</code>, kể cả người chơi ngoại tuyến (đã kiểm thử với AxVaults 2.15.0)
- Nhập từ PlayerVaultsX bằng <code>/ee migrate playervaultsx</code>, kể cả người chơi ngoại tuyến (đã kiểm thử với PlayerVaultsX 4.4.13)
- Nhập từ CustomEnderChest bằng <code>/ee migrate customenderchest</code>, kể cả người chơi ngoại tuyến (đã kiểm thử với CustomEnderChest 2.1.2; yêu cầu lưu trữ YAML)

Xem trang [Chuyển Dữ Liệu](/vi/docs/migration) để biết chi tiết.

## Hỗ Trợ Bedrock {#bedrock-support}

Người chơi Bedrock vào qua proxy [Geyser](https://geysermc.org/) sẽ thấy menu rương hiển thị dưới dạng form Bedrock native, không cần cấu hình thêm từ phía EnhancedEchest.

- Danh sách rương, hộp thoại đổi tên và thao tác "Đặt làm rương chính" đều hiện ra dưới dạng giao diện Bedrock
- Bản thân kho đồ rương là container thông thường và hoạt động bình thường trên Bedrock

::: tip
Hãy giữ bản build Geyser cập nhật để việc chuyển đổi menu mượt mà nhất.
:::

## Đa Ngôn Ngữ {#localization}

Mọi văn bản hiển thị cho người chơi nằm trong các file ngôn ngữ có thể chỉnh sửa. Tạo bản dịch bằng cách sao chép thư mục <code>en_US</code>, dịch nó, và trỏ <code>language</code> tới locale mới. Xem trang [Ngôn Ngữ](/vi/docs/language).

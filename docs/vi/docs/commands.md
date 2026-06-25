# Lệnh

Mọi lệnh được đăng ký qua hệ thống Brigadier của Paper, nên bạn có đầy đủ tab-completion và
gợi ý đối số ngay trong game. Có hai nhóm: **lệnh người chơi** để mở và quản lý rương của chính mình,
và **lệnh quản trị viên** (`/ee`) để quản lý rương của người chơi khác.

## Lệnh người chơi

<div class="command-section">

<CommandRow commands="/enderchest" aliases="/ec" permission="enhancedechest.command.open">
Mở rương Ender của bạn. Cái gì mở ra phụ thuộc vào số rương bạn sở hữu và việc bạn đã chọn rương chính hay chưa:
<ul>
<li><strong>Một rương:</strong> mở nó trực tiếp.</li>
<li><strong>Nhiều rương, đã đặt rương chính:</strong> mở trực tiếp rương <strong>chính</strong> của bạn.</li>
<li><strong>Nhiều rương, chưa đặt rương chính:</strong> mở menu quản lý để bạn chọn một rương.</li>
</ul>
Rương đầu tiên của bạn được tạo tự động ở kích thước mặc định trong lần đầu bạn mở. Một rương mới <strong>không bao giờ</strong> tự động trở thành rương chính; bạn tự chọn nó (xem <code>/eclist</code>).
</CommandRow>

<CommandRow :commands="['/enderchest #&lt;index&gt;', '/enderchest &lt;name&gt;']" aliases="/ec" permission="enhancedechest.command.open">
Mở một rương cụ thể, bỏ qua menu. Truyền vào số thứ tự dưới dạng <code>#&lt;index&gt;</code> (ví dụ <code>/ec #2</code>) hoặc tên tùy chỉnh của nó (ví dụ <code>/ec Loot</code>). Tab-completion gợi ý các rương của chính bạn. Số thứ tự hoặc tên không xác định sẽ báo lỗi thay vì mở một rương khác.
</CommandRow>

<CommandRow commands="/eclist" permission="enhancedechest.command.open">
Luôn mở <strong>menu quản lý</strong> liệt kê mọi rương của bạn, kể cả khi bạn đã đặt rương chính. Từ đó bạn có thể, với mỗi rương: <strong>Mở</strong> nó, <strong>Đổi tên</strong> nó, hoặc <strong>Đặt làm rương chính</strong> (rương mà <code>/ec</code> và chuột phải vào khối sẽ mở trực tiếp). Rương chính được đánh dấu bằng một <strong>★</strong> màu vàng.
</CommandRow>

</div>

::: tip Chuột phải vào khối rương Ender
Chuột phải vào một khối rương Ender đã đặt sẽ mở rương của bạn với **cùng quy tắc định tuyến** như `/ec`
(một rương → mở trực tiếp; nhiều rương + đã đặt chính → rương chính; nhiều rương chưa đặt chính → menu).
Bản thân khối **không cần quyền** để dùng; chỉ quyền `enhancedechest.command.open` mới mở khóa việc mở
bằng lệnh và khả năng đặt rương chính. Do đó một người chơi **không có** quyền đó mà sở hữu nhiều rương
sẽ luôn rơi vào menu quản lý.
:::

## Lệnh quản trị viên

Mọi lệnh quản trị viên nằm dưới `/enhancedechest` (alias `/ee`). Mỗi lệnh con yêu cầu quyền cơ sở
`enhancedechest.command.admin` **cùng với** quyền riêng của từng lệnh liệt kê ở mỗi dòng bên dưới.

<div class="command-section">

<CommandRow :commands="['/ee add &lt;player&gt; &lt;size&gt; [count] [duration]']" permission="enhancedechest.admin.add">
Cấp cho người chơi một rương mới. <code>&lt;size&gt;</code> là số ô, một bội số của <code>9</code> từ <code>9</code> đến <code>54</code>. Tham số tùy chọn <code>[count]</code> tạo nhiều rương cùng lúc (mặc định là <code>1</code>). Tham số tùy chọn <code>[duration]</code> (truyền sau count, ví dụ <code>/ee add Steve 54 1 7d</code>) khiến chúng thành <strong>rương tạm</strong> hết hạn sau khoảng thời gian đó (ví dụ <code>7d</code>, <code>1h</code>, <code>1d_12h</code>); bỏ qua nó để có rương vĩnh viễn. Mỗi rương mới được thêm vào số thứ tự trống tiếp theo và không được đặt làm rương chính.
</CommandRow>

<CommandRow :commands="['/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;']" permission="enhancedechest.admin.resize">
Thay đổi số ô của một rương sẵn có. Tăng kích thước, hoặc thu nhỏ mà không có vật phẩm ở các ô bị cắt, là một lần đổi kích thước đơn giản. Thu nhỏ xuống dưới số ô đang chứa đồ sẽ <strong>dồn</strong> các vật phẩm dư sang một rương tạm thay vì hủy chúng. Rương <strong>cấp theo quyền</strong> không thể đổi kích thước theo cách này; kích thước của chúng được cố định bởi quyền cấp.
</CommandRow>

<CommandRow :commands="['/ee delete &lt;player&gt; &lt;count&gt; [force]']" permission="enhancedechest.admin.delete">
Xóa <code>&lt;count&gt;</code> rương <strong>mới nhất</strong> (số thứ tự cao nhất) mà người chơi sở hữu. Mặc định mọi vật phẩm chúng chứa sẽ được <strong>dồn</strong> sang một rương tạm để không mất gì; thêm từ khóa <code>force</code> để <strong>xóa cứng</strong> chúng, bỏ luôn nội dung ngay lập tức. <strong>Rương đầu tiên</strong> (số thứ tự nhỏ nhất) của người chơi luôn được giữ lại, nên thao tác này không bao giờ khiến họ không còn rương nào; nếu chỉ còn rương đầu tiên thì không xóa gì cả. Rương <strong>cấp theo quyền</strong> được bỏ qua hoàn toàn. Nếu bạn xóa rương chính của người chơi, họ đơn giản là không có rương chính cho đến khi chọn rương mới.
</CommandRow>

<CommandRow :commands="['/ee view &lt;player&gt; [list | index]']" permission="enhancedechest.admin.view">
Tự mình mở rương Ender của người chơi khác. Người chơi đó <strong>không</strong> cần phải trực tuyến. Bạn tham gia vào <strong>cùng một kho đồ trực tiếp</strong> mà chủ rương thấy, nên không có rủi ro nhân đôi vật phẩm.
<ul>
<li><strong>Không có đối số:</strong> nếu họ sở hữu một rương thì nó mở trực tiếp; với nhiều rương, một <strong>menu lựa chọn</strong> các rương của họ sẽ mở ra để bạn chọn.</li>
<li><strong><code>list</code></strong>: luôn mở menu lựa chọn, kể cả khi họ chỉ sở hữu một rương.</li>
<li><strong><code>&lt;index&gt;</code></strong>: mở rương cụ thể theo số (ví dụ <code>/ee view Steve 2</code>); tab-completion gợi ý các rương của mục tiêu.</li>
</ul>
Quyền: chỉ với <code>enhancedechest.admin.view</code> bạn có thể <strong>nhìn nhưng không động vào</strong>: mọi nỗ lực di chuyển vật phẩm đều bị chặn. Thêm <code>enhancedechest.admin.edit</code> để cũng <strong>lấy và thêm</strong> vật phẩm.
<br>
Trên <strong>Paper</strong>, quản trị viên và người chơi có thể chỉnh sửa cùng một rương cùng lúc. Trên <strong>Folia</strong> chỉ một người được mở một rương tại một thời điểm, nên nếu người kia đang dùng nó, bạn sẽ được yêu cầu thử lại sau giây lát.
</CommandRow>

<CommandRow commands="/ee reload" permission="enhancedechest.admin.reload">
Tải lại các file cấu hình và ngôn ngữ từ đĩa mà không cần khởi động lại máy chủ.
</CommandRow>

<CommandRow :commands="['/ee migrate run &lt;player&gt;', '/ee migrate run all']" permission="enhancedechest.admin.migrate.run">
Nhập nội dung rương Ender vanilla vào kho lưu trữ của plugin, cho một <code>&lt;player&gt;</code> đang trực tuyến hoặc cho tất cả mọi người với <code>all</code>. Rương vanilla của mỗi người chơi chỉ được chuyển vào rương&nbsp;#1 của họ đúng một lần.
</CommandRow>

</div>

::: tip Người chơi ngoại tuyến
`/ee add`, `/ee resize`, `/ee delete` và `/ee view` đều hoạt động với người chơi **ngoại tuyến** (bất kỳ ai
đã từng vào máy chủ trước đó); rương của họ nằm trong cơ sở dữ liệu, không phải trên đối tượng người chơi.
Tab-completion cũng gợi ý tên người ngoại tuyến: bắt đầu gõ và những người chơi ngoại tuyến khớp sẽ hiện
cùng với người trực tuyến (đánh dấu *Player (offline)*; danh sách bị giới hạn để một danh sách lớn không làm
tràn menu). Chỉ `/ee migrate run` yêu cầu người chơi **trực tuyến**, vì nó đọc rương Ender vanilla trực tiếp của họ.
:::

::: warning Thời lượng
Đơn vị thời lượng là `s` (giây), `m` (phút), `h` (giờ), `d` (ngày), `w` (tuần), `mo` (tháng, ≈30 ngày)
và `y` (năm, ≈365 ngày). Ghép các thành phần bằng dấu gạch dưới, ví dụ `1d_12h`, `2w`, `1mo`. Tháng và
năm là giá trị xấp xỉ.
:::

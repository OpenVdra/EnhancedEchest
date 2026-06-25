# Chào mừng đến với EnhancedEchest

**EnhancedEchest** là một plugin Minecraft miễn phí, mã nguồn mở, nâng cấp rương Ender vanilla thành một hệ thống lưu trữ lớn hơn, bền bỉ, nhiều rương. Thay vì ba hàng chật chội nằm trong một file người chơi duy nhất, mỗi người chơi nhận được các rương Ender với tối đa **54 ô**, được lưu vào một cơ sở dữ liệu thực sự để nội dung tồn tại qua các lần khởi động lại, reset thế giới và di chuyển máy chủ.

## Điều hướng nhanh

<CardGrid>

<DocCard icon="📥" title="Cài đặt" link="/vi/docs/installation" desc="Cài EnhancedEchest lên máy chủ của bạn chỉ trong vài phút." />

<DocCard icon="✨" title="Tính năng" link="/vi/docs/features" desc="Rương lớn hơn, quản lý nhiều rương, lưu bằng cơ sở dữ liệu và hơn thế nữa." />

<DocCard icon="⌨️" title="Lệnh" link="/vi/docs/commands" desc="Tham khảo đầy đủ các lệnh kèm cú pháp và mô tả." />

<DocCard icon="🔐" title="Quyền" link="/vi/docs/permissions" desc="Các node quyền cho tính năng của người chơi và quản trị viên." />

<DocCard icon="⚙️" title="Cấu hình" link="/vi/docs/configuration" desc="Cấu hình kích thước rương, cơ sở dữ liệu, chuyển dữ liệu và ngôn ngữ." />

<DocCard icon="💾" title="Cơ sở dữ liệu" link="/vi/docs/database" desc="Thiết lập lưu trữ SQLite, MySQL, MariaDB hoặc PostgreSQL." />

</CardGrid>

## Vì sao chọn EnhancedEchest?

<CardGrid>

<FeatureCard icon="📦" title="Nhiều Không Gian Hơn">
Rương Ender vanilla chứa 27 vật phẩm. EnhancedEchest cho người chơi một rương có thể cấu hình lên tới 54 ô (một rương đôi đầy đủ), mở từ chính khối rương Ender đó hoặc bằng lệnh <code>/ec</code>.
</FeatureCard>

<FeatureCard icon="🗂️" title="Nhiều Rương Cho Mỗi Người Chơi">
Người chơi không còn bị giới hạn ở một rương Ender duy nhất. Quản trị viên có thể cấp thêm rương, mỗi rương với kích thước và tên tùy chỉnh riêng, chuyển đổi được từ một menu quản lý trong game.
</FeatureCard>

<FeatureCard icon="💾" title="Lưu Trữ Bền Bỉ">
Toàn bộ nội dung được lưu trong cơ sở dữ liệu: SQLite dùng ngay không cần thiết lập, hoặc MySQL / MariaDB / PostgreSQL cho các network. Dữ liệu được chia sẻ gọn gàng qua các lần khởi động lại và, với cơ sở dữ liệu dùng chung, qua các máy chủ.
</FeatureCard>

<FeatureCard icon="🛡️" title="Chống Nhân Đôi Theo Thiết Kế">
Nội dung được tải mới khi rương được mở và ghi ngay khi đóng. Một chuỗi chờ lưu (pending-save) đảm bảo lần mở tiếp theo không bao giờ đọc dữ liệu cũ, đóng lại cánh cửa nhân đôi vật phẩm dựa trên reload.
</FeatureCard>

</CardGrid>

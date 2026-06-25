# Quyền

EnhancedEchest dùng cổng quyền Brigadier trên mọi lệnh. **Tất cả** node mặc định là `op`, nên ngay từ
đầu chỉ operator mới dùng được các lệnh. Hãy cấp các node bên dưới qua plugin quyền của bạn (LuckPerms, v.v.)
để mở chúng cho các rank khác.

## Quyền người chơi

<BaseTable :columns="['Quyền', 'Mô tả', 'Mặc định']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.command.open" defaultVal="op">
Mở rương Ender bằng lệnh (<code>/enderchest</code>, <code>/ec</code>, <code>/eclist</code>) và dùng thao tác <strong>Đặt làm rương chính</strong> trong menu. Chuột phải vào <em>khối</em> rương Ender không bao giờ cần quyền này. Một người chơi không có nó mà sở hữu nhiều rương sẽ không bao giờ đặt được rương chính, nên chuột phải vào khối luôn mở menu quản lý cho họ.
</PermRow>

<PermRow permission="enhancedechest.additional_amount.&lt;count&gt;.slot.&lt;size&gt;" defaultVal="none">
Cấp thêm rương Ender tự động. Node mã hóa cả số lượng rương lẫn số ô của chúng. Ví dụ, <code>enhancedechest.additional_amount.2.slot.54</code> cấp <strong>hai rương 54 ô</strong>. Nhiều node khớp sẽ <strong>cộng dồn</strong> (cộng theo từng kích thước). Xóa một node sẽ xóa đúng ngần ấy rương của kích thước đó; mọi vật phẩm chúng chứa sẽ dồn sang một rương tạm mà người chơi có thể khôi phục từ <code>/eclist</code>. Xem <a href="#permission-granted-chests">Rương cấp theo quyền</a> bên dưới.
</PermRow>

</BaseTable>

## Rương cấp theo quyền

Ngoài lệnh quản trị `/ee add`, bạn có thể phát rương Ender hoàn toàn qua quyền với node
`enhancedechest.additional_amount.<count>.slot.<size>`. Điều này lý tưởng cho việc gắn các đặc quyền rương
vào rank trong LuckPerms (hoặc bất kỳ plugin quyền nào).

- **`<count>`**: số rương cần cấp (một số nguyên dương).
- **`<size>`**: số ô của chúng, một bội số của `9` từ `9` đến `54`. Kích thước không hợp lệ bị bỏ qua.

Việc cấp được đồng bộ mỗi lần người chơi mở rương Ender, nên thay đổi có hiệu lực ở lần mở tiếp theo của họ,
không cần lệnh hay đăng nhập lại.

::: tip Cách việc cấp hoạt động
- **Cộng dồn, không phải lấy cao nhất.** Mọi node khớp đều được cộng lại, nhóm theo kích thước. Cấp cả
  `enhancedechest.additional_amount.1.slot.9` và `enhancedechest.additional_amount.2.slot.9` sẽ cho người chơi
  **ba** rương 9 ô.
- **Mất một node sẽ xóa đúng những rương đó.** Hạ rank và các rương khớp bị xóa lại; nếu chúng chứa vật phẩm,
  những vật phẩm đó dồn sang một rương tạm khôi phục được từ `/eclist`, nên không có gì bị hủy.
- **Rương cơ bản được bảo vệ.** Mỗi người chơi luôn giữ ít nhất một rương thường. Quyền không bao giờ tạo,
  xóa hay ghi đè nó.
- **Chúng hành xử như rương thường.** Rương được cấp có thể mở, đổi tên, gán biểu tượng và đặt làm rương chính;
  chúng không mang nhãn hay hạn chế đặc biệt nào. Chỉ lệnh quản trị bỏ qua chúng:
  `/ee resize` và `/ee delete` sẽ không chỉnh sửa một rương cấp theo quyền.
:::

::: warning Tắt tính năng
Việc cấp theo quyền chỉ được áp dụng khi `permission-chests.enabled` là `true` trong `config.yml`. Tắt nó
sẽ dừng đồng bộ nhưng giữ nguyên các rương đã cấp (chúng tiếp tục hành xử như rương thường). Xem trang
[Cấu hình](/vi/docs/configuration).
:::

## Quyền quản trị viên

Lệnh quản trị dùng mô hình **hai khóa**: mỗi lệnh con `/ee` kiểm tra node cơ sở
`enhancedechest.command.admin` **cùng với** node riêng của nó bên dưới. Chỉ cấp node riêng thôi là không đủ:
người chơi còn cần `enhancedechest.command.admin`. Không có sự kế thừa giữa chúng.

<BaseTable :columns="['Quyền', 'Mô tả', 'Mặc định']" grid="2fr 3fr 0.6fr">

<PermRow permission="enhancedechest.command.admin" defaultVal="op">
Quyền cơ sở bắt buộc cho <strong>mọi</strong> lệnh quản trị <code>/enhancedechest</code> (<code>/ee</code>), cùng với node riêng của từng lệnh.
</PermRow>

<PermRow permission="enhancedechest.admin.add" defaultVal="op">
Dùng <code>/ee add &lt;player&gt; &lt;size&gt; [duration]</code> để cấp cho người chơi một rương mới (tùy chọn tạm thời).
</PermRow>

<PermRow permission="enhancedechest.admin.resize" defaultVal="op">
Dùng <code>/ee resize &lt;player&gt; &lt;index&gt; &lt;size&gt;</code> để thay đổi số ô của một rương (dồn phần dư khi thu nhỏ).
</PermRow>

<PermRow permission="enhancedechest.admin.delete" defaultVal="op">
Dùng <code>/ee delete &lt;player&gt; &lt;count&gt; [force]</code> để xóa các rương mới nhất (dồn vật phẩm của chúng, hoặc xóa cứng với <code>force</code>); rương đầu tiên của người chơi luôn được giữ lại.
</PermRow>

<PermRow permission="enhancedechest.admin.view" defaultVal="op">
Dùng <code>/ee view &lt;player&gt; [index]</code> để mở rương của người chơi khác. <strong>Chỉ đọc</strong> nếu cấp một mình: bạn thấy nội dung nhưng không di chuyển được vật phẩm.
</PermRow>

<PermRow permission="enhancedechest.admin.edit" defaultVal="op">
Cấp <em>thêm vào trên</em> <code>enhancedechest.admin.view</code>, cho phép bạn <strong>lấy và thêm</strong> vật phẩm khi xem rương của người chơi khác. Không có nó, <code>/ee view</code> chỉ là xem.
</PermRow>

<PermRow permission="enhancedechest.admin.reload" defaultVal="op">
Dùng <code>/ee reload</code> để tải lại các file cấu hình và ngôn ngữ từ đĩa.
</PermRow>

<PermRow permission="enhancedechest.admin.migrate.run" defaultVal="op">
Dùng <code>/ee migrate run &lt;player&gt;</code> và <code>/ee migrate run all</code> để nhập nội dung rương Ender vanilla.
</PermRow>

</BaseTable>

::: tip Cấp quyền quản trị một lần
Để cho một moderator toàn quyền quản trị, hãy cấp cả `enhancedechest.command.admin` lẫn các node riêng họ
cần (hoặc một wildcard như `enhancedechest.admin.*` nếu plugin quyền của bạn mở rộng nó, nhớ rằng họ
**vẫn** cần node cơ sở `enhancedechest.command.admin`).
:::

# <img src="https://skillicons.dev/icons?i=sqlite" width="28" height="28" alt="SQLite" style="display:inline-block;vertical-align:middle;margin:0 6px 0 0" /> SQLite

SQLite không cần cấu hình gì. Plugin tự tạo file cơ sở dữ liệu tại `plugins/EnhancedEchest/enderchests.db` khi khởi động lần đầu.

::: tip Xem cơ sở dữ liệu ngay trong trang tài liệu
Dùng [SQLite Viewer](/vi/docs/sqlite-viewer) tích hợp sẵn để mở cơ sở dữ liệu EnhancedEchest và xem hoặc chỉnh sửa các bảng, metadata người chơi và metadata rương ngay trong trình duyệt. File luôn nằm trên thiết bị của bạn; thay đổi chỉ tác động đến bản sao trong bộ nhớ cho tới khi bạn tải file xuống. Hãy dùng bản sao lưu tự động hoặc bản sao được tạo khi máy chủ đã dừng, thay vì tự sao chép file cơ sở dữ liệu đang hoạt động.
:::

**Phù hợp cho:** máy chủ đơn và cộng đồng nhỏ đến vừa (khoảng vài trăm người chơi cùng lúc trở xuống). Chỉ một file, không cần cài hay bảo trì gì thêm.
**Không phù hợp nếu:** bạn chạy nhiều máy chủ sau proxy cần chia sẻ dữ liệu người chơi, vì một file chỉ thuộc về một máy chủ. Hãy dùng [MySQL / MariaDB](/vi/docs/mysql-mariadb) hoặc [PostgreSQL](/vi/docs/postgresql) để hỗ trợ [Liên Máy Chủ](/vi/docs/cross-server).

Tài liệu: [sqlite.org](https://www.sqlite.org/docs.html)

```yaml
database:
  type: sqlite
  sqlite-file: enderchests.db
```

::: info Các file phụ cạnh cơ sở dữ liệu
SQLite chạy ở chế độ write-ahead logging (WAL) để có hiệu năng tốt hơn khi đông người, nên bạn có thể thấy thêm `enderchests.db-wal` và `enderchests.db-shm` cạnh file cơ sở dữ liệu. Đó là file của SQLite: cứ để nguyên, và đừng bao giờ tự copy file `.db` bằng tay khi máy chủ đang chạy (hãy dùng [sao lưu tự động](/vi/docs/configuration) tích hợp sẵn).
:::

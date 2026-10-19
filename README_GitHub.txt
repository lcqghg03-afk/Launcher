TV Launcher - mã nguồn cập nhật

Cách build trên GitHub:
1. Giải nén toàn bộ thư mục TVLauncher và đưa nội dung thư mục lên repository (giữ nguyên cấu trúc).
2. Mở tab Actions, chạy workflow “Build TV Launcher APK” hoặc push lên nhánh main/master.
3. Mở lượt chạy thành công và tải artifact TVLauncher-debug-apk.

Lưu ý: workflow tạo APK debug để thử nghiệm. Mã nguồn có các tuỳ chọn giao diện/phông chữ/avatar và bảng cấu hình con trỏ; chức năng chuột ảo di chuyển con trỏ thực tế chưa được nối vào dịch vụ điều khiển, nên không được hiểu là đã hoàn tất tính năng chuột ảo. QR ủng hộ không có trong tệp người dùng gửi nên đã để dòng nhắc thêm ảnh QR thay vì tạo QR giả.

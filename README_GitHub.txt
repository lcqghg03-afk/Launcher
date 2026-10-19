TV Launcher 1.8 — bản sửa cấu hình Gradle và workflow build Release

CÁCH BUILD APK TRÊN GITHUB
1. Giải nén ZIP này.
2. Mở thư mục TVLauncher và tải TOÀN BỘ NỘI DUNG bên trong thư mục đó lên thư mục gốc repository GitHub (không tải thư mục TVLauncher lồng thêm một tầng).
3. Đảm bảo có đường dẫn .github/workflows/android.yml ở gốc repository.
4. Mở Actions → Build TV Launcher APK → Run workflow.
5. Khi workflow hoàn tất thành công, vào phần Artifacts tải TVLauncher-release-apk.

THAY ĐỔI
- Cài đặt được chia thành nhóm Hình nền, Giao diện, Màn hình chính, Điều khiển, Ứng dụng và Hệ thống thay vì xếp ABC toàn bộ.
- Giao diện Cài đặt có tiêu đề Premium Glass và tiêu đề nhóm màu teal.
- Màn hình chính có lớp phủ gradient nhẹ giúp chữ dễ đọc hơn trên ảnh nền.
- Hình nền hỗ trợ danh sách nhiều ảnh, tự đổi theo số giây cài đặt, và chuyển ảnh mờ dần.
- Đã bỏ cấu hình ký release.jks cục bộ không có trong ZIP để tránh làm hỏng build GitHub Actions. APK Release được ký bằng debug key để có thể cài thử trên TV; không dùng khóa này để phát hành chính thức lên Google Play.

LƯU Ý
Chưa thể xác nhận build thành công từ môi trường hiện tại vì không có Gradle/Android SDK cục bộ. Sau khi tải lên GitHub, hãy xem log Actions nếu còn lỗi và gửi log đó để sửa chính xác.
\nSỬA LỖI GRADLE\n- settings.gradle.kts khai báo include(":app") để Gradle nhận diện module app.\n- Workflow chạy :app:assembleRelease và tải artifact từ app/build/outputs/apk/release/.\n
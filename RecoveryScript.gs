// Google Apps Script: script.google.com > Dự án mới > dán code > Triển khai > Ứng dụng web
// (Thực thi bằng: Tôi, Quyền truy cập: Bất kỳ ai) > sao chép URL dán vào RECOVERY_URL trong SettingsActivity.kt
function doGet(e) {
  var to = e.parameter.to, code = e.parameter.code;
  if (!to || !/^\d{6}$/.test(code)) return ContentService.createTextOutput("err");
  MailApp.sendEmail(to, "Mã khôi phục Khoá ứng dụng - TV Launcher",
    "Mã của bạn: " + code + "\nMã có hiệu lực trong 10 phút.");
  return ContentService.createTextOutput("ok");
}

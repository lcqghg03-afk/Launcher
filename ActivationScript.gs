// Google Apps Script kiểm tra key kích hoạt TV Launcher.
// CÁCH DÙNG:
// 1. Tạo Google Sheet mới, đặt tên tab là: Keys
//    Hàng 1 (tiêu đề): A = Mã máy | B = Key | C = Đã dùng (ô tích) | D = Ngày dùng | E = Ghi chú
// 2. Trong Sheet: Tiện ích mở rộng > Apps Script, dán toàn bộ file này, lưu.
// 3. Triển khai > Triển khai mới > Loại: Ứng dụng web
//    Thực thi bằng: Tôi | Ai có quyền truy cập: Bất kỳ ai > Triển khai
// 4. Chép "URL ứng dụng web" dán vào ACT_URL trong Activation.kt rồi build lại APK.
//
// CẤP KEY: khách gửi MÃ MÁY (hiện trên màn hình khoá) -> dán vào cột A -> menu "TV Launcher" > "Tạo key cho các dòng chưa có"
// -> gửi key ở cột B cho khách. Mỗi key chỉ dùng được 1 lần cho đúng TV đó.
// MỞ KHOÁ LẠI (khách xoá dữ liệu): bỏ tích ô ở cột C của dòng đó, khách nhập lại key.
// THU HỒI: xoá dòng của TV đó.

const SHEET_NAME = 'Keys';

function out(s) { return ContentService.createTextOutput(s); }

function doGet(e) {
  const p = (e && e.parameter) || {};
  if (p.act !== '1') return out('err');
  const id = String(p.id || '').trim().toUpperCase();
  const key = String(p.key || '').trim().toUpperCase();
  if (!id || !key) return out('bad');
  const lock = LockService.getScriptLock();
  lock.waitLock(10000);
  try {
    const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_NAME);
    const rows = sh.getDataRange().getValues();
    for (let i = 1; i < rows.length; i++) {
      if (String(rows[i][0]).trim().toUpperCase() !== id) continue;
      if (String(rows[i][1]).trim().toUpperCase() !== key) return out('bad');
      const used = rows[i][2] === true || String(rows[i][2]).toUpperCase() === 'TRUE';
      if (used) return out('used');
      sh.getRange(i + 1, 3).setValue(true);
      sh.getRange(i + 1, 4).setValue(new Date());
      return out('ok');
    }
    return out('bad');
  } finally { lock.releaseLock(); }
}

function onOpen() {
  SpreadsheetApp.getUi().createMenu('TV Launcher').addItem('Tạo key cho các dòng chưa có', 'genKeys').addToUi();
}

function genKeys() {
  const sh = SpreadsheetApp.getActive().getSheetByName(SHEET_NAME);
  const last = sh.getLastRow();
  if (last < 2) return;
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  for (let r = 2; r <= last; r++) {
    if (sh.getRange(r, 1).getValue() && !sh.getRange(r, 2).getValue()) {
      let k = ''; for (let i = 0; i < 10; i++) k += chars[Math.floor(Math.random() * chars.length)];
      sh.getRange(r, 2).setValue(k.slice(0, 5) + '-' + k.slice(5));
      sh.getRange(r, 3).insertCheckboxes();
    }
  }
}

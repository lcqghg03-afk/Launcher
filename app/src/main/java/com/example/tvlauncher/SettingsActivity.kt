package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import android.os.Handler
import android.os.Looper
import java.net.URL
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.concurrent.thread

const val RECOVERY_URL = "https://script.google.com/macros/s/AKfycbxsslXTbvK6TalPPg_tZp27CspaFKqTn6voyzCGxL9YViJQY6a32k7CwpPvoIH36Xk/exec"

private fun toast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_LONG).show()
private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
private fun numField(c: Context, h: String) = EditText(c).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = h }
private fun mailField(c: Context, v: String?) = EditText(c).apply { inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT; hint = "Gmail khôi phục"; setText(v ?: "") }
private fun validMail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
private fun column(c: Context, vararg v: android.view.View) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(c.dp(24), c.dp(8), c.dp(24), 0); v.forEach { addView(it) } }

/** Khoá ứng dụng: có PIN thì mở cài đặt / xoá / mở ứng dụng đã khoá phải nhập PIN */
fun guard(c: Context, ok: () -> Unit) {
    val pin = Apps.str(c, "pin")
    if (pin == null) { ok(); return }
    val et = numField(c, "Nhập mã PIN")
    AlertDialog.Builder(c).setTitle("Khoá ứng dụng").setView(column(c, et))
        .setPositiveButton("OK") { _, _ -> if (sha(et.text.toString()) == pin) ok() else toast(c, "Sai mã PIN") }
        .setNeutralButton("Quên mã?") { _, _ -> recoverPin(c) { ok() } }
        .setNegativeButton("Huỷ", null).show()
}

/** Bật khoá: đặt PIN 4 số + Gmail khôi phục. askMail=false khi chỉ đặt lại PIN */
fun setupLock(c: Context, askMail: Boolean, done: () -> Unit) {
    val pin = numField(c, "Đặt PIN 4 số"); val mail = mailField(c, null)
    AlertDialog.Builder(c).setTitle("Bật khoá ứng dụng").setView(if (askMail) column(c, pin, mail) else column(c, pin))
        .setPositiveButton("Lưu") { _, _ ->
            if (pin.text.length != 4) toast(c, "PIN phải đủ 4 số")
            else if (askMail && !validMail(mail.text.toString().trim())) toast(c, "Gmail không hợp lệ")
            else { Apps.putStr(c, "pin", sha(pin.text.toString())); if (askMail) Apps.putStr(c, "pin_mail", mail.text.toString().trim()); done() }
        }.setNegativeButton("Huỷ", null).show()
}

/** Quên PIN: gửi mã 6 số tới Gmail đã đăng ký, nhập đúng mã thì đặt lại PIN */
fun recoverPin(c: Context, done: () -> Unit) {
    val mail = Apps.str(c, "pin_mail")
    if (mail == null || RECOVERY_URL.isEmpty()) { toast(c, "Chưa có Gmail khôi phục hoặc chưa cấu hình máy chủ gửi mã"); return }
    val code = (100000..999999).random().toString()
    toast(c, "Đang gửi mã tới $mail…")
    thread {
        val ok = try { httpGet("$RECOVERY_URL?to=${URLEncoder.encode(mail, "UTF-8")}&code=$code").contains("ok") } catch (e: Exception) { false }
        Handler(Looper.getMainLooper()).post {
            if (!ok) { toast(c, "Không gửi được mã. Kiểm tra mạng"); return@post }
            Apps.putStr(c, "rc_hash", sha(code)); Apps.putStr(c, "rc_exp", (System.currentTimeMillis() + 600000).toString())
            val et = numField(c, "Mã 6 số")
            AlertDialog.Builder(c).setTitle("Nhập mã đã gửi tới $mail").setView(column(c, et))
                .setPositiveButton("Xác nhận") { _, _ ->
                    val valid = sha(et.text.toString()) == Apps.str(c, "rc_hash") && System.currentTimeMillis() < (Apps.str(c, "rc_exp")?.toLong() ?: 0L)
                    Apps.del(c, "rc_hash", "rc_exp")
                    if (valid) setupLock(c, false, done) else toast(c, "Mã sai hoặc đã hết hạn")
                }.setNegativeButton("Huỷ", null).show()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (Apps.bool(c, "boot", false)) c.startActivity(Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

class SettingsActivity : VActivity() {
    private lateinit var list: LinearLayout
    private var pendingVideo = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(120), dp(20), dp(120), dp(40)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF101018.toInt()); addView(list) })
        build()
    }

    private fun build() {
        list.removeAllViews()
        list.addView(TextView(this).apply { text = "Cài đặt Home TV"; textSize = 24f; setTextColor(0xFF4D6BFF.toInt()); setPadding(dp(24), 0, 0, dp(12)) })
        item("Giới thiệu", "v1.2", false, null, null) { showAbout() }
        item("Hướng dẫn cho TV Xiaomi nội địa", null, false, null, null) { xiaomiHelp() }
        val dm = android.util.DisplayMetrics(); windowManager.defaultDisplay.getRealMetrics(dm)
        item("Độ phân giải màn hình", "${dm.widthPixels}x${dm.heightPixels}", false, null, null) { toast(this, "Đây là độ phân giải TV đang xuất ra. Nếu chữ quá to hoặc quá nhỏ, hãy chọn Kích cỡ TV bên dưới.") }
        choice("Kích cỡ TV (mốc chuẩn: 55 inch)", "tvsize", TV_SIZES, 0, true)
        choice("Tinh chỉnh độ lớn giao diện", "tvfine", FINE_LABELS, 2, true)
        item("Chuyển đầu vào (HDMI/AV)", null, false, null, null) { pickInput(this) }
        item("Cài đặt hệ thống", null, false, null, null) { openSystemSettings(this) }
        val vp = Apps.str(this, "voice_pkg")
        item("Trợ lý giọng nói", vp?.let { p -> Apps.loadAll(this).firstOrNull { it.pkg == p }?.label } ?: "Tự động", false, null, { Apps.del(this, "voice_pkg") }) { chooseVoice() }
        choice("Chiều cao danh mục", "cat_h", arrayOf("Thấp", "Vừa", "Cao"), 1)
        choice("Bo góc ứng dụng", "radius", arrayOf("Vuông", "Vừa", "Tròn"), 1)
        toggle("Khởi động cùng thiết bị", "boot", false)
        choice("Số cột ứng dụng", "cols", arrayOf("4", "5", "6", "7", "8"), 2)
        val clear = { Apps.del(this, "bg_uri", "bg_type") }
        item("Thay đổi ảnh nền", bgLabel("image"), false, null, clear) { pick("image/*", 1) }
        item("Đặt video làm ảnh nền", bgLabel("video"), false, null, clear) { pick("video/*", 2) }
        choice("Độ phân giải video nền", "res", arrayOf("Gốc", "Full HD (1080p)", "2K (1440p)", "4K (2160p)"), 0)
        choice("Thay đổi màu sắc", "color", arrayOf("Mặc định", "Xanh dương", "Tím", "Đỏ", "Xanh lá"), 0)
        choice("Múi giờ", "tz", arrayOf("Theo hệ thống TV", "Việt Nam (GMT+7)", "UTC", "Nhật Bản (GMT+9)", "New York", "Trung Quốc (GMT+8)"), 1)
        toggle("Hiển thị lịch âm", "lunar", true)
        item("Hiển thị tiện ích thời tiết", null, false, !Apps.isHidden(this, "@weather"), { Apps.setHidden(this, "@weather", false) }) {
            Apps.setHidden(this, "@weather", !Apps.isHidden(this, "@weather")); build() }
        item("Hiển thị hàng Yêu thích", null, false, !Apps.isHidden(this, "@favs"), { Apps.setHidden(this, "@favs", false) }) {
            Apps.setHidden(this, "@favs", !Apps.isHidden(this, "@favs")); build() }
        item("Hiển thị hàng Đầu vào (HDMI, VGA...)", null, false, !Apps.isHidden(this, "@inputs"), { Apps.setHidden(this, "@inputs", false) }) {
            Apps.setHidden(this, "@inputs", !Apps.isHidden(this, "@inputs")); build() }
        item("Vị trí hàng Đầu vào", if (Apps.inputsAbove(this)) "Trên Yêu thích" else "Dưới Yêu thích", false, null, { Apps.setInputsAbove(this, false) }) {
            Apps.setInputsAbove(this, !Apps.inputsAbove(this)); build() }
        toggle("Cho phép chọn tiện ích", "pick", true)
        item("Thiết lập lại tiện ích", null, false, null, null) {
            listOf("@weather", "@clock", "@favs", "@inputs").forEach { Apps.setHidden(this, it, false); Apps.del(this, "size_$it") }
            Apps.setInputsAbove(this, false)
            Toast.makeText(this, "Đã thiết lập lại", Toast.LENGTH_SHORT).show(); build() }
        choice("Kích thước ứng dụng", "appsize", arrayOf("Nhỏ", "Vừa", "Lớn"), 1)
        item("Dịch vụ trợ năng", enabledServices().size.let { if (it == 0) "Tắt" else "Bật ($it)" }, false, null, null) { accessibilityDialog() }
        choice("Cỡ chữ", "fontsz", arrayOf("Vừa", "Lớn", "Rất lớn"), 0)
        toggle("Tương phản cao", "hc", false)
        toggle("Giảm chuyển động (nên bật cho TV yếu)", "rm", isXiaomi())
        toggle("Đọc tên ứng dụng (TTS)", "tts", false)
        item("Cài app không rõ nguồn gốc", null, false, null, null) { guard(this) { unknownDialog() } }
        item("Kho ứng dụng (DL Store, v.v.)", null, false, null, null) { guard(this) { storesDialog() } }
        item("Quyền ứng dụng", null, false, null, null) { permissionDialog() }
        item("Khoá ứng dụng", if (Apps.str(this, "pin") == null) "Tắt" else "Đang bật", true, null, null) { lockDialog() }
        list.addView(TextView(this).apply { text = "(Nhấn giữ nút OK để về mặc định)"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) })
    }

    private fun item(title: String, value: String?, red: Boolean, checked: Boolean?, reset: (() -> Unit)?, click: () -> Unit) {
        list.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(TextView(context).apply { text = title; textSize = 20f * Apps.fs(this@SettingsActivity); setTextColor(if (red) 0xFFFF4444.toInt() else Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
            if (value != null) addView(TextView(context).apply { text = value; textSize = 18f; setTextColor(0xFFBBBBBB.toInt()) })
            if (checked != null) addView(Switch(context).apply { isChecked = checked; isClickable = false; isFocusable = false })
            setOnFocusChangeListener { v, f -> v.background = if (f) GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(0x55FFFFFF) } else null }
            setOnClickListener { click() }
            if (reset != null) setOnLongClickListener { reset(); Toast.makeText(context, "Đã về mặc định", Toast.LENGTH_SHORT).show(); build(); true }
        })
    }

    private fun choice(title: String, key: String, labels: Array<String>, def: Int, rc: Boolean = false) {
        item(title, labels[Apps.int(this, key, def)], false, null, { Apps.del(this, key); if (rc) recreate() }) {
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels, Apps.int(this, key, def)) { d, i -> Apps.putInt(this, key, i); d.dismiss(); if (rc) recreate() else build() }.show()
        }
    }

    private fun toggle(title: String, key: String, def: Boolean) {
        item(title, null, false, Apps.bool(this, key, def), { Apps.del(this, key) }) { Apps.putBool(this, key, !Apps.bool(this, key, def)); build() }
    }

    private fun bgLabel(type: String): String {
        val u = Apps.str(this, "bg_uri")
        if (u == null || Apps.str(this, "bg_type") != type) return "Chưa đặt"
        if (type == "image") return "Đã đặt"
        return try {
            val r = MediaMetadataRetriever().apply { setDataSource(this@SettingsActivity, Uri.parse(u)) }
            "Đã đặt (${r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)}x${r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)})"
        } catch (e: Exception) { "Đã đặt" }
    }

    // Thử trình chọn tệp của hệ thống; TV Xiaomi nội địa thường không có nên dùng trình duyệt tệp tích hợp
    private fun pick(mime: String, rc: Int) {
        val i1 = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val i2 = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime)
        AlertDialog.Builder(this).setTitle("Chọn tệp từ đâu?").setItems(arrayOf("Trình chọn tệp của TV", "Duyệt bộ nhớ / USB (khuyên dùng cho TV Xiaomi)")) { _, i ->
            if (i == 1) { browse(rc == 2); return@setItems }
            try { startActivityForResult(i1, rc) } catch (e: Exception) {
                try { startActivityForResult(i2, rc) } catch (e2: Exception) { toast(this, "TV không có trình chọn tệp, chuyển sang duyệt bộ nhớ"); browse(rc == 2) }
            }
        }.show()
    }

    private fun browse(video: Boolean) {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingVideo = video; requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 77); return
        }
        showDir(null, video)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) {
        if (rc == 77) { if (r.isNotEmpty() && r[0] == PackageManager.PERMISSION_GRANTED) showDir(null, pendingVideo) else toast(this, "Cần cấp quyền đọc bộ nhớ để chọn tệp") }
    }

    private fun roots(): List<File> {
        val l = mutableListOf(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()?.forEach { if (it.isDirectory && it.name != "emulated" && it.name != "self" && it.canRead()) l.add(it) }
        return l
    }

    private fun showDir(dir: File?, video: Boolean) {
        val exts = if (video) setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "ts") else setOf("jpg", "jpeg", "png", "webp", "bmp")
        val entries: List<File>
        val labels: List<String>
        if (dir == null) {
            entries = roots()
            labels = entries.map { if (it == Environment.getExternalStorageDirectory()) "Bộ nhớ TV" else "USB / thẻ nhớ: ${it.name}" }
        } else {
            entries = (dir.listFiles() ?: emptyArray<File>()).filter { it.isDirectory || it.extension.lowercase() in exts }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            labels = entries.map { if (it.isDirectory) "[Thư mục] ${it.name}" else it.name }
        }
        val up = dir != null
        val items = (if (up) listOf("« Quay lại") else emptyList<String>()) + labels
        AlertDialog.Builder(this).setTitle(dir?.path ?: "Chọn nơi lưu trữ").setItems(items.toTypedArray()) { _, i ->
            if (up && i == 0) {
                val parent = dir!!.parentFile
                showDir(if (parent == null || roots().any { it == dir }) null else parent, video)
            } else {
                val f = entries[i - (if (up) 1 else 0)]
                if (f.isDirectory) showDir(f, video) else {
                    Apps.putStr(this, "bg_uri", Uri.fromFile(f).toString()); Apps.putStr(this, "bg_type", if (video) "video" else "image"); build()
                }
            }
        }.setNegativeButton("Đóng", null).show()
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (res != RESULT_OK) return
        val u = d?.data ?: return
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        Apps.putStr(this, "bg_uri", u.toString()); Apps.putStr(this, "bg_type", if (rc == 2) "video" else "image"); build()
    }

    private fun xiaomiHelp() {
        AlertDialog.Builder(this).setTitle("Hướng dẫn cho TV Xiaomi nội địa").setMessage(
            "• Phím Menu (≡) trên điều khiển mở nhanh cài đặt của Home TV.\n" +
            "• Nút Home của Xiaomi có thể vẫn về giao diện gốc (PatchWall). Hãy mở TV Launcher từ danh sách ứng dụng.\n" +
            "• Muốn nút Home mở thẳng TV Launcher: vào Cài đặt hệ thống tìm mục ứng dụng mặc định / màn hình chính (nếu TV có). " +
            "Nếu không có, có thể đặt bằng ADB: adb shell cmd package set-home-activity com.example.tvlauncher/.MainActivity\n" +
            "• Giao diện luôn hiển thị tiếng Việt dù TV đang để tiếng Trung.\n" +
            "• Chọn đúng Kích cỡ TV (32, 43, 55, 65, 75, 85 inch...) để chữ và biểu tượng vừa mắt; chưa vừa thì dùng Tinh chỉnh độ lớn.\n" +
            "• Nếu TV yếu, giữ bật \"Giảm chuyển động\" và dùng ảnh nền thay vì video 4K.\n" +
            "• Nếu không chọn được ảnh/video nền, dùng \"Duyệt bộ nhớ / USB\".").setPositiveButton("Đóng", null).show()
    }

    private fun chooseVoice() {
        val apps = Apps.loadAll(this)
        val names = arrayOf("Tự động (Kiki hoặc Google Assistant)") + apps.map { it.label }
        AlertDialog.Builder(this).setTitle("Trợ lý giọng nói").setItems(names) { _, i ->
            if (i == 0) Apps.del(this, "voice_pkg") else Apps.putStr(this, "voice_pkg", apps[i - 1].pkg); build() }.show()
    }

    private fun enabledServices(): List<String> = try {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .map { it.resolveInfo.loadLabel(packageManager).toString() }
    } catch (e: Exception) { emptyList() }

    private fun accessibilityDialog() {
        val l = enabledServices()
        AlertDialog.Builder(this).setTitle("Dịch vụ trợ năng")
            .setMessage(if (l.isEmpty()) "Chưa bật dịch vụ trợ năng nào." else "Đang bật:\n" + l.joinToString("\n") { "• $it" })
            .setPositiveButton("Mở cài đặt trợ năng") { _, _ ->
                if (!safeStart(this, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) toast(this, "TV không có màn hình trợ năng") }
            .setNegativeButton("Đóng", null).show()
    }

    private fun permissionDialog() {
        val overlay = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(this) else true
        val b = AlertDialog.Builder(this).setTitle("Quyền ứng dụng")
            .setMessage("• Truy cập Internet (thời tiết, gửi mã Gmail)\n• Xem danh sách ứng dụng đã cài\n• Gỡ cài đặt ứng dụng\n• Khởi động cùng thiết bị\n• Đọc bộ nhớ/USB (khi chọn ảnh, video nền)\n• Hiển thị trên ứng dụng khác (giúp tự khởi động ổn định): " +
                (if (overlay) "đã cấp" else "chưa cấp"))
            .setNeutralButton("Cài đặt quyền") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e: Exception) { toast(this, "TV không mở được màn hình này") } }
            .setNegativeButton("Đóng", null)
        if (!overlay) b.setPositiveButton("Cấp quyền hiển thị") { _, _ ->
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) { toast(this, "TV không mở được màn hình này") } }
        b.show()
    }

    private fun isStore(a: AppItem) = a.pkg == "com.android.vending" ||
        listOf("store", "market", "aptoide", "apk", "cửa hàng", "chợ").any { a.label.contains(it, true) }

    private fun openSettings(i: Intent) {
        try { startActivity(i) } catch (e: Exception) {
            try { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } catch (e2: Exception) { toast(this, "TV không mở được màn hình này") } }
    }

    // Màn hình hệ thống "Cài app không rõ nguồn gốc" cho đúng một ứng dụng
    private fun unknownSources(pkg: String) =
        openSettings(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$pkg")))

    private fun pickApp(title: String, apps: List<AppItem>, onPick: (AppItem) -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setItems(apps.map { it.label }.toTypedArray()) { _, i -> onPick(apps[i]) }.show()
    }

    private fun unknownDialog() {
        AlertDialog.Builder(this).setTitle("Cài app không rõ nguồn gốc")
            .setItems(arrayOf("Cho phép Home TV cài app", "Cho phép cho app khác…", "Mở cài đặt bảo mật hệ thống")) { _, i ->
                when (i) {
                    0 -> unknownSources(packageName)
                    1 -> pickApp("Cho phép cài từ…", Apps.loadAll(this)) { unknownSources(it.pkg) }
                    2 -> openSettings(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
            }.show()
    }

    private fun storesDialog() {
        val all = Apps.loadAll(this)
        val stores = all.filter { isStore(it) }
        val names = (stores.map { it.label } + "Chọn app khác…").toTypedArray()
        AlertDialog.Builder(this).setTitle(if (stores.isEmpty()) "Chưa thấy kho ứng dụng nào" else "Kho ứng dụng").setItems(names) { _, i ->
            if (i < stores.size) storeMenu(stores[i]) else pickApp("Chọn kho ứng dụng", all) { storeMenu(it) }
        }.show()
    }

    private fun storeMenu(a: AppItem) {
        AlertDialog.Builder(this).setTitle(a.label)
            .setItems(arrayOf("Mở ứng dụng", "Cho phép cài app không rõ nguồn từ app này", "Thông tin ứng dụng")) { _, i ->
                when (i) {
                    0 -> Apps.open(this, a.pkg)
                    1 -> unknownSources(a.pkg)
                    2 -> openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                }
            }.show()
    }

    private fun showAbout() {
        fun t(txt: String, sz: Float, col: Int = Color.WHITE) = TextView(this).apply { text = txt; textSize = sz; setTextColor(col); setPadding(0, dp(4), 0, dp(4)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(t("TV Launcher - Phiên bản 1.2", 18f))
            addView(t("Liên hệ tác giả", 16f, 0xFF4D6BFF.toInt()))
            addView(t("Facebook: Lộc Hưng", 16f))
            addView(t("Zalo: 0349353193", 16f))
            addView(t("Ủng hộ tác giả", 16f, 0xFF4D6BFF.toInt()))
            addView(ImageView(context).apply { setImageResource(R.drawable.qr_donate); adjustViewBounds = true }, LinearLayout.LayoutParams(dp(300), -2))
            addView(t("Cảm ơn bạn đã sử dụng!", 14f))
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { setBackgroundColor(0xFF1C1C24.toInt()); addView(box) }).setPositiveButton("Đóng", null).show()
    }

    private fun lockDialog() {
        if (Apps.str(this, "pin") == null) { setupLock(this, true) { build() }; return }
        AlertDialog.Builder(this).setTitle("Khoá ứng dụng").setItems(arrayOf("Đổi Gmail khôi phục", "Tắt khoá ứng dụng")) { _, i ->
            guard(this) {
                if (i == 1) { Apps.del(this, "pin", "pin_mail", "locked"); build() }
                else {
                    val mail = mailField(this, Apps.str(this, "pin_mail"))
                    AlertDialog.Builder(this).setTitle("Gmail khôi phục").setView(column(this, mail)).setPositiveButton("Lưu") { _, _ ->
                        if (validMail(mail.text.toString().trim())) Apps.putStr(this, "pin_mail", mail.text.toString().trim()) else toast(this, "Gmail không hợp lệ") }
                        .setNegativeButton("Huỷ", null).show()
                }
            }
        }.show()
    }
}

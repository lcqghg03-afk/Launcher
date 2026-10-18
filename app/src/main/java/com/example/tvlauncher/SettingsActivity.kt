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
import android.text.InputFilter
import android.text.InputType
import android.view.View
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
/** Độ dài mã PIN đang dùng: 4 hoặc 8 số (bản cũ chưa lưu thì mặc định 4) */
private fun pinLen(c: Context) = if (Apps.str(c, "pin_len") == "8") 8 else 4
/** Ô nhập số. max > 0: chỉ cho gõ tối đa max số, gõ thêm sẽ không nhận */
private fun numField(c: Context, h: String, max: Int = 0) = EditText(c).apply {
    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = h
    if (max > 0) filters = arrayOf(InputFilter.LengthFilter(max))
}
private fun mailField(c: Context, v: String?) = EditText(c).apply { inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT; hint = "Gmail khôi phục"; setText(v ?: "") }
private fun validMail(s: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()
private fun column(c: Context, vararg v: android.view.View) = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(c.dp(24), c.dp(8), c.dp(24), 0); v.forEach { addView(it) } }

/** Khoá ứng dụng: có PIN thì mở cài đặt / xoá / mở ứng dụng đã khoá phải nhập PIN.
 *  Nút "Quên mã?" chỉ hiện khi đã nhập Gmail khôi phục; không có Gmail thì không lấy lại được mã */
fun guard(c: Context, ok: () -> Unit) {
    val pin = Apps.str(c, "pin")
    if (pin == null) { ok(); return }
    val n = pinLen(c)
    val et = numField(c, "Nhập mã PIN ($n số)", n)
    val b = AlertDialog.Builder(c).setTitle("Khoá ứng dụng").setView(column(c, et))
        .setPositiveButton("OK") { _, _ -> if (sha(et.text.toString()) == pin) ok() else toast(c, "Sai mã PIN") }
        .setNegativeButton("Huỷ", null).setCancelable(false)
    if (Apps.str(c, "pin_mail") != null && RECOVERY_URL.isNotEmpty()) b.setNeutralButton("Quên mã?") { _, _ -> recoverPin(c) { ok() } }
    b.show()
}

/** Bật khoá / đặt PIN mới: chọn PIN 4 hoặc 8 số (không gõ quá số đã chọn).
 *  askMail=true (lúc bật khoá lần đầu): có ô tích "Dùng Gmail khôi phục" — không tích thì vẫn khoá được nhưng quên mã sẽ không lấy lại được.
 *  askMail=false (đổi PIN / đặt lại sau khi quên): giữ nguyên Gmail đã có */
fun setupLock(c: Context, askMail: Boolean, done: () -> Unit) {
    var n = pinLen(c)
    val pin = numField(c, "Đặt PIN $n số", n)
    val r4 = RadioButton(c).apply { id = View.generateViewId(); text = "PIN 4 số" }
    val r8 = RadioButton(c).apply { id = View.generateViewId(); text = "PIN 8 số" }
    val grp = RadioGroup(c).apply { orientation = LinearLayout.HORIZONTAL; addView(r4); addView(r8); check(if (n == 8) r8.id else r4.id) }
    grp.setOnCheckedChangeListener { _, id ->
        n = if (id == r8.id) 8 else 4
        pin.hint = "Đặt PIN $n số"
        pin.filters = arrayOf(InputFilter.LengthFilter(n))
        if (pin.text.length > n) pin.setText(pin.text.toString().substring(0, n))   // đổi 8 → 4 thì cắt bớt số thừa
        pin.setSelection(pin.text.length)
    }
    val views = ArrayList<android.view.View>()
    views.add(grp); views.add(pin)
    var useMail: CheckBox? = null
    var mail: EditText? = null
    if (askMail) {
        val note = TextView(c).apply { text = "Không dùng Gmail: vẫn khoá được, nhưng nếu quên mã PIN sẽ không lấy lại được."; textSize = 13f; setPadding(0, c.dp(4), 0, 0) }
        val m = mailField(c, null).apply { visibility = View.GONE }
        val cb = CheckBox(c).apply { text = "Dùng Gmail để lấy lại mã khi quên (không bắt buộc)"; isChecked = false; setPadding(0, c.dp(8), 0, 0) }
        cb.setOnCheckedChangeListener { _, on ->
            m.visibility = if (on) View.VISIBLE else View.GONE
            note.visibility = if (on) View.GONE else View.VISIBLE
        }
        useMail = cb; mail = m
        views.add(cb); views.add(m); views.add(note)
    }
    val dlg = AlertDialog.Builder(c).setTitle(if (askMail) "Bật khoá ứng dụng" else "Đặt mã PIN mới")
        .setView(ScrollView(c).apply { addView(column(c, *views.toTypedArray())) })
        .setPositiveButton("Lưu", null).setNegativeButton("Huỷ", null).setCancelable(false).create()
    // Nhập sai thì báo lỗi nhưng KHÔNG đóng hộp thoại, đỡ phải chọn lại từ đầu
    dlg.setOnShowListener {
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val p = pin.text.toString()
            val wantMail = askMail && useMail?.isChecked == true
            val mailText = mail?.text?.toString()?.trim().orEmpty()
            if (p.length != n) toast(c, "PIN phải đủ $n số")
            else if (wantMail && !validMail(mailText)) toast(c, "Gmail không hợp lệ")
            else {
                Apps.putStr(c, "pin", sha(p)); Apps.putStr(c, "pin_len", n.toString())
                if (askMail) { if (wantMail) Apps.putStr(c, "pin_mail", mailText) else Apps.del(c, "pin_mail") }
                dlg.dismiss(); done()
            }
        }
    }
    dlg.show()
}

/** Quên PIN: gửi mã 6 số tới Gmail đã đăng ký, nhập đúng mã thì đặt lại PIN (chọn lại 4 hoặc 8 số) */
fun recoverPin(c: Context, done: () -> Unit) {
    val mail = Apps.str(c, "pin_mail")
    if (mail == null || RECOVERY_URL.isEmpty()) { toast(c, "Chưa có Gmail khôi phục nên không thể lấy lại mã PIN"); return }
    val code = (100000..999999).random().toString()
    toast(c, "Đang gửi mã tới $mail…")
    thread {
        val r = try { httpGet("$RECOVERY_URL?to=${URLEncoder.encode(mail, "UTF-8")}&code=$code").trim() } catch (e: Exception) { "net" }
        Handler(Looper.getMainLooper()).post {
            if (!r.contains("ok")) {
                // Script gửi mã có giới hạn lượt nhận: wait = vừa gửi xong, limit = Gmail này nhận đủ mã, full/quota = hết lượt gửi trong ngày
                toast(c, when (r) {
                    "wait" -> "Vừa gửi mã xong. Đợi khoảng 1 phút rồi thử lại"
                    "limit" -> "Gmail này đã nhận quá nhiều mã. Hãy thử lại sau 1 giờ"
                    "full", "quota" -> "Hôm nay hệ thống đã hết lượt gửi mã. Hãy thử lại vào ngày mai"
                    else -> "Không gửi được mã. Kiểm tra mạng"
                })
                return@post
            }
            Apps.putStr(c, "rc_hash", sha(code)); Apps.putStr(c, "rc_exp", (System.currentTimeMillis() + 600000).toString())
            val et = numField(c, "Mã 6 số", 6)
            AlertDialog.Builder(c).setTitle("Nhập mã đã gửi tới $mail").setView(column(c, et))
                .setPositiveButton("Xác nhận") { _, _ ->
                    val valid = sha(et.text.toString()) == Apps.str(c, "rc_hash") && System.currentTimeMillis() < (Apps.str(c, "rc_exp")?.toLong() ?: 0L)
                    Apps.del(c, "rc_hash", "rc_exp")
                    if (valid) setupLock(c, false, done) else toast(c, "Mã sai hoặc đã hết hạn")
                }.setNegativeButton("Huỷ", null).setCancelable(false).show()
        }
    }
}

/** Gói của giao diện chính (nút Home) đang là mặc định. "android" = chưa đặt mặc định (hệ thống sẽ hỏi mỗi lần). null = không xác định được */
fun defaultHomePkg(c: Context): String? = try {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    c.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
} catch (e: Exception) { null }

/** Một giao diện chính (HOME) trên TV: tên, gói, độ ưu tiên (số lớn hơn thì Android chọn trước, không hiện bảng chọn) */
class HomeRow(val label: String, val pkg: String, val priority: Int)

fun homeRows(c: Context): List<HomeRow> = try {
    val pm = c.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { r ->
        val p = r.activityInfo.packageName
        HomeRow(try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }, p, r.priority)
    }.distinctBy { it.pkg }
} catch (e: Exception) { emptyList() }

/** Nguồn khi bật TV: "off" | "app" (ứng dụng đã chọn, mặc định là TV Launcher) | "in:<id đầu vào>". Tương thích bản cũ (công tắc "boot") */
fun bootSrc(c: Context): String = Apps.str(c, "boot_src") ?: if (Apps.bool(c, "boot", false)) "app" else "off"

/** Các cổng vào HDMI/AV của TV: (id, tên hiển thị) */
fun bootInputs(c: Context): List<Pair<String, String>> = try {
    val tm = c.getSystemService(Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
    tm.tvInputList.filter { it.isPassthroughInput }.map { i ->
        val l = (i.loadCustomLabel(c) ?: i.loadLabel(c))?.toString()?.trim().orEmpty()
        i.id to l.ifEmpty { when (i.type) {
            android.media.tv.TvInputInfo.TYPE_HDMI -> "HDMI"; android.media.tv.TvInputInfo.TYPE_COMPOSITE -> "AV"
            android.media.tv.TvInputInfo.TYPE_VGA -> "VGA"; android.media.tv.TvInputInfo.TYPE_COMPONENT -> "Component"
            else -> "Đầu vào khác" } }
    }.sortedWith(compareBy({ !it.second.startsWith("HDMI", true) }, { it.second.lowercase() }))
} catch (e: Exception) { emptyList() }

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        HomeGuard.ensure(c)
        val src = bootSrc(c)
        val self = Intent(c, MainActivity::class.java)
        when {
            src == "off" -> {}
            src.startsWith("in:") -> {
                if (!safeStart(c, Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(src.substring(3))))) safeStart(c, self)
            }
            else -> {
                val pkg = Apps.str(c, "boot_app")
                val launch = if (pkg == null || pkg == c.packageName) null else c.packageManager.getLaunchIntentForPackage(pkg)
                safeStart(c, launch ?: self)
            }
        }
    }
}

class SettingsActivity : VActivity() {
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private var pendingVideo = false
    private val REQ_HOME = 3
    private var refreshOnBack = false   // đã mở màn hình hệ thống (giao diện mặc định / trợ năng) => quay lại thì làm mới trạng thái
    private var homeCheckOnBack = false  // quay lại thì báo đã đặt được giao diện mặc định hay chưa
    private var guardCheckOnBack = false // quay lại thì báo bật chặn thành công hay thất bại

    override fun onResume() {
        super.onResume()
        if (refreshOnBack) {
            refreshOnBack = false
            HomeGuard.resume()   // đã quay lại từ màn hình hệ thống: hết thời gian tạm dừng chặn
            build()
            if (homeCheckOnBack) { homeCheckOnBack = false; Apps.del(this, "home_check"); reportDefaultHome() }
            if (guardCheckOnBack) { guardCheckOnBack = false; verifyGuard() }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(120), dp(20), dp(120), dp(40)) }
        scroll = ScrollView(this).apply { setBackgroundColor(0xFF101018.toInt()); addView(list) }
        setContentView(scroll)
        build()
        if (Apps.str(this, "home_fail") != null) { Apps.del(this, "home_fail"); list.post { homeFailDialog() } }
        // Sau khi màn hình được tạo lại (đổi kích cỡ TV...), đưa con trỏ về đúng dòng đang chọn thay vì về đầu danh sách
        val idx = b?.getInt("focus_idx", -1) ?: -1
        if (idx >= 0) afterLayout { focusRow(idx) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("focus_idx", focusedRow())
    }

    // Số thứ tự dòng đang được chọn trong danh sách (-1 nếu chưa chọn dòng nào)
    private fun focusedRow(): Int = (0 until list.childCount).firstOrNull { list.getChildAt(it).hasFocus() } ?: -1

    // Chọn lại một dòng và cuộn sao cho dòng đó nằm khoảng 1/3 từ trên xuống
    private fun focusRow(idx: Int) {
        val row = list.getChildAt(idx) ?: return
        scroll.scrollTo(0, (row.top - scroll.height / 3).coerceAtLeast(0))
        row.requestFocus()
    }

    // Chạy sau khi giao diện đã đo xong và trước khi vẽ, nên không bị chớp
    private fun afterLayout(block: () -> Unit) {
        list.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (list.viewTreeObserver.isAlive) list.viewTreeObserver.removeOnPreDrawListener(this)
                block()
                return true
            }
        })
    }

    private fun build() {
        // Nhớ vị trí cuộn và dòng đang chọn để dựng lại danh sách mà không bị nhảy về đầu
        val keepY = scroll.scrollY
        val keepIdx = focusedRow()
        list.removeAllViews()
        list.addView(TextView(this).apply { text = "TV Launcher"; textSize = 24f; setTextColor(0xFF4D6BFF.toInt()); setPadding(dp(24), 0, 0, dp(12)) })
        item("Giới thiệu", "v$APP_VERSION", false, null, null) { showAbout() }
        item("Chuyển đầu vào (HDMI/AV)", null, false, null, null) { pickInput(this) }
        item("Cài đặt hệ thống", null, false, null, null) { openSystemSettings(this) }
        item("Cài đặt tivi", tvSettingsLabel() ?: "Chưa chọn", false, null, { Apps.del(this, "tvset_cmp") }) { openTvSettings() }
        choice("Chiều cao danh mục", "cat_h", arrayOf("Thấp", "Vừa", "Cao"), 1)
        choice("Bo góc ứng dụng", "radius", arrayOf("Vuông", "Vừa", "Tròn"), 1)
        item("Khởi động cùng thiết bị", bootLabel(), false, null, { Apps.del(this, "boot_src", "boot", "boot_app") }) { bootDialog() }
        choice("Số cột ứng dụng", "cols", arrayOf("4", "5", "6", "7", "8"), 2)
        val clear = { Apps.del(this, "bg_uri", "bg_type") }
        item("Thay đổi ảnh nền", bgLabel("image"), false, null, clear) { pick("image/*", 1) }
        item("Đặt video làm ảnh nền", bgLabel("video"), false, null, clear) { pick("video/*", 2) }
        choice("Múi giờ", "tz", arrayOf("Theo hệ thống TV", "Việt Nam (GMT+7)", "UTC", "Nhật Bản (GMT+9)", "New York", "Trung Quốc (GMT+8)"), 1)
        toggle("Hiển thị lịch âm", "lunar", true)
        item("Hiển thị tiện ích thời tiết", null, false, !Apps.isHidden(this, "@weather"), { Apps.setHidden(this, "@weather", false) }) {
            Apps.setHidden(this, "@weather", !Apps.isHidden(this, "@weather")); build() }
        item("Hiển thị hàng Yêu thích", null, false, !Apps.isHidden(this, "@favs"), { Apps.setHidden(this, "@favs", false) }) {
            Apps.setHidden(this, "@favs", !Apps.isHidden(this, "@favs")); build() }
        item("Hiển thị hàng Đầu vào (HDMI, VGA...)", null, false, !Apps.isHidden(this, "@inputs"), { Apps.setHidden(this, "@inputs", false) }) {
            Apps.setHidden(this, "@inputs", !Apps.isHidden(this, "@inputs")); build() }
        toggle("Hiển thị đầu vào MiTV", "mitv", false)
        item("Vị trí hàng Đầu vào", if (Apps.inputsAbove(this)) "Trên Yêu thích" else "Dưới Yêu thích", false, null, { Apps.setInputsAbove(this, false) }) {
            Apps.setInputsAbove(this, !Apps.inputsAbove(this)); build() }
        item("Ẩn / hiện ứng dụng", Apps.loadAll(this).count { Apps.isHidden(this, it.pkg) }.let { if (it == 0) "Chưa ẩn" else "Đang ẩn $it" }, false, null, null) { hiddenAppsDialog() }
        choice("Kích thước ứng dụng", "appsize", arrayOf("Nhỏ", "Vừa", "Lớn"), 1)
        item("Dịch vụ trợ năng", enabledServices().size.let { if (it == 0) "Tắt" else "Bật ($it)" }, false, null, null) { accessibilityDialog() }
        choice("Cỡ chữ", "fontsz", arrayOf("Vừa", "Lớn", "Rất lớn"), 0)
        item("Khoá ứng dụng", if (Apps.str(this, "pin") == null) "Tắt" else "Đang bật (PIN ${pinLen(this)} số)", true, null, null) { lockDialog() }
        item("Khôi phục cài đặt TV Launcher", null, true, null, null) { guard(this) { confirmReset() } }
        list.addView(TextView(this).apply { text = "TV Launcher By Lộc Hưng"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0) })
        // Trả lại đúng vị trí cuộn và dòng đang chọn như trước khi dựng lại
        if (keepY > 0 || keepIdx >= 0) afterLayout {
            scroll.scrollTo(0, keepY)
            if (keepIdx >= 0) list.getChildAt(keepIdx)?.requestFocus()
        }
    }

    // Giá trị hiển thị ở dòng "Độ phân giải màn hình": độ phân giải giao diện, kèm độ phân giải TV đang xuất ra nếu khác nhau (vd TV 4K vẽ giao diện Full HD)
    @Suppress("DEPRECATION")
    private fun resLabel(): String {
        val dm = android.util.DisplayMetrics(); windowManager.defaultDisplay.getRealMetrics(dm)
        var s = "${dm.widthPixels}x${dm.heightPixels}"
        if (Build.VERSION.SDK_INT >= 23) try {
            val m = windowManager.defaultDisplay.mode
            if (m.physicalWidth != dm.widthPixels || m.physicalHeight != dm.heightPixels) s += " (TV xuất ${m.physicalWidth}x${m.physicalHeight})"
        } catch (e: Exception) {}
        s += "  •  " + (RES_TIERS.firstOrNull { it.key == Apps.str(this, "dispmode") }?.label ?: "Tự động")
        return s
    }

    // Chọn độ phân giải: Tự động (theo TV) hoặc Full HD / 2K / 4K. Chỉ xin được mức mà TV báo hỗ trợ, rồi báo thành công / thất bại
    private fun resolutionDialog() {
        val cur = Apps.str(this, "dispmode")
        val labels = ArrayList<String>()
        labels.add("Tự động (theo TV)")
        RES_TIERS.forEach { t -> labels.add(if (modeForTier(this, t.key) == null) t.label + "  •  TV không hỗ trợ" else t.label) }
        labels.add("Vì sao TV 4K chỉ hiện Full HD?")
        val checked = if (cur == null) 0 else RES_TIERS.indexOfFirst { it.key == cur }.let { if (it < 0) 0 else it + 1 }
        AlertDialog.Builder(this).setTitle("Độ phân giải màn hình")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, i ->
                d.dismiss()
                when {
                    i == 0 -> { Apps.del(this, "dispmode"); applyDisplayMode(this); build(); toast(this, "Thành công: đã về Tự động (theo TV)") }
                    i <= RES_TIERS.size -> pickTier(RES_TIERS[i - 1])
                    else -> resolutionHelp()
                }
            }.show()
    }

    private fun pickTier(t: ResTier) {
        val m = modeForTier(this, t.key)
        if (m == null) { toast(this, "Thất bại: TV không báo hỗ trợ ${t.label}. Giữ nguyên như cũ."); return }
        Apps.putStr(this, "dispmode", t.key); applyDisplayMode(this); build()
        // TV cần một lúc để đổi chế độ, đợi rồi kiểm tra xem đã đổi thật chưa
        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed
            val now = currentModeId()
            if (now == m.id) toast(this, "Thành công: TV đã chuyển sang ${t.label} (${m.w}x${m.h}, ${m.hz.toInt()}Hz)")
            else toast(this, "Thất bại: đã xin ${t.label} nhưng TV chưa đổi. TV có thể không cho ứng dụng đổi, hãy xem mục \"Vì sao TV 4K chỉ hiện Full HD?\".")
        }, 1800)
    }

    @Suppress("DEPRECATION")
    private fun currentModeId(): Int = if (Build.VERSION.SDK_INT >= 23) try { windowManager.defaultDisplay.mode.modeId } catch (e: Exception) { -1 } else -1

    private fun resolutionHelp() {
        AlertDialog.Builder(this).setTitle("Vì sao TV 4K chỉ hiện Full HD?").setMessage(
            "Nhiều TV Android (Xiaomi, TCL, Sony...) vẽ giao diện ở Full HD 1920x1080 rồi tự phóng lên 4K khi hiển thị, nên ứng dụng chỉ đọc được 1920x1080. " +
            "Đây không phải lỗi của TV Launcher. Phim/video 4K trong YouTube, Netflix... vẫn phát đúng 4K.\n\n" +
            "Ứng dụng không tự đổi được độ phân giải giao diện của hệ thống. Chọn Full HD / 2K / 4K chỉ là xin TV đổi chế độ xuất hình khi đang ở TV Launcher, " +
            "và chỉ được với mức TV báo hỗ trợ. Mức nào ghi \"TV không hỗ trợ\" thì TV không cho đổi. Sau khi chọn, ứng dụng sẽ báo thành công hoặc thất bại.\n\n" +
            "Muốn ép giao diện hệ thống lên 4K (có thể làm TV giật, lag), dùng ADB:\n" +
            "1) adb shell wm density   (xem số hiện tại, ví dụ 240)\n" +
            "2) adb shell wm size 3840x2160\n" +
            "3) adb shell wm density 480   (gấp đôi số ở bước 1)\n" +
            "Khôi phục: adb shell wm size reset  và  adb shell wm density reset\n" +
            "Sau đó chỉnh Kích cỡ TV / Tinh chỉnh độ lớn cho vừa mắt."
        ).setPositiveButton("Đóng", null).show()
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

    // Ẩn / hiện ứng dụng: danh sách mọi ứng dụng, tích = đang ẩn. Bấm Lưu để áp dụng, hoặc bỏ ẩn tất cả
    private fun hiddenAppsDialog() {
        val apps = Apps.loadAll(this)
        val was = BooleanArray(apps.size) { Apps.isHidden(this, apps[it].pkg) }
        val checked = was.copyOf()
        AlertDialog.Builder(this).setTitle("Ẩn / hiện ứng dụng (tích = ẩn)")
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("Lưu") { _, _ ->
                var n = 0
                apps.forEachIndexed { i, a -> if (checked[i] != was[i]) { Apps.setHidden(this, a.pkg, checked[i]); n++ } }
                toast(this, if (n == 0) "Không có thay đổi" else "Đã cập nhật $n ứng dụng"); build()
            }
            .setNeutralButton("Bỏ ẩn tất cả") { _, _ -> Apps.showAllApps(this); toast(this, "Đã bỏ ẩn tất cả ứng dụng"); build() }
            .setNegativeButton("Đóng", null).show()
    }

    // Gỡ cài đặt (xoá) ứng dụng: chọn ứng dụng, nhập PIN nếu có khoá, rồi để hệ thống hỏi xác nhận
    private fun uninstallDialog() {
        pickApp("Chọn ứng dụng cần gỡ cài đặt", Apps.loadAll(this).filter { it.pkg != packageName }) { a ->
            guard(this) {
                if (!safeStart(this, Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))) toast(this, "TV không cho gỡ ứng dụng ở đây")
            }
        }
    }

    private fun bootAppLabel(): String {
        val p = Apps.str(this, "boot_app")
        return if (p == null || p == packageName) "TV Launcher" else Apps.loadAll(this).firstOrNull { it.pkg == p }?.label ?: "TV Launcher"
    }

    private fun bootLabel(): String {
        val s = bootSrc(this)
        return when {
            s == "off" -> "Tắt"
            s.startsWith("in:") -> bootInputs(this).firstOrNull { "in:" + it.first == s }?.second ?: "Đầu vào"
            else -> "Ứng dụng: " + bootAppLabel()
        }
    }

    // Chọn nguồn khi bật TV: HDMI/AV, ứng dụng (nhấn giữ để đổi ứng dụng) hoặc tắt
    private fun bootDialog() {
        val ins = bootInputs(this)
        val cur = bootSrc(this)
        val labels = ins.map { it.second } + ("Ứng dụng (Nhấn giữ để thay đổi)" + (if (Apps.str(this, "boot_app").let { it != null && it != packageName }) ": " + bootAppLabel() else "")) + "Tắt"
        val checked = when {
            cur == "off" -> ins.size + 1
            cur.startsWith("in:") -> ins.indexOfFirst { "in:" + it.first == cur }.let { if (it < 0) ins.size + 1 else it }
            else -> ins.size
        }
        val dlg = AlertDialog.Builder(this).setTitle("Khởi động cùng thiết bị")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { d, i ->
                when { i < ins.size -> Apps.putStr(this, "boot_src", "in:" + ins[i].first); i == ins.size -> Apps.putStr(this, "boot_src", "app"); else -> Apps.putStr(this, "boot_src", "off") }
                d.dismiss(); build()
            }.create()
        dlg.show()
        dlg.listView.setOnItemLongClickListener { _, _, pos, _ -> if (pos == ins.size) { dlg.dismiss(); pickBootApp() }; true }
    }

    private fun pickBootApp() {
        val apps = Apps.loadAll(this)
        val ad = object : ArrayAdapter<AppItem>(this, 0, apps) {
            override fun getView(p: Int, v: android.view.View?, g: android.view.ViewGroup): android.view.View = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8))
                addView(ImageView(context).apply { setImageDrawable(apps[p].icon) }, LinearLayout.LayoutParams(dp(40), dp(40)))
                addView(TextView(context).apply { text = apps[p].label; textSize = 18f; setPadding(dp(16), 0, 0, 0) })
            }
        }
        AlertDialog.Builder(this).setTitle("Chọn một ứng dụng").setAdapter(ad) { _, i ->
            Apps.putStr(this, "boot_app", apps[i].pkg); Apps.putStr(this, "boot_src", "app"); build()
        }.show()
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
        if (rc == REQ_HOME) return   // kết quả đặt giao diện mặc định: onResume sẽ làm mới trạng thái
        if (res != RESULT_OK) return
        val u = d?.data ?: return
        try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
        Apps.putStr(this, "bg_uri", u.toString()); Apps.putStr(this, "bg_type", if (rc == 2) "video" else "image"); build()
    }

    /** Trạng thái hiện ở cuối dòng "Chặn về giao diện gốc của TV" */
    private fun guardLabel(): String = when {
        !Apps.bool(this, "hg_on", false) -> "Tắt"
        HomeGuard.serviceEnabled(this) -> "Đang chặn"
        else -> "Đã bật, chờ cấp quyền trợ năng"
    }

    /** Chọn giao diện gốc cần chặn (tích = chặn). Bật chặn xong mà chưa bật dịch vụ trợ năng thì hướng dẫn bật */
    private fun guardDialog() {
        val apps = HomeGuard.homeApps(this)
        if (apps.isEmpty()) { toast(this, "TV không có giao diện chính nào khác để chặn"); return }
        val off = HomeGuard.excluded(this)
        val checked = BooleanArray(apps.size) { apps[it].first !in off }
        val on = Apps.bool(this, "hg_on", false)
        val b = AlertDialog.Builder(this).setTitle("Chặn về giao diện gốc (tích = chặn)")
            .setMultiChoiceItems(apps.map { it.second + "  (" + it.first + ")" }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
            .setPositiveButton(if (on) "Lưu" else "Bật chặn") { _, _ ->
                if (checked.none { it }) { toast(this, "Chưa tích giao diện nào để chặn"); return@setPositiveButton }
                Apps.putStr(this, "hg_off", apps.filterIndexed { i, _ -> !checked[i] }.joinToString(",") { it.first })
                Apps.putBool(this, "hg_on", true); HomeGuard.refresh()
                HomeGuard.enableService(this)
                build()
                verifyGuard()
            }
            .setNegativeButton("Đóng", null)
        if (on) b.setNeutralButton("Tắt chặn") { _, _ -> Apps.putBool(this, "hg_on", false); HomeGuard.refresh(); build(); toast(this, "Đã tắt chặn về giao diện gốc") }
        b.show()
    }

    /** Đang chặn những gì (để báo kết quả): giao diện gốc và/hoặc voice gốc */
    private fun guardWhat(): String = listOfNotNull(
        "giao diện gốc".takeIf { Apps.bool(this, "hg_on", false) },
        "voice gốc".takeIf { VoiceGuard.on(this) }
    ).joinToString(" và ")

    /** Trạng thái hiện ở cuối dòng "Chặn voice gốc của TV" */
    private fun voiceGuardLabel(): String {
        if (!VoiceGuard.on(this)) return "Tắt"
        if (!HomeGuard.serviceEnabled(this)) return "Đã bật, chờ cấp quyền trợ năng"
        val rep = VoiceGuard.replacement(this)
        val name = rep?.let { p -> Apps.loadAll(this).firstOrNull { it.pkg == p }?.label ?: p }
        return "Đang chặn  •  micro mở " + (name ?: "Google Assistant")
    }

    /** Chọn trợ lý giọng nói gốc cần chặn (tích = chặn). Nút micro trên điều khiển sẽ mở trợ lý đã chọn ở mục "Trợ lý giọng nói" (hoặc Kiki / Google Assistant) */
    private fun voiceGuardDialog() {
        val apps = VoiceGuard.assistants(this)
        val on = VoiceGuard.on(this)
        val off = VoiceGuard.excluded(this)
        val checked = BooleanArray(apps.size) { apps[it].first !in off }
        val b = AlertDialog.Builder(this).setTitle("Chặn voice gốc (tích = chặn)")
        if (apps.isEmpty()) b.setMessage("Không thấy trợ lý giọng nói gốc nào trong danh sách ứng dụng. Vẫn có thể bật để nút micro trên điều khiển luôn mở trợ lý thay thế " +
            "(chọn ở mục \"Trợ lý giọng nói\", hoặc Kiki / Google Assistant nếu có).")
        else b.setMultiChoiceItems(apps.map { it.second + "  (" + it.first + ")" }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
        b.setPositiveButton(if (on) "Lưu" else "Bật chặn") { _, _ ->
            if (apps.isNotEmpty() && checked.none { it }) { toast(this, "Chưa tích trợ lý nào để chặn"); return@setPositiveButton }
            Apps.putStr(this, "vg_off", apps.filterIndexed { i, _ -> !checked[i] }.joinToString(",") { it.first })
            Apps.putBool(this, "vg_on", true); VoiceGuard.refresh()
            HomeGuard.enableService(this)
            build()
            verifyGuard()
        }.setNegativeButton("Đóng", null)
        if (on) b.setNeutralButton("Tắt chặn") { _, _ -> Apps.putBool(this, "vg_on", false); VoiceGuard.refresh(); build(); toast(this, "Đã tắt chặn voice gốc") }
        b.show()
    }

    /** Màn hình kiểm tra: trạng thái dịch vụ, quyền, danh sách đang chặn và các sự kiện gần nhất (chụp màn hình này gửi đi nếu chặn chưa hiệu quả) */
    private fun guardInfoDialog() {
        val overlay = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(this) else true
        val blocked = HomeGuard.homeApps(this).filter { it.first !in HomeGuard.excluded(this) }
        val notes = HomeGuard.notes().ifEmpty { listOf("(chưa có) Hãy bấm Home / nút micro trên điều khiển, mở lại TV Launcher rồi vào đây xem") }
        val msg = "• Chặn: " + (if (Apps.bool(this, "hg_on", false)) "bật" else "tắt") +
            "\n• Dịch vụ trợ năng: " + (if (HomeGuard.running()) "đang chạy" else if (HomeGuard.serviceEnabled(this)) "đã bật nhưng chưa chạy" else "chưa bật") +
            "\n• Quyền ghi cài đặt bảo mật (ADB): " + (if (HomeGuard.canWriteSecure(this)) "đã cấp" else "chưa cấp") +
            "\n• Quyền hiển thị trên ứng dụng khác: " + (if (overlay) "đã cấp" else "chưa cấp") +
            "\n• Giao diện mặc định hiện tại: " + (defaultHomePkg(this) ?: "không rõ") +
            "\n• Các giao diện chính: " + homeRows(this).joinToString(", ") { "${it.label} (${it.pkg}, ưu tiên ${it.priority})" } +
            "\n• Đang chặn: " + (if (blocked.isEmpty()) "không có" else blocked.joinToString(", ") { it.second + " (" + it.first + ")" }) +
            "\n• Chặn voice gốc: " + (if (VoiceGuard.on(this)) "bật" else "tắt") +
            "\n• Voice đang chặn: " + (VoiceGuard.assistants(this).filter { it.first !in VoiceGuard.excluded(this) }.let { l -> if (l.isEmpty()) "không có" else l.joinToString(", ") { it.second + " (" + it.first + ")" } }) +
            "\n• Số lần bắt nút Home: ${HomeGuard.homeKeys}" +
            "\n\nSự kiện gần nhất (mới nhất ở trên):\n" + notes.joinToString("\n")
        AlertDialog.Builder(this).setTitle("Kiểm tra chặn giao diện gốc").setMessage(msg)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Làm mới") { _, _ -> guardInfoDialog() }
            .show()
    }

    private fun guardEnableDialog() {
        val comp = "$packageName/${HomeGuardService::class.java.name}"
        AlertDialog.Builder(this).setTitle("Bật dịch vụ trợ năng để chặn")
            .setMessage(
                "Android không cho ứng dụng tự chặn giao diện khác, nên cần bật dịch vụ trợ năng \"TV Launcher - Chặn giao diện gốc\". " +
                "Dịch vụ chỉ biết giao diện nào đang hiện lên để đưa về TV Launcher, không đọc nội dung màn hình.\n\n" +
                "CÁCH 1 (TV không có màn hình trợ năng): cấp quyền một lần bằng ADB trên máy tính, sau đó bấm \"Thử bật lại\". " +
                "TV Launcher sẽ tự bật dịch vụ và tự bật lại mỗi khi khởi động, không đụng tới dịch vụ trợ năng khác:\n" +
                "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS\n\n" +
                "CÁCH 2: bấm \"Mở cài đặt trợ năng\", chọn dịch vụ trên rồi bật.\n\n" +
                "CÁCH 3: ADB (lệnh này ghi đè các dịch vụ trợ năng khác đang bật):\n" +
                "adb shell settings put secure enabled_accessibility_services $comp\n" +
                "adb shell settings put secure accessibility_enabled 1\n\n" +
                "Nên cấp thêm quyền \"Hiển thị trên ứng dụng khác\" (mục Quyền ứng dụng) để việc chuyển về TV Launcher ổn định.")
            .setPositiveButton("Mở cài đặt trợ năng") { _, _ -> refreshOnBack = true; guardCheckOnBack = true; openAccessibilitySettings() }
            .setNeutralButton("Thử bật lại") { _, _ -> HomeGuard.enableService(this); build(); verifyGuard() }
            .setNegativeButton("Đóng", null).show()
    }

    /** Báo kết quả bật chặn: thành công khi dịch vụ trợ năng đã chạy thật. Nếu dịch vụ đã được ghi vào cài đặt mà hệ thống chưa kết nối thì đợi tối đa ~3 giây */
    private fun verifyGuard(tries: Int = 6) {
        if (isFinishing) return
        if (HomeGuard.running()) {
            build()
            val w = guardWhat()
            toast(this, "Thành công: " + (if (w.isEmpty()) "dịch vụ chặn đã chạy" else "đang chặn $w của TV"))
            return
        }
        if (tries > 0 && HomeGuard.serviceEnabled(this)) {
            Handler(Looper.getMainLooper()).postDelayed({ verifyGuard(tries - 1) }, 500)
            return
        }
        build()
        guardFailDialog()
    }

    private fun guardFailDialog() {
        val why = when {
            !HomeGuard.serviceEnabled(this) && !HomeGuard.canWriteSecure(this) ->
                "Dịch vụ trợ năng chưa bật, và TV Launcher chưa được cấp quyền WRITE_SECURE_SETTINGS (chỉ cấp được bằng ADB) nên không tự bật được."
            !HomeGuard.serviceEnabled(this) -> "TV Launcher đã có quyền nhưng hệ thống không cho bật dịch vụ trợ năng."
            else -> "Dịch vụ đã được bật trong cài đặt nhưng hệ thống chưa chạy nó. Thử tắt rồi bật lại, hoặc khởi động lại TV."
        }
        AlertDialog.Builder(this).setTitle("Thất bại: chưa bật được chặn")
            .setMessage("$why\n\nChặn chỉ hoạt động khi dịch vụ trợ năng đang chạy.")
            .setPositiveButton("Xem hướng dẫn") { _, _ -> guardEnableDialog() }
            .setNegativeButton("Đóng", null).show()
    }

    // Mở màn hình trợ năng: thử lối chuẩn trước, rồi vài màn hình của trình cài đặt TV/Android (TV Xiaomi nội địa thường đã bỏ màn hình này)
    private fun openAccessibilitySettings() {
        val tries = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.accessibility.AccessibilityActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.accessibility.AccessibilitySettings"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$AccessibilitySettingsActivity")
        )
        for (i in tries) if (safeStart(this, i)) return
        toast(this, "TV không có màn hình trợ năng. Dùng cách 1 (ADB) trong hướng dẫn.")
    }

    /** Trạng thái hiện ở cuối dòng "Đặt làm giao diện mặc định" */
    private fun homeLabel(): String {
        val p = defaultHomePkg(this)
        return when {
            p == packageName -> "Đã là mặc định"
            p == null || p == "android" -> "Chưa đặt"
            else -> "Đang dùng: " + (try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(p!!, 0)).toString() } catch (e: Exception) { p })
        }
    }

    /** Đặt TV Launcher làm giao diện chính, để nút Home trên điều khiển mở thẳng TV Launcher.
     *  Android không cho ứng dụng tự đặt âm thầm, nên thử lần lượt: hộp thoại của hệ thống (Android 10+),
     *  màn hình "ứng dụng màn hình chính" của TV, cuối cùng là hướng dẫn làm tay */
    private fun setDefaultHome() {
        try { setDefaultHomeInner() } catch (e: Exception) {
            refreshOnBack = false; homeCheckOnBack = false; HomeGuard.resume()
            toast(this, "Thất bại: không mở được màn hình đặt giao diện mặc định. Làm theo hướng dẫn thủ công.")
            homeGuide(defaultHomePkg(this))
        }
    }

    private fun setDefaultHomeInner() {
        val cur = defaultHomePkg(this)
        if (cur == packageName) { toast(this, "TV Launcher đã là giao diện mặc định. Nút Home trên điều khiển sẽ mở TV Launcher."); return }
        // Đang đổi giao diện mặc định trong màn hình hệ thống: tạm dừng chặn (đang bật trợ năng mà không tạm dừng thì bị kéo về giữa chừng,
        // màn hình Cài đặt bị đóng nên bấm vào như không có tác dụng). Quay lại Cài đặt thì chặn chạy lại
        HomeGuard.allow(120000L)
        // Hệ thống có thể mở thẳng TV Launcher (đóng luôn màn Cài đặt này): ghi lại để màn hình tiếp theo vẫn báo được kết quả
        Apps.putStr(this, "home_check", System.currentTimeMillis().toString())
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
                if (rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    refreshOnBack = true; homeCheckOnBack = true
                    startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME), REQ_HOME)
                    return
                }
            } catch (e: Exception) { refreshOnBack = false; homeCheckOnBack = false }
        }
        refreshOnBack = true; homeCheckOnBack = true
        if (safeStart(this, Intent(Settings.ACTION_HOME_SETTINGS), Intent("android.settings.MANAGE_DEFAULT_APPS_SETTINGS"))) {
            toast(this, "Hãy chọn TV Launcher trong danh sách, nếu có nút \"Luôn\" / \"Một lần\" thì bấm \"Luôn\""); return
        }
        refreshOnBack = false; homeCheckOnBack = false
        Apps.del(this, "home_check")
        HomeGuard.resume()
        homeGuide(cur)
    }

    /** Quay lại từ màn hình chọn giao diện mặc định: báo đã đặt được hay chưa */
    private fun reportDefaultHome() {
        if (defaultHomePkg(this) == packageName) toast(this, "Thành công: TV Launcher đã là giao diện mặc định")
        else homeFailDialog()
    }

    /** Đặt mặc định chưa được: nói rõ hệ thống đang chọn gì, vì sao, và cách làm tiếp */
    private fun homeFailDialog() {
        val cur = defaultHomePkg(this)
        val rows = homeRows(this)
        val myPri = rows.firstOrNull { it.pkg == packageName }?.priority ?: 0
        val higher = rows.filter { it.pkg != packageName && it.priority > myPri }
        val curName = rows.firstOrNull { it.pkg == cur }?.label ?: cur ?: "không rõ"
        val why = if (higher.isNotEmpty())
            "Giao diện gốc " + higher.joinToString(", ") { it.label } + " có độ ưu tiên cao hơn TV Launcher, nên Android luôn chọn nó và không hiện bảng chọn \"Luôn\". " +
            "Trường hợp này không đặt được ngay trên TV, chỉ đặt được bằng ADB (chạy trên máy tính, TV đã bật ADB):\n" +
            "adb shell cmd package set-home-activity $packageName/.MainActivity\n\n"
        else
            "Nếu hiện bảng chọn có nút \"Luôn\" và \"Một lần\": chọn TV Launcher rồi bấm \"Luôn\" (bấm \"Một lần\" thì chỉ mở TV Launcher chứ không lưu). " +
            "Nếu không hiện bảng chọn thì giao diện gốc đang giữ mặc định: bấm \"Hướng dẫn thủ công\" để xoá mặc định của nó trước.\n\n"
        val tip = if (Apps.bool(this, "hg_on", false)) "" else "Mẹo: bật \"Chặn về giao diện gốc của TV\" thì nút Home trên điều khiển vẫn về TV Launcher dù chưa đặt mặc định."
        AlertDialog.Builder(this).setTitle("Chưa thành công: chưa đặt được mặc định")
            .setMessage("Hệ thống đang dùng: $curName" + (if (cur != null) " ($cur)" else "") + "\n\n$why" +
                "Các giao diện chính trên TV:\n" + (if (rows.isEmpty()) "không đọc được" else rows.joinToString("\n") { "• ${it.label} (${it.pkg}) • ưu tiên ${it.priority}" }) +
                (if (tip.isEmpty()) "" else "\n\n$tip"))
            .setPositiveButton("Thử lại") { _, _ -> setDefaultHome() }
            .setNeutralButton("Hướng dẫn thủ công") { _, _ -> homeGuide(cur) }
            .setNegativeButton("Đóng", null).show()
    }

    private fun homeGuide(cur: String?) {
        val none = cur == null || cur == "android"
        val b = AlertDialog.Builder(this).setTitle("Đặt TV Launcher làm giao diện mặc định")
            .setMessage(
                "TV này không có màn hình chọn giao diện chính, hãy làm theo 1 trong 2 cách:\n\n" +
                (if (none) "Cách 1: bấm \"Mở màn hình chọn\" bên dưới, chọn TV Launcher rồi bấm \"Luôn\" (Always).\n\n"
                 else "Cách 1: bấm \"Mở thông tin giao diện hiện tại\", chọn \"Xoá mặc định\" (Clear defaults). Sau đó bấm nút Home trên điều khiển, chọn TV Launcher rồi bấm \"Luôn\" (Always).\n\n") +
                "Cách 2 (dùng máy tính, bật ADB trên TV):\nadb shell cmd package set-home-activity $packageName/.MainActivity")
            .setNeutralButton("Cài đặt hệ thống") { _, _ -> openSystemSettings(this) }
            .setNegativeButton("Đóng", null)
        if (none) b.setPositiveButton("Mở màn hình chọn") { _, _ ->
            refreshOnBack = true; HomeGuard.allow(120000L)
            if (!safeStart(this, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))) toast(this, "TV không mở được màn hình chọn")
        } else b.setPositiveButton("Mở thông tin giao diện hiện tại") { _, _ -> refreshOnBack = true; HomeGuard.allow(120000L); openDetails(this, cur!!, "Chọn Xoá mặc định / Clear defaults") }
        b.show()
    }

    private fun xiaomiHelp() {
        AlertDialog.Builder(this).setTitle("Hướng dẫn cho TV Xiaomi nội địa").setMessage(
            "• Phím Menu (≡) trên điều khiển mở nhanh cài đặt của TV Launcher.\n" +
            "• Nút Home của Xiaomi có thể vẫn về giao diện gốc (PatchWall). Hãy mở TV Launcher từ danh sách ứng dụng.\n" +
            "• Muốn nút Home mở thẳng TV Launcher: vào Cài đặt hệ thống tìm mục ứng dụng mặc định / màn hình chính (nếu TV có). " +
            "Nếu không có, có thể đặt bằng ADB: adb shell cmd package set-home-activity com.example.tvlauncher/.MainActivity\n" +
            "• Giao diện luôn hiển thị tiếng Việt dù TV đang để tiếng Trung.\n" +
            "• Chọn đúng Kích cỡ TV (32, 43, 55, 65, 75, 85 inch...) để chữ và biểu tượng vừa mắt; chưa vừa thì dùng Tinh chỉnh độ lớn.\n" +
            "• Nếu TV yếu, giữ bật \"Giảm chuyển động\" và dùng ảnh nền thay vì video 4K.\n" +
            "• Nếu không chọn được ảnh/video nền, dùng \"Duyệt bộ nhớ / USB\".").setPositiveButton("Đóng", null).show()
    }

    // Cài đặt tivi: mở màn hình cài đặt riêng của TV (hình ảnh, âm thanh...). Mỗi hãng đặt màn hình này ở một nơi khác nhau
    // nên chọn một lần trong danh sách, lần sau bấm là mở thẳng. Nhấn giữ dòng này để chọn lại.
    private fun tvSettingsComp(): android.content.ComponentName? {
        val cn = Apps.str(this, "tvset_cmp")?.let { android.content.ComponentName.unflattenFromString(it) } ?: return null
        return try { packageManager.getActivityInfo(cn, 0); cn } catch (e: Exception) { null }
    }

    private fun tvSettingsLabel(): String? {
        val cn = tvSettingsComp() ?: return null
        return try { packageManager.getActivityInfo(cn, 0).loadLabel(packageManager).toString() } catch (e: Exception) { null }
    }

    private fun openTvSettings() {
        val cn = tvSettingsComp()
        if (cn == null) { chooseTvSettings(); return }
        if (!safeStart(this, Intent(Intent.ACTION_MAIN).setComponent(cn))) {
            toast(this, "Không mở được, hãy chọn lại"); chooseTvSettings()
        }
    }

    private fun chooseTvSettings() {
        val pm = packageManager
        val found = LinkedHashMap<String, android.content.pm.ResolveInfo>()
        val cats = listOf("android.intent.category.LEANBACK_SETTINGS", Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in cats) {
            try {
                for (r in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    val ai = r.activityInfo ?: continue
                    if (ai.packageName == packageName) continue
                    val k = ai.packageName + "/" + ai.name
                    if (!found.containsKey(k)) found[k] = r
                }
            } catch (e: Exception) {}
        }
        val all = found.values.sortedBy { it.loadLabel(pm).toString().lowercase() }
        if (all.isEmpty()) { toast(this, "Không tìm thấy ứng dụng cài đặt nào"); return }
        val keys = listOf("setting", "cài đặt", "设置", "mitv", "picture", "tvmanager")
        val likely = all.filter { r ->
            val t = (r.loadLabel(pm).toString() + " " + r.activityInfo.packageName).lowercase()
            keys.any { t.contains(it) }
        }
        fun show(src: List<android.content.pm.ResolveInfo>, more: Boolean) {
            val names = src.map { it.loadLabel(pm).toString() + "  •  " + it.activityInfo.packageName }.toMutableList()
            if (more) names.add("Xem tất cả ứng dụng…")
            AlertDialog.Builder(this).setTitle("Chọn màn hình Cài đặt tivi (hình ảnh, âm thanh...)")
                .setItems(names.toTypedArray()) { _, i ->
                    if (i >= src.size) show(all, false)
                    else {
                        val ai = src[i].activityInfo
                        val c = android.content.ComponentName(ai.packageName, ai.name)
                        Apps.putStr(this, "tvset_cmp", c.flattenToString()); build()
                        if (!safeStart(this, Intent(Intent.ACTION_MAIN).setComponent(c))) toast(this, "Không mở được mục này, hãy chọn mục khác")
                    }
                }.setNegativeButton("Đóng", null).show()
        }
        if (likely.isEmpty()) show(all, false) else show(likely, likely.size < all.size)
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
            .setPositiveButton("Mở cài đặt trợ năng") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("Đóng", null).show()
    }

    private fun permissionDialog() {
        val overlay = if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(this) else true
        val b = AlertDialog.Builder(this).setTitle("Quyền ứng dụng")
            .setMessage("• Truy cập Internet (thời tiết, gửi mã Gmail)\n• Xem danh sách ứng dụng đã cài\n• Gỡ cài đặt ứng dụng\n• Khởi động cùng thiết bị\n• Dịch vụ trợ năng (chỉ khi bật \"Chặn về giao diện gốc\" hoặc \"Chặn voice gốc\")\n• Đọc bộ nhớ/USB (khi chọn ảnh, video nền)\n• Hiển thị trên ứng dụng khác (giúp tự khởi động ổn định): " +
                (if (overlay) "đã cấp" else "chưa cấp") +
                "\n• Ghi cài đặt bảo mật (tự bật dịch vụ trợ năng, chỉ cấp được bằng ADB): " + (if (HomeGuard.canWriteSecure(this)) "đã cấp" else "chưa cấp"))
            .setNeutralButton("Cài đặt quyền") { _, _ ->
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) } catch (e: Exception) { toast(this, "TV không mở được màn hình này") } }
            .setNegativeButton("Đóng", null)
        if (!overlay) b.setPositiveButton("Cấp quyền hiển thị") { _, _ ->
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (e: Exception) { toast(this, "TV không mở được màn hình này") } }
        b.show()
    }

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
            .setItems(arrayOf("Cho phép TV Launcher cài app", "Cho phép cho app khác…", "Mở cài đặt bảo mật hệ thống")) { _, i ->
                when (i) {
                    0 -> unknownSources(packageName)
                    1 -> pickApp("Cho phép cài từ…", Apps.loadAll(this)) { unknownSources(it.pkg) }
                    2 -> openSettings(Intent(Settings.ACTION_SECURITY_SETTINGS))
                }
            }.show()
    }

    private fun showAbout() {
        fun t(txt: String, sz: Float, col: Int = Color.WHITE) = TextView(this).apply { text = txt; textSize = sz; setTextColor(col); setPadding(0, dp(4), 0, dp(4)) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), dp(16))
            addView(t("TV Launcher - Phiên bản $APP_VERSION", 18f))
            addView(t("Liên hệ tác giả", 16f, 0xFF4D6BFF.toInt()))
            addView(t("Facebook: Lộc Hưng", 16f))
            addView(t("Zalo: 0349353193", 16f))
            addView(t("Ủng hộ tác giả", 16f, 0xFF4D6BFF.toInt()))
            addView(ImageView(context).apply { setImageResource(R.drawable.qr_donate); adjustViewBounds = true }, LinearLayout.LayoutParams(dp(300), -2))
            addView(t("Cảm ơn bạn đã sử dụng!", 14f))
        }
        AlertDialog.Builder(this).setView(ScrollView(this).apply { setBackgroundColor(0xFF1C1C24.toInt()); addView(box) }).setPositiveButton("Đóng", null).show()
    }

    /** Hỏi xác nhận trước khi khôi phục toàn bộ cài đặt */
    private fun confirmReset() {
        AlertDialog.Builder(this).setTitle("Khôi phục cài đặt TV Launcher")
            .setMessage("Toàn bộ cài đặt (giao diện, ảnh nền, tiện ích, mục Yêu thích, các mục tuỳ chỉnh, ứng dụng đã ẩn...) sẽ về mặc định. Mã PIN và các ứng dụng đã khoá được giữ nguyên.")
            .setPositiveButton("Khôi phục") { _, _ -> resetAll() }
            .setNegativeButton("Huỷ", null).show()
    }

    /** Xoá toàn bộ cài đặt về mặc định (giữ lại PIN và Gmail khôi phục) */
    private fun resetAll() {
        Apps.resetAll(this)
        toast(this, "Đã khôi phục cài đặt mặc định")
        recreate()
    }

    private fun lockDialog() {
        if (Apps.str(this, "pin") == null) { setupLock(this, true) { build() }; return }
        val hasMail = Apps.str(this, "pin_mail") != null
        val labels = ArrayList<String>(); val actions = ArrayList<() -> Unit>()
        fun add(l: String, a: () -> Unit) { labels.add(l); actions.add(a) }
        add("Đổi mã PIN (chọn 4 hoặc 8 số)") { setupLock(this, false) { toast(this, "Đã đổi mã PIN"); build() } }
        add(if (hasMail) "Đổi Gmail khôi phục" else "Thêm Gmail khôi phục (để lấy lại mã khi quên)") { mailDialog() }
        if (hasMail) add("Bỏ Gmail khôi phục (quên mã sẽ không lấy lại được)") { Apps.del(this, "pin_mail"); toast(this, "Đã bỏ Gmail khôi phục") }
        add("Tắt khoá ứng dụng") { Apps.del(this, "pin", "pin_len", "pin_mail", "locked"); build() }
        AlertDialog.Builder(this).setTitle("Khoá ứng dụng").setItems(labels.toTypedArray()) { _, i ->
            guard(this) { actions[i]() }
        }.show()
    }

    private fun mailDialog() {
        val mail = mailField(this, Apps.str(this, "pin_mail"))
        val dlg = AlertDialog.Builder(this).setTitle("Gmail khôi phục").setView(column(this, mail))
            .setPositiveButton("Lưu", null).setNegativeButton("Huỷ", null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val m = mail.text.toString().trim()
                if (validMail(m)) { Apps.putStr(this, "pin_mail", m); toast(this, "Đã lưu Gmail khôi phục"); dlg.dismiss() }
                else toast(this, "Gmail không hợp lệ")
            }
        }
        dlg.show()
    }
}

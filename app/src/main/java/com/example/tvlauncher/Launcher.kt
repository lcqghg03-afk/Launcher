package com.example.tvlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewOutlineProvider
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.widget.*
import android.os.Build
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread

/** Số phiên bản hiển thị trong Cài đặt (dòng Giới thiệu và hộp thoại Giới thiệu). Nhớ đổi versionName trong build.gradle.kts cho khớp */
const val APP_VERSION = "1.7"

/** Danh sách tỉnh/thành để chọn nơi hiển thị thời tiết thủ công (toạ độ xấp xỉ trung tâm) */
val VN_PLACES = listOf(
    Triple("Hà Nội", 21.0285, 105.8542), Triple("TP. Hồ Chí Minh", 10.7769, 106.7009),
    Triple("Hải Phòng", 20.8449, 106.6881), Triple("Đà Nẵng", 16.0544, 108.2022),
    Triple("Huế", 16.4637, 107.5909), Triple("Cần Thơ", 10.0452, 105.7469),
    Triple("Tuyên Quang", 21.8233, 105.2141), Triple("Lào Cai", 21.7229, 104.9113),
    Triple("Thái Nguyên", 21.5928, 105.8442), Triple("Phú Thọ", 21.3227, 105.4019),
    Triple("Bắc Ninh", 21.1861, 106.0763), Triple("Hưng Yên", 20.6464, 106.0511),
    Triple("Ninh Bình", 20.2506, 105.9745), Triple("Thanh Hóa", 19.8067, 105.7852),
    Triple("Nghệ An", 18.6796, 105.6813), Triple("Hà Tĩnh", 18.3428, 105.9057),
    Triple("Quảng Trị", 17.4690, 106.6222), Triple("Quảng Ngãi", 15.1205, 108.7923),
    Triple("Gia Lai", 13.7829, 109.2196), Triple("Khánh Hòa", 12.2388, 109.1967),
    Triple("Lâm Đồng", 11.9404, 108.4583), Triple("Đắk Lắk", 12.6667, 108.0378),
    Triple("Đồng Nai", 10.9574, 106.8427), Triple("Tây Ninh", 11.3100, 106.0983),
    Triple("Vĩnh Long", 10.2537, 105.9722), Triple("Đồng Tháp", 10.4938, 105.6882),
    Triple("An Giang", 10.3759, 105.4185), Triple("Cà Mau", 9.1769, 105.1524),
    Triple("Điện Biên", 21.3860, 103.0230), Triple("Lai Châu", 22.3964, 103.4703),
    Triple("Sơn La", 21.3256, 103.9188), Triple("Lạng Sơn", 21.8537, 106.7615),
    Triple("Quảng Ninh", 20.9517, 107.0807), Triple("Cao Bằng", 22.6657, 106.2570)
)

/** Quận/huyện/thị xã/thành phố theo từng tỉnh (toạ độ xấp xỉ). Phú Thọ gộp cả Vĩnh Phúc và Hòa Bình cũ.
 *  Xã/phường/thị trấn thì dùng nút "Tìm" (tra cứu qua mạng, cả nước) */
val VN_DISTRICTS: Map<String, List<Triple<String, Double, Double>>> = mapOf(
    "Phú Thọ" to listOf(
        Triple("TP Việt Trì", 21.3227, 105.4019), Triple("TX Phú Thọ", 21.4007, 105.2273),
        Triple("Đoan Hùng", 21.6090, 105.1450), Triple("Hạ Hòa", 21.5890, 104.9700),
        Triple("Thanh Ba", 21.4585, 105.1750), Triple("Phù Ninh", 21.4440, 105.3190),
        Triple("Yên Lập", 21.3750, 105.0340), Triple("Cẩm Khê", 21.3956, 105.1050),
        Triple("Tam Nông", 21.2970, 105.2480), Triple("Lâm Thao", 21.3150, 105.3050),
        Triple("Thanh Sơn", 21.1600, 105.1730), Triple("Thanh Thủy", 21.0720, 105.2760),
        Triple("Tân Sơn", 21.1880, 104.8800),
        Triple("TP Vĩnh Yên", 21.3089, 105.6049), Triple("TX Phúc Yên", 21.2315, 105.7000),
        Triple("Lập Thạch", 21.4108, 105.4658), Triple("Tam Dương", 21.3680, 105.5700),
        Triple("Tam Đảo", 21.4590, 105.6050), Triple("Bình Xuyên", 21.2970, 105.6620),
        Triple("Yên Lạc", 21.2160, 105.5850), Triple("Vĩnh Tường", 21.2400, 105.4930),
        Triple("Sông Lô", 21.4700, 105.4000),
        Triple("TP Hòa Bình", 20.8133, 105.3383), Triple("Đà Bắc", 20.9200, 105.0000),
        Triple("Mai Châu", 20.6600, 105.0000), Triple("Lương Sơn", 20.8700, 105.5100),
        Triple("Kim Bôi", 20.6700, 105.5400), Triple("Lạc Sơn", 20.4600, 105.4500),
        Triple("Tân Lạc", 20.6100, 105.2900), Triple("Lạc Thủy", 20.4800, 105.7100),
        Triple("Yên Thủy", 20.4000, 105.6200), Triple("Cao Phong", 20.7000, 105.3000)
    )
)

/** Ép giao diện tiếng Việt, kể cả khi TV Xiaomi nội địa đang để tiếng Trung */
// Kích cỡ màn hình: hệ số phóng to/thu nhỏ toàn bộ giao diện. 55 inch là mốc chuẩn (1.0)
// Xếp theo thứ tự tăng dần: điện thoại/tablet, laptop/màn hình PC, rồi tới TV. Hai mảng dưới phải cùng số phần tử
val TV_SIZES = arrayOf(
    "Tự động (theo TV)",
    "6.5 inch (điện thoại)", "8 inch (tablet nhỏ)", "10 inch (tablet)", "15.6 inch (laptop)",
    "21.5 inch (màn hình PC)", "24 inch (PC / TV nhỏ)", "27 inch (màn hình PC)",
    "32 inch", "40 inch", "43 inch", "50 inch", "55 inch", "60 inch", "65 inch", "70 inch",
    "75 inch", "77 inch", "85 inch", "98 inch", "100 inch", "110 inch"
)
val TV_FACTORS = floatArrayOf(
    1f,
    0.50f, 0.55f, 0.60f, 0.64f,
    0.68f, 0.72f, 0.77f,
    0.82f, 0.88f, 0.92f, 0.97f, 1f, 1.05f, 1.1f, 1.15f,
    1.2f, 1.23f, 1.3f, 1.45f, 1.48f, 1.6f
)
val FINE_LABELS = arrayOf("80%", "90%", "100%", "110%", "120%", "130%", "140%", "150%", "160%", "170%", "180%", "190%", "200%")
val FINE_FACTORS = floatArrayOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f, 1.5f, 1.6f, 1.7f, 1.8f, 1.9f, 2f)

// Bản cũ lưu số thứ tự theo danh sách cũ dưới đây. Danh sách mới có thêm nhiều cỡ nên phải đổi số cũ sang số mới (chỉ chạy một lần)
private val OLD_TV_SIZES = arrayOf("Tự động (theo TV)", "32 inch", "43 inch", "50 inch", "55 inch", "65 inch", "75 inch", "85 inch", "98 inch")

private fun migrateTvSize(sp: android.content.SharedPreferences) {
    if (sp.getInt("tvsize_ver", 1) >= 2) return
    val e = sp.edit()
    if (sp.contains("tvsize")) {
        val label = OLD_TV_SIZES.getOrNull(sp.getInt("tvsize", 0))
        e.putInt("tvsize", TV_SIZES.indexOf(label).coerceAtLeast(0))
    }
    e.putInt("tvsize_ver", 2).apply()
}

fun uiScale(c: Context): Float {
    val sp = c.getSharedPreferences("cfg", 0)
    migrateTvSize(sp)
    return TV_FACTORS[sp.getInt("tvsize", 0).coerceIn(0, TV_FACTORS.size - 1)] * FINE_FACTORS[sp.getInt("tvfine", 2).coerceIn(0, FINE_FACTORS.size - 1)]
}

fun viCtx(b: Context): Context {
    val vi = Locale("vi", "VN"); Locale.setDefault(vi)
    val cfg = android.content.res.Configuration(b.resources.configuration); cfg.setLocale(vi)
    val f = uiScale(b)
    if (f != 1f) {
        val base = if (cfg.densityDpi > 0) cfg.densityDpi else b.resources.displayMetrics.densityDpi
        cfg.densityDpi = (base * f).toInt().coerceIn(80, 800)
    }
    return b.createConfigurationContext(cfg)
}

open class VActivity : Activity() {
    override fun attachBaseContext(b: Context) { super.attachBaseContext(viCtx(b)) }
}

// Gói ứng dụng xem TV/nguồn tín hiệu của Xiaomi (thử lần lượt, gói nào có trên TV thì dùng)
val MITV_PKGS = listOf("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.livetv", "com.mitv.tvhome", "com.xiaomi.mitv.settings.input")

fun isXiaomi() = Build.MANUFACTURER.equals("xiaomi", true) || Build.BRAND.equals("xiaomi", true) || Build.BRAND.equals("redmi", true)

/** Thử lần lượt từng intent, intent nào mở được thì dừng. TV Xiaomi nội địa hay thiếu màn hình hệ thống chuẩn */
fun safeStart(c: Context, vararg list: Intent?): Boolean {
    for (i in list) {
        if (i == null) continue
        try { if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else HomeGuard.allow(); c.startActivity(i); return true } catch (e: Exception) {}
    }
    return false
}

/** Chọn cổng HDMI/AV. Mở từ Cài đặt Home TV */
fun pickInput(c: Context) {
    try {
        val tm = c.getSystemService(Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
        val ins = tm.tvInputList.filter { it.isPassthroughInput }
        if (ins.isEmpty()) { Toast.makeText(c, "Không tìm thấy cổng HDMI/AV", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(c).setTitle("Đầu vào").setItems(ins.map { it.loadLabel(c).toString() }.toTypedArray()) { _, i ->
            if (!safeStart(c, Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(ins[i].id)))) Toast.makeText(c, "Không chuyển được đầu vào", Toast.LENGTH_SHORT).show()
        }.show()
    } catch (e: Exception) { Toast.makeText(c, "Thiết bị không có đầu vào TV", Toast.LENGTH_SHORT).show() }
}

fun openSystemSettings(c: Context) {
    val pm = c.packageManager
    val ok = safeStart(c, Intent(Settings.ACTION_SETTINGS), pm.getLaunchIntentForPackage("com.xiaomi.mitv.settings"),
        pm.getLaunchIntentForPackage("com.android.tv.settings"), pm.getLaunchIntentForPackage("com.android.settings"))
    if (!ok) Toast.makeText(c, "TV không mở được cài đặt hệ thống", Toast.LENGTH_LONG).show()
}

fun httpGet(u: String): String {
    val c = URL(u).openConnection() as java.net.HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "TVLauncher/$APP_VERSION")
    try { return c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
}

fun decodeScaled(c: Context, u: Uri, mw: Int, mh: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / (s * 2) >= mw && o.outHeight / (s * 2) >= mh) s *= 2
    // Màu 32-bit (mặc định) cho ảnh mịn, không bị sọc ở vùng chuyển màu; hết RAM thì tự lùi về 16-bit
    fun dec(cfg: Bitmap.Config): Bitmap? {
        val o2 = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = cfg }
        return c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
    }
    if (Apps.int(c, "imgq", 0) == 0) { try { dec(Bitmap.Config.ARGB_8888) } catch (e: OutOfMemoryError) { dec(Bitmap.Config.RGB_565) } }
    else dec(Bitmap.Config.RGB_565)
} catch (e: Throwable) { null }

fun focusBg(c: Context): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(12).toFloat(); setColor(0x55FFFFFF) })
    addState(intArrayOf(), GradientDrawable().apply { setColor(0) })
}

fun chipBg(c: Context): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0x66FFFFFF); setStroke(c.dp(2), Color.WHITE) })
    addState(intArrayOf(), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0x44000000); setStroke(c.dp(1), 0x88FFFFFF.toInt()) })
}

/** Nền ô giờ + thời tiết: hoàn toàn trong suốt, chỉ hiện viền sáng khi đang được chọn bằng điều khiển */
fun cwBg(c: Context): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0x22FFFFFF); setStroke(c.dp(2), Color.WHITE) })
    addState(intArrayOf(), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0) })
}

fun wmo(code: Int) = when (code) {
    0 -> "Trời quang"; 1, 2 -> "Ít mây"; 3 -> "Nhiều mây"; 45, 48 -> "Sương mù"
    in 51..57 -> "Mưa phùn"; in 61..67, in 80..82 -> "Mưa"; in 71..77, 85, 86 -> "Tuyết"; in 95..99 -> "Dông"
    else -> ""
}

data class AppItem(val label: String, val pkg: String, val icon: Drawable)

object Speaker {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var ready = false
    fun say(c: Context, t: String) {
        if (tts == null) {
            tts = android.speech.tts.TextToSpeech(c.applicationContext) { ready = it == 0; if (ready) tts?.language = java.util.Locale("vi") }
            return
        }
        if (ready) tts?.speak(t, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, null)
    }
}

object Apps {
    fun fs(c: Context) = floatArrayOf(1f, 1.25f, 1.5f)[int(c, "fontsz", 0)]
    fun load(c: Context): List<AppItem> = loadAll(c).filter { !isHidden(c, it.pkg) }
    private var cache: List<AppItem>? = null
    private var recv = false
    fun loadAll(c: Context): List<AppItem> {
        cache?.let { return it }
        if (!recv) {
            recv = true
            val f = IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package") }
            c.applicationContext.registerReceiver(object : BroadcastReceiver() { override fun onReceive(x: Context, i: Intent) { cache = null } }, f)
        }
        val pm = c.packageManager
        val map = LinkedHashMap<String, AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach {
                val p = it.activityInfo.packageName
                if (p !in map) map[p] = AppItem(it.loadLabel(pm).toString(), p, it.loadIcon(pm))
            }
        }
        val r = map.values.sortedBy { it.label.lowercase() }
        cache = r
        return r
    }
    fun rm(c: Context) = bool(c, "rm", isXiaomi())
    private fun sp(c: Context) = c.getSharedPreferences("cfg", 0)
    private fun csv(c: Context, k: String): List<String> = sp(c).getString(k, "")!!.split(",").filter { it.isNotEmpty() }
    fun favs(c: Context) = csv(c, "fav")
    fun setFavs(c: Context, l: List<String>) = sp(c).edit().putString("fav", l.joinToString(",")).apply()
    // Ẩn: khoá ứng dụng = package, khoá khối giao diện = "@weather", "@clock", "@favs", "@inputs"
    fun isHidden(c: Context, k: String) = k in csv(c, "hid")
    fun setHidden(c: Context, k: String, h: Boolean) {
        val l = csv(c, "hid").toMutableList(); l.remove(k); if (h) l.add(k)
        sp(c).edit().putString("hid", l.joinToString(",")).apply()
    }
    fun isLocked(c: Context, k: String) = k in csv(c, "locked")
    fun setLocked(c: Context, k: String, v: Boolean) {
        val l = csv(c, "locked").toMutableList(); l.remove(k); if (v) l.add(k)
        sp(c).edit().putString("locked", l.joinToString(",")).apply()
    }
    fun showAll(c: Context) = sp(c).edit().putString("hid", "").apply()
    // Vị trí hàng Đầu vào: false (mặc định) = nằm DƯỚI hàng Yêu thích, true = nằm TRÊN
    fun inputsAbove(c: Context) = sp(c).getBoolean("inp_above", false)
    fun setInputsAbove(c: Context, v: Boolean) = sp(c).edit().putBoolean("inp_above", v).apply()
    // Kích thước: 0 = nhỏ, 1 = vừa, 2 = lớn
    fun size(c: Context, k: String) = sp(c).getInt("size_$k", 1)
    fun setSize(c: Context, k: String, v: Int) = sp(c).edit().putInt("size_$k", v).remove("fs_$k").apply()
    private val S = floatArrayOf(0.75f, 1f, 1.4f)
    // Kích thước liên tục (kéo trái/phải) của giờ và thời tiết, lưu dạng số thực
    fun fscale(c: Context, k: String): Float? = if (sp(c).contains("fs_$k")) sp(c).getFloat("fs_$k", 1f) else null
    fun setScale(c: Context, k: String, v: Float) = sp(c).edit().putFloat("fs_$k", v).apply()
    fun scale(c: Context, k: String): Float = (fscale(c, k) ?: S[size(c, k)]) * (if (k.startsWith("@")) 1f else S[int(c, "appsize", 1)])
    // Hiện lại các ứng dụng đã ẩn (giữ nguyên trạng thái ẩn của các khối giao diện bắt đầu bằng @)
    fun showAllApps(c: Context) = sp(c).edit().putString("hid", csv(c, "hid").filter { it.startsWith("@") }.joinToString(",")).apply()
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun putInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun putBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun str(c: Context, k: String): String? = sp(c).getString(k, null)
    fun putStr(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
    fun del(c: Context, vararg k: String) { val e = sp(c).edit(); k.forEach { e.remove(it) }; e.apply() }
    /** Khôi phục TV Launcher về mặc định: xoá mọi cài đặt đã lưu (kể cả yêu thích, ứng dụng ẩn, ảnh nền, vị trí thời tiết...).
     *  Chỉ giữ lại mã PIN, Gmail khôi phục và danh sách ứng dụng đã khoá để không vô tình mở khoá ứng dụng */
    fun resetAll(c: Context) {
        val keep = setOf("pin", "pin_len", "pin_mail", "locked", "tvsize_ver", "act_dev")
        val e = sp(c).edit()
        sp(c).all.keys.filter { it !in keep }.forEach { e.remove(it) }
        e.apply()
    }
    fun radius(c: Context) = intArrayOf(0, 12, 32)[int(c, "radius", 1)]
    fun tz(c: Context): String? = arrayOf<String?>(null, "Asia/Ho_Chi_Minh", "UTC", "Asia/Tokyo", "America/New_York", "Asia/Shanghai")[int(c, "tz", 1)]
    fun open(c: Context, pkg: String) {
        HomeGuard.allow()   // người dùng chủ động mở: không chặn nhầm (kể cả khi đó là giao diện gốc của TV)
        val pm = c.packageManager
        val list = ArrayList<Intent>()
        pm.getLeanbackLaunchIntentForPackage(pkg)?.let { list.add(it) }
        pm.getLaunchIntentForPackage(pkg)?.let { list.add(it) }
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat).setPackage(pkg), 0).firstOrNull()?.let {
                    list.add(Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName(it.activityInfo.packageName, it.activityInfo.name))
                }
            } catch (e: Exception) {}
        }
        var err: String? = null
        for (i in list) {
            try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); c.startActivity(i); return }
            catch (e: Exception) { err = e.javaClass.simpleName }
        }
        Toast.makeText(c, "Không mở được ứng dụng này" + (err?.let { " ($it)" } ?: ""), Toast.LENGTH_SHORT).show()
    }
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Ô ứng dụng: ấn = mở, giữ lâu = menu */
fun appView(c: Context, a: AppItem, inGrid: Boolean = false, mult: Float = 1f, onChange: () -> Unit): View = LinearLayout(c).apply {
    val sc = Apps.scale(c, a.pkg) * mult
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
    setPadding(c.dp(12), c.dp(12), c.dp(12), c.dp(12))
    // Trong lưới "Tất cả ứng dụng" ô không tự nhận phím/chạm, GridView lo việc bấm và giữ
    isFocusable = !inGrid; isClickable = !inGrid
    addView(ImageView(c).apply { setImageDrawable(a.icon); layoutParams = LinearLayout.LayoutParams(c.dp((72 * sc).toInt()), c.dp((72 * sc).toInt()))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, c.dp(Apps.radius(c)).toFloat()) } } })
    addView(TextView(c).apply { text = a.label; setTextColor(Color.WHITE); textSize = 13f * sc * Apps.fs(c); maxLines = 1; gravity = Gravity.CENTER })
    setOnFocusChangeListener { v, f ->
        v.scaleX = if (f && !Apps.rm(c)) 1.12f else 1f; v.scaleY = v.scaleX
        v.background = if (f) GradientDrawable().apply { cornerRadius = c.dp(Apps.radius(c) + 4).toFloat(); setColor(0x33FFFFFF) } else null
        if (f && Apps.bool(c, "tts", false)) Speaker.say(c, a.label)
    }
    if (!inGrid) {
        setOnClickListener { openApp(c, a) }
        setOnLongClickListener { showMenu(c, a, onChange); true }
    }
}

/** Mở ứng dụng, hỏi PIN nếu ứng dụng đang bị khoá */
fun openApp(c: Context, a: AppItem) {
    if (Apps.isLocked(c, a.pkg)) guard(c) { Apps.open(c, a.pkg) } else Apps.open(c, a.pkg)
}

fun openDetails(c: Context, pkg: String, hint: String?) {
    if (!safeStart(c, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))) Toast.makeText(c, "TV không mở được màn hình thông tin ứng dụng", Toast.LENGTH_LONG).show()
    else if (hint != null) Toast.makeText(c, hint, Toast.LENGTH_LONG).show()
}

fun openPerms(c: Context, pkg: String) {
    val i = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra("android.intent.extra.PACKAGE_NAME", pkg)
    if (!safeStart(c, i)) openDetails(c, pkg, "Chọn mục Quyền để cấp hoặc thu hồi quyền")
}

fun sizeDialog(c: Context, key: String, title: String, onChange: () -> Unit) {
    AlertDialog.Builder(c).setTitle("Kích thước: $title")
        .setSingleChoiceItems(arrayOf("Nhỏ", "Vừa", "Lớn"), Apps.size(c, key)) { d, i -> Apps.setSize(c, key, i); d.dismiss(); onChange() }.show()
}

fun showMenu(c: Context, a: AppItem, onChange: () -> Unit) {
    val fav = a.pkg in Apps.favs(c)
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    fun row(l: String, f: () -> Unit) { labels.add(l); acts.add(f) }
    fun toggleFav() { val l = Apps.favs(c).toMutableList(); if (fav) l.remove(a.pkg) else l.add(a.pkg); Apps.setFavs(c, l); onChange() }
    if (fav) row("Xoá khỏi mục Yêu thích") { toggleFav() }
    row("Mở ứng dụng") { openApp(c, a) }
    if (!fav) row("Thêm vào mục Yêu thích") { toggleFav() }
    row("Thêm vào mục tuỳ chỉnh…") { secPickAdd(c, a, onChange) }
    row("Tuỳ chỉnh kích thước") { sizeDialog(c, a.pkg, a.label, onChange) }
    row("Ẩn ứng dụng") { Apps.setHidden(c, a.pkg, true); onChange() }
    row(if (Apps.isLocked(c, a.pkg)) "Mở khoá ứng dụng" else "Khoá ứng dụng") {
        if (Apps.str(c, "pin") == null) Toast.makeText(c, "Hãy bật Khoá ứng dụng trong Cài đặt Home TV trước", Toast.LENGTH_LONG).show()
        else guard(c) { Apps.setLocked(c, a.pkg, !Apps.isLocked(c, a.pkg)); onChange() }
    }
    row("Cấp quyền ứng dụng") { openPerms(c, a.pkg) }
    row("Xoá dữ liệu ứng dụng") { guard(c) { openDetails(c, a.pkg, "Chọn Bộ nhớ rồi bấm Xoá dữ liệu") } }
    row("Xem thông tin ứng dụng") { openDetails(c, a.pkg, null) }
    row("Gỡ cài đặt (xoá) ứng dụng") {
        guard(c) { if (!safeStart(c, Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))) Toast.makeText(c, "TV không cho gỡ ứng dụng ở đây", Toast.LENGTH_LONG).show() }
    }
    AlertDialog.Builder(c).setTitle(a.label).setItems(labels.toTypedArray()) { _, i -> acts[i]() }.show()
}

/** Menu cho widget / giờ / hàng yêu thích / hàng đầu vào: đổi kích thước, chuyển lên/xuống, ẩn */
fun blockMenu(c: Context, key: String, title: String, onChange: () -> Unit) {
    if (!Apps.bool(c, "pick", true)) return
    val labels = mutableListOf("Đổi kích thước")
    val acts = mutableListOf<() -> Unit>({ sizeDialog(c, key, title, onChange) })
    if (key == "@favs" || key == "@inputs") {
        val above = Apps.inputsAbove(c)
        // Khi above = false thứ tự từ trên xuống là: Yêu thích, Đầu vào. Khi above = true thì ngược lại
        val isTop = (key == "@inputs") == above
        labels.add(if (isTop) "Chuyển xuống" else "Chuyển lên")
        acts.add { Apps.setInputsAbove(c, !above); onChange() }
    }
    labels.add("Ẩn mục này")
    acts.add { Apps.setHidden(c, key, true); onChange() }
    AlertDialog.Builder(c).setTitle(title).setItems(labels.toTypedArray()) { _, i -> acts[i]() }.show()
}

/** Áp kích thước + ẩn cho một khối. px,py = điểm neo phóng to (0..1) */
fun applyBlock(c: Context, v: View, key: String, px: Float, py: Float, reflow: Boolean = false) {
    v.visibility = if (Apps.isHidden(c, key)) View.GONE else View.VISIBLE
    // reflow = true: khối đổi cỡ chữ thật (không phóng hình) nên các khối bên dưới tự dịch chỗ
    val s = if (reflow) 1f else Apps.scale(c, key); v.scaleX = s; v.scaleY = s
    v.post { v.pivotX = v.width * px; v.pivotY = v.height * py }
}

class MainActivity : VActivity() {
    private lateinit var favRow: LinearLayout
    private lateinit var weather: TextView
    private lateinit var cwBox: LinearLayout
    private lateinit var timeRow: LinearLayout
    private lateinit var lunar: TextView
    private var scaleKey = 0
    private val tick = android.os.Handler(android.os.Looper.getMainLooper())
    private val tickR = object : Runnable { override fun run() { refreshLunar(); refreshInputs(); tick.postDelayed(this, 60000) } }
    private lateinit var bg: FrameLayout
    private lateinit var tint: View
    private var mp: MediaPlayer? = null
    private lateinit var favBox: View
    private lateinit var inputRow: LinearLayout
    private lateinit var inputBox: View
    private lateinit var bottomCol: LinearLayout
    private lateinit var leftRow: LinearLayout
    private lateinit var iconsRow: LinearLayout
    private lateinit var secBox: LinearLayout
    private lateinit var bottomScroll: ScrollView
    private val secCards = ArrayList<View>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        scaleKey = (uiScale(this) * 1000).toInt()
        // Dọn dữ liệu cũ của hàng Kênh / Truyền hình (đã bỏ hẳn tính năng này)
        Apps.del(this, "tv_ch", "tv_show", "tvc_show", "vch", "size_@tv", "fs_@tv")
        Apps.setHidden(this, "@tv", false)
        val root = FrameLayout(this)
        bg = FrameLayout(this); tint = View(this)
        root.addView(bg, -1, -1); root.addView(tint, -1, -1)

        // Trái: trợ lý giọng nói + lưới tất cả ứng dụng
        val left = LinearLayout(this).apply {
            addView(iconBtn(R.drawable.ic_mic, "Trợ lý giọng nói") { voice() })
            addView(iconBtn(R.drawable.ic_apps, "Tất cả ứng dụng") { safeStart(this@MainActivity, Intent(this@MainActivity, AllAppsActivity::class.java)) })
        }
        leftRow = left
        root.addView(left, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(40), dp(24), 0, 0) })

        // Phải trên cùng: Wi-Fi + cài đặt. Đứng yên một chỗ, không bị ô giờ/thời tiết ảnh hưởng
        val icons = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(iconBtn(R.drawable.ic_wifi, "Wi-Fi") { if (!safeStart(this@MainActivity, Intent(Settings.ACTION_WIFI_SETTINGS))) openSystemSettings(this@MainActivity) })
            addView(iconBtn(R.drawable.ic_settings, "Cài đặt") { settingsMenu() })
        }
        iconsRow = icons
        root.addView(icons, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(24), dp(40), 0) })

        // Ô độc lập: giờ + ngày + âm lịch ở trên, thời tiết ở dưới. Phóng to/thu nhỏ bằng thanh gạt, không đụng tới thứ khác
        timeRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextClock(this@MainActivity).apply { format24Hour = "HH:mm"; format12Hour = "HH:mm"; timeZone = Apps.tz(this@MainActivity); textSize = 26f; setTextColor(Color.WHITE) })
            lunar = TextView(this@MainActivity).apply { textSize = 11f; setTextColor(Color.WHITE) }
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0)
                addView(TextClock(this@MainActivity).apply { format24Hour = "EEEE, d 'tháng' M, yyyy"; format12Hour = format24Hour; timeZone = Apps.tz(this@MainActivity); textSize = 11f; setTextColor(Color.WHITE) })
                addView(lunar)
            })
        }
        weather = TextView(this).apply {
            text = "Đang tải thời tiết…"; setTextColor(Color.WHITE); textSize = 15f; maxLines = 1
            setPadding(0, dp(6), 0, 0)
        }
        cwBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(16), dp(10), dp(16), dp(12)); background = cwBg(this@MainActivity)
            addView(timeRow); addView(weather)
            shadowAll(this)
            setOnClickListener { boxSettings() }
            setOnLongClickListener { boxMenu(); true }
        }
        root.addView(cwBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(84), dp(40), 0) })
        // Đổi cỡ ô: bấm OK vào ô rồi dùng thanh gạt, hoặc kéo trái/phải bằng chuột/cảm ứng (phím TRÁI/PHẢI dành để chuyển mục)
        attachBoxResize()

        // Yêu thích
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rowTitle("Yêu thích", "@favs", "Hàng yêu thích") { favRow }, LinearLayout.LayoutParams(-2, -2))
            favRow = LinearLayout(this@MainActivity)
            addView(HorizontalScrollView(this@MainActivity).apply { isFocusable = false; addView(favRow) })
            setOnLongClickListener { blockMenu(this@MainActivity, "@favs", "Hàng yêu thích") { applyAll() }; true }
        }
        favBox = box

        // Đầu vào: HDMI 1/2/3, VGA, AV... mặc định nằm DƯỚI hàng Yêu thích (đổi lên/xuống được, xem applyOrder)
        val ibox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rowTitle("Đầu vào", "@inputs", "Hàng đầu vào") { inputRow }, LinearLayout.LayoutParams(-2, -2))
            inputRow = LinearLayout(this@MainActivity)
            addView(HorizontalScrollView(this@MainActivity).apply { isFocusable = false; addView(inputRow) })
            setOnLongClickListener { blockMenu(this@MainActivity, "@inputs", "Hàng đầu vào") { applyAll() }; true }
        }
        inputBox = ibox
        // Các mục tuỳ chỉnh: nằm trên hàng Yêu thích, mỗi mục là một hàng (tên mục + các ô ứng dụng); chưa có mục nào thì ẩn cả khối
        secBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        bottomCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        // Cuộn dọc được: nhiều mục thì màn hình cuộn theo ô đang chọn, không tràn ra ngoài. Chừa phía trên cho ô giờ + thời tiết
        bottomScroll = ScrollView(this).apply { isFocusable = false; isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(bottomCol) }
        root.addView(bottomScroll, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(40), dp(180), dp(40), dp(40)) })
        setContentView(root)
        left.getChildAt(1).requestFocus()
    }

    private var resizeToast: Toast? = null

    private fun attachBoxResize() {
        val v: View = cwBox
        fun change(ns: Float) {
            val s = ns.coerceIn(0.5f, maxScale())
            Apps.setScale(this, "@clock", s); applyBox()
            resizeToast?.cancel()
            resizeToast = Toast.makeText(this, "Giờ và thời tiết: ${(s * 100).toInt()}%", Toast.LENGTH_SHORT).also { it.show() }
        }
        var downX = 0f; var start = 1f; var drag = false
        v.setOnTouchListener { _, ev ->
            if (Apps.bool(this, "cw_lock", false)) return@setOnTouchListener false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = ev.rawX; start = Apps.scale(this, "@clock"); drag = false; false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    if (!drag && Math.abs(dx) > dp(12)) { drag = true; v.cancelLongPress() }
                    if (drag) { change(start + dx / dp(500)); true } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { val d = drag; drag = false; if (d) v.isPressed = false; d }
                else -> false
            }
        }
    }

    // Cỡ lớn nhất mà ô còn nằm trọn trong màn hình (không bị tràn mất chữ)
    private fun maxScale(): Float {
        val root = cwBox.parent as? View ?: return 2.5f
        if (root.width == 0 || cwBox.width == 0) return 2.5f
        val byW = (root.width - dp(80)).toFloat() / cwBox.width
        val byH = (root.height - dp(84) - dp(200)).toFloat() / cwBox.height.coerceAtLeast(1)
        return minOf(2.5f, byW, byH).coerceAtLeast(1f)
    }

    // Phóng to/thu nhỏ cả ô giờ + thời tiết quanh góc phải-trên. Dùng scale hình nên KHÔNG đẩy Wi-Fi, cài đặt hay khối khác
    private fun applyBox() {
        val hideClock = Apps.isHidden(this, "@clock"); val hideWeather = Apps.isHidden(this, "@weather")
        timeRow.visibility = if (hideClock) View.GONE else View.VISIBLE
        weather.visibility = if (hideWeather) View.GONE else View.VISIBLE
        cwBox.visibility = if (hideClock && hideWeather) View.GONE else View.VISIBLE
        val s = Apps.scale(this, "@clock").coerceAtMost(maxScale()); cwBox.scaleX = s; cwBox.scaleY = s
        cwBox.pivotX = cwBox.width.toFloat(); cwBox.pivotY = 0f
        cwBox.post { cwBox.pivotX = cwBox.width.toFloat(); cwBox.pivotY = 0f }
    }

    // Đổ bóng chữ để vẫn đọc rõ khi nền trong suốt
    private fun shadowAll(v: View) {
        if (v is TextView) v.setShadowLayer(6f, 0f, 2f, 0xDD000000.toInt())
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) shadowAll(v.getChildAt(i))
    }

    // Bấm vào ô: chọn tỉnh/thành, thanh gạt kích thước, khoá. Chỉ lưu khi bấm "Lưu"
    private fun boxSettings() {
        val c = this
        val oldScale = Apps.scale(c, "@clock")
        val oldLock = Apps.bool(c, "cw_lock", false)
        val mx = maxScale()
        val col = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(16), dp(24), 0) }
        col.addView(TextView(c).apply { text = "Nơi hiển thị thời tiết" })
        val names = listOf("Tự động (theo mạng)") + VN_PLACES.map { it.first }
        val sp = Spinner(c)
        sp.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, names)
        col.addView(sp)
        val sp2 = Spinner(c)
        col.addView(sp2)
        val picked = TextView(c).apply { textSize = 13f; setPadding(0, dp(4), 0, 0) }
        var pick: Triple<String, Double, Double>? = null
        val showPick = { picked.text = pick?.let { "Đã chọn: ${it.first}" } ?: "" }
        val fillDist = { p: String? ->
            val l = listOf("Toàn tỉnh/thành") + ((p?.let { VN_DISTRICTS[it] } ?: emptyList()).map { it.first })
            sp2.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, l)
            sp2.visibility = if (l.size > 1) View.VISIBLE else View.GONE
        }
        val savedName = Apps.str(c, "wx_place")
        val savedLat = Apps.str(c, "wx_lat")?.toDoubleOrNull(); val savedLon = Apps.str(c, "wx_lon")?.toDoubleOrNull()
        val savedProv = VN_DISTRICTS.entries.firstOrNull { e -> e.value.any { it.first == savedName } }
        var lastProv = names.indexOf(savedProv?.key ?: savedName).coerceAtLeast(0)
        sp.setSelection(lastProv)
        fillDist(names.getOrNull(lastProv)?.takeIf { lastProv > 0 })
        if (savedProv != null) sp2.setSelection(1 + savedProv.value.indexOfFirst { it.first == savedName })
        else if (savedName != null && lastProv == 0 && savedLat != null && savedLon != null) pick = Triple(savedName, savedLat, savedLon)
        else if (savedName != null && savedLat != null && savedLon != null && names.indexOf(savedName) < 0) pick = Triple(savedName, savedLat, savedLon)
        showPick()
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) {
                if (i == lastProv) return
                lastProv = i; fillDist(names.getOrNull(i)?.takeIf { i > 0 }); pick = null; showPick()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        col.addView(Button(c).apply {
            text = "Tìm xã / phường / thị trấn / huyện…"
            setOnClickListener { searchPlace { h -> pick = Triple(h.first, h.second, h.third); showPick() } }
        })
        col.addView(picked)
        col.addView(TextView(c).apply { text = "Kích thước: gạt trái = nhỏ, gạt phải = lớn (tối đa vừa màn hình)"; setPadding(0, dp(20), 0, 0) })
        val bar = SeekBar(c).apply {
            max = 100
            progress = (((oldScale.coerceAtMost(mx) - 0.5f) / (mx - 0.5f)) * 100).toInt().coerceIn(0, 100)
            isEnabled = !oldLock
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) { Apps.setScale(c, "@clock", 0.5f + (mx - 0.5f) * p / 100f); applyBox() }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        col.addView(bar)
        val lockCb = CheckBox(c).apply { text = "Khoá kích thước (tránh đổi nhầm)"; isChecked = oldLock; setPadding(0, dp(12), 0, 0) }
        lockCb.setOnCheckedChangeListener { _, on -> bar.isEnabled = !on }
        col.addView(lockCb)
        val revert = { Apps.setScale(c, "@clock", oldScale); applyBox() }
        AlertDialog.Builder(c).setTitle("Giờ và thời tiết").setView(ScrollView(c).apply { addView(col) })
            .setPositiveButton("Lưu") { _, _ ->
                val i = sp.selectedItemPosition; val di = sp2.selectedItemPosition
                val pk = pick
                if (pk != null) {
                    Apps.putStr(c, "wx_place", pk.first); Apps.putStr(c, "wx_lat", pk.second.toString()); Apps.putStr(c, "wx_lon", pk.third.toString())
                } else if (i > 0 && sp2.visibility == View.VISIBLE && di > 0) {
                    val d = VN_DISTRICTS[names[i]]!![di - 1]
                    Apps.putStr(c, "wx_place", d.first); Apps.putStr(c, "wx_lat", d.second.toString()); Apps.putStr(c, "wx_lon", d.third.toString())
                } else if (i == 0) Apps.del(c, "wx_place", "wx_lat", "wx_lon")
                else { Apps.del(c, "wx_lat", "wx_lon"); Apps.putStr(c, "wx_place", names[i]) }
                Apps.putBool(c, "cw_lock", lockCb.isChecked)
                weather.text = "Đang tải thời tiết…"
                loadWeather()
                Toast.makeText(c, "Đã lưu", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ") { _, _ -> revert() }
            .setOnCancelListener { revert() }
            .show()
    }

    private class Hit(val label: String, val name: String, val lat: Double, val lon: Double)

    // Bỏ dấu + chữ thường để gõ có dấu hay không dấu đều khớp ("Thanh Ba" = "thanh ba" = "THANH BÁ")
    private fun norm(s: String): String {
        val d = java.text.Normalizer.normalize(s.lowercase(Locale("vi")), java.text.Normalizer.Form.NFD)
        return d.replace(Regex("\\p{M}+"), "").replace('đ', 'd').replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    // Tra cứu mọi địa danh cả nước (tỉnh, huyện, xã, phường, thị trấn...): danh sách có sẵn (dùng được khi mất mạng),
    // rồi OpenStreetMap, dự phòng Open-Meteo. Tự thử nhiều cách gõ (đủ câu, chỉ phần trước dấu phẩy, bỏ dấu)
    private fun geocode(q0: String): List<Hit> {
        val q = q0.trim().replace(Regex("\\s+"), " ")
        val tokens = norm(q).split(" ").filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        fun add(h: Hit) {
            val n = norm(h.name)
            if (out.none { Math.abs(it.lat - h.lat) < 0.003 && Math.abs(it.lon - h.lon) < 0.003 && norm(it.name) == n }) out.add(h)
        }
        fun matchAll(label: String): Boolean { val n = " " + norm(label) + " "; return tokens.all { n.contains(" " + it) } }

        for (pl in VN_PLACES) if (matchAll(pl.first)) add(Hit(pl.first, pl.first, pl.second, pl.third))
        VN_DISTRICTS.forEach { (prov, l) -> l.forEach { d -> if (matchAll(d.first + " " + prov)) add(Hit(d.first + ", " + prov, d.first, d.second, d.third)) } }

        val first = q.substringBefore(",").trim()
        val plain = norm(q)
        val variants = LinkedHashSet<String>().apply { add(q); if (first.isNotEmpty()) add(first); add(plain); add(norm(first)) }.filter { it.isNotEmpty() }
        var anyOk = false
        var lastErr: Exception? = null

        for (v in variants) {
            if (out.size >= 8) break
            try {
                val e = URLEncoder.encode(v, "UTF-8")
                val arr = JSONArray(httpGet("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&dedupe=1&limit=30&countrycodes=vn&accept-language=vi&q=$e"))
                anyOk = true
                val raw = ArrayList<Hit>(); val pref = ArrayList<Hit>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cat = o.optString("category")
                    val disp = o.optString("display_name").removeSuffix(", Việt Nam").replace(Regex(",\\s*\\d{5,6}$"), "")
                    val nm = o.optString("name").ifEmpty { disp.substringBefore(",") }
                    val h = Hit(disp, nm, o.getString("lat").toDouble(), o.getString("lon").toDouble())
                    if (cat == "boundary" || cat == "place") pref.add(h) else if (cat != "highway" && cat != "shop" && cat != "amenity") raw.add(h)
                }
                (pref + raw).forEach { add(it) }
                if (out.size < 8) Thread.sleep(1100)   // Nominatim chỉ cho tối đa 1 yêu cầu/giây
            } catch (ex: Exception) { lastErr = ex }
        }

        if (out.isEmpty()) for (v in variants) {
            try {
                val e = URLEncoder.encode(v.substringBefore(","), "UTF-8")
                val arr = JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?count=20&language=vi&format=json&name=$e")).optJSONArray("results")
                anyOk = true
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("country_code") != "VN") continue
                    val label = listOf(o.optString("name"), o.optString("admin3"), o.optString("admin2"), o.optString("admin1")).filter { it.isNotEmpty() }.joinToString(", ")
                    add(Hit(label, o.optString("name"), o.getDouble("latitude"), o.getDouble("longitude")))
                }
                if (out.isNotEmpty()) break
            } catch (ex: Exception) { lastErr = ex }
        }

        if (out.isEmpty() && !anyOk && lastErr != null) throw lastErr
        // Kết quả khớp đủ các từ khoá đứng trước
        return out.sortedBy { if (matchAll(it.label)) 0 else 1 }
    }

    private fun searchPlace(onPick: (Triple<String, Double, Double>) -> Unit) {
        val c = this
        val hits = ArrayList<Hit>(); val labels = ArrayList<String>()
        val adapter = ArrayAdapter(c, android.R.layout.simple_list_item_1, labels)
        val input = EditText(c).apply { hint = "VD: Thanh Ba, Phú Thọ (có dấu hay không dấu đều được)"; setSingleLine(true); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH }
        val status = TextView(c).apply { textSize = 13f; text = "Nhập tên rồi bấm Tìm (có thể gõ kèm tỉnh để chính xác hơn)"; setPadding(0, dp(6), 0, dp(6)) }
        val list = ListView(c).apply { this.adapter = adapter }
        val go = {
            val q = input.text.toString().trim()
            if (q.isNotEmpty()) {
                status.text = "Đang tìm…"
                thread {
                    val r = try { geocode(q) } catch (ex: Exception) { null }
                    runOnUiThread {
                        hits.clear(); labels.clear()
                        if (r == null) status.text = "Không kết nối được mạng"
                        else { hits.addAll(r); r.forEach { labels.add(it.label) }; status.text = if (r.isEmpty()) "Không tìm thấy" else "Chọn một nơi bên dưới" }
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
        val bar = LinearLayout(c).apply {
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(c).apply { text = "Tìm"; setOnClickListener { go() } })
        }
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(12), dp(24), 0)
            addView(bar); addView(status); addView(list, LinearLayout.LayoutParams(-1, dp(260)))
        }
        input.setOnEditorActionListener { _, _, _ -> go(); true }
        val dlg = AlertDialog.Builder(c).setTitle("Tìm nơi hiển thị thời tiết").setView(col).setNegativeButton("Đóng", null).create()
        list.setOnItemClickListener { _, _, i, _ -> hits.getOrNull(i)?.let { onPick(Triple(it.name, it.lat, it.lon)); dlg.dismiss() } }
        dlg.show()
    }

    private fun boxMenu() {
        val locked = Apps.bool(this, "cw_lock", false)
        AlertDialog.Builder(this).setTitle("Giờ và thời tiết")
            .setItems(arrayOf("Chọn nơi ở và kích thước", if (locked) "Mở khoá kích thước" else "Khoá kích thước", "Ẩn mục này")) { _, i ->
                when (i) {
                    0 -> boxSettings()
                    1 -> { Apps.putBool(this, "cw_lock", !locked); Toast.makeText(this, if (locked) "Đã mở khoá" else "Đã khoá", Toast.LENGTH_SHORT).show() }
                    else -> { Apps.setHidden(this, "@clock", true); Apps.setHidden(this, "@weather", true); applyAll() }
                }
            }.show()
    }

    // Cỡ chữ của giờ, ngày, lịch âm và thời tiết theo cỡ chữ chung
    private fun applyTexts() {
        val fs = Apps.fs(this)
        weather.textSize = 15f * fs
        (timeRow.getChildAt(0) as TextView).textSize = 26f * fs
        val d = timeRow.getChildAt(1) as LinearLayout
        (d.getChildAt(0) as TextView).textSize = 11f * fs
        lunar.textSize = 11f * fs
    }

    // Tiêu đề của một hàng: bấm OK (hoặc giữ) để đổi kích thước / chuyển lên-xuống / ẩn
    private fun rowTitle(label: String, key: String, name: String, menu: (() -> Unit)? = null, row: () -> android.view.ViewGroup) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f
        setPadding(dp(12), dp(4), dp(12), dp(4)); isFocusable = false; isClickable = true  // tiêu đề chỉ để đọc, điều khiển không dừng vào đây
        background = null
        // Mục tuỳ chỉnh (menu != null) mở menu riêng của mục; các hàng Yêu thích / Đầu vào vẫn dùng blockMenu như cũ
        val open: () -> Unit = {
            if (menu != null) { if (Apps.bool(this@MainActivity, "pick", true)) menu() }
            else blockMenu(this@MainActivity, key, name) { applyAll() }
        }
        setOnClickListener { open() }
        setOnLongClickListener { open(); true }
        // Bấm XUỐNG từ tiêu đề: vào thẳng ô đầu tiên của hàng, không để Android tự đoán rồi nhảy sang hàng khác
        setOnKeyListener { _, code, e ->
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && e.action == KeyEvent.ACTION_DOWN) {
                val first = row().getChildAt(0)
                if (first != null && first.isFocusable) { first.requestFocus(); true } else false
            } else false
        }
    }

    // Sắp xếp các khối dưới cùng: các mục tuỳ chỉnh ở trên, rồi mặc định Yêu thích ở trên, Đầu vào ở dưới
    private fun applyOrder() {
        val above = Apps.inputsAbove(this)
        val first = if (above) inputBox else favBox
        val second = if (above) favBox else inputBox
        val keyOf = { v: View -> if (v === favBox) "@favs" else "@inputs" }
        val all = listOf(secBox, first, second)
        if (bottomCol.childCount != 3 || bottomCol.getChildAt(0) !== secBox || bottomCol.getChildAt(1) !== first) {
            bottomCol.removeAllViews(); all.forEach { bottomCol.addView(it) }
        }
        // Khoảng cách chỉ chừa khi phía dưới còn khối đang hiện (mỗi mục tuỳ chỉnh đã tự chừa khoảng cách riêng)
        val shown = { v: View -> if (v === secBox) secBox.childCount > 0 else !Apps.isHidden(this, keyOf(v)) }
        all.forEachIndexed { i, v ->
            val laterVisible = all.drop(i + 1).any { shown(it) }
            v.setPadding(0, 0, 0, if (laterVisible && v !== secBox) dp(14) else 0)
        }
    }

    // Bấm bánh răng (hoặc phím Menu) mở thẳng cài đặt TV Launcher. Cài đặt hệ thống của TV nằm bên trong đó
    private fun settingsMenu() {
        guard(this) { safeStart(this, Intent(this, SettingsActivity::class.java)) }
    }

    // Thứ tự di chuyển cố định (không phụ thuộc cách Android tự đoán):
    // Mic -> Tất cả ứng dụng -> Wi-Fi -> Cài đặt -> Giờ/ngày/thời tiết -> từng ô trong các mục tuỳ chỉnh -> từng ứng dụng yêu thích
    // -> từng cổng vào. Các tiêu đề (Yêu thích, Đầu vào) bị bỏ qua, chỉ dừng ở ô có thể bấm. Nếu đã đổi vị trí hàng Đầu vào lên trên thì hai hàng cuối đổi chỗ cho khớp màn hình
    private fun focusOrder(): List<View> {
        val l = ArrayList<View>()
        for (i in 0 until leftRow.childCount) l.add(leftRow.getChildAt(i))
        for (i in 0 until iconsRow.childCount) l.add(iconsRow.getChildAt(i))
        l.add(cwBox)
        l.addAll(secCards)
        val blocks = if (Apps.inputsAbove(this)) listOf(inputBox to inputRow, favBox to favRow) else listOf(favBox to favRow, inputBox to inputRow)
        for ((_, row) in blocks) {
            for (i in 0 until row.childCount) l.add(row.getChildAt(i))
        }
        return l.filter { it.isShown && it.isFocusable }
    }

    // Phím PHẢI hoặc XUỐNG: sang mục kế tiếp, hết mục cuối thì về Mic. Phím TRÁI hoặc LÊN: ngược lại, ở Mic bấm TRÁI/LÊN thì nhảy xuống mục cuối
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val code = e.keyCode
        if (e.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) && !isFinishing) {
            val forward = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN
            val list = focusOrder()
            if (list.isNotEmpty()) {
                val n = list.size
                // Tiêu điểm có thể đang nằm ở một view con bên trong mục (biểu tượng, chữ...) nên đi ngược lên tìm mục chứa nó
                var i = -1
                var f: View? = currentFocus
                while (f != null && i < 0) { i = list.indexOf(f); f = f.parent as? View }
                val start = if (i >= 0) i else if (forward) -1 else 0
                var step = 0
                while (step < n) {
                    step++
                    val idx = if (forward) (start + step) % n else ((start - step) % n + n) % n
                    if (list[idx].requestFocus()) return true
                }
            }
        }
        return super.dispatchKeyEvent(e)
    }

    // Phím Menu (≡) trên điều khiển Xiaomi mở nhanh cài đặt; phím Back không thoát launcher
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) { settingsMenu(); return true }
        return super.onKeyDown(k, e)
    }
    override fun onBackPressed() {}

    private var lastWeather = 0L
    override fun onResume() {
        super.onResume()
        // Chưa kích hoạt (hoặc đã xoá dữ liệu ứng dụng) thì chỉ hiện màn hình nhập key
        if (!Act.ok(this)) { startActivity(Intent(this, ActivationActivity::class.java)); return }
        if ((uiScale(this) * 1000).toInt() != scaleKey) { recreate(); return }
        applyAll()
        tick.removeCallbacks(tickR); tickR.run()
        if (System.currentTimeMillis() - lastWeather > 600000L) loadWeather()
    }

    override fun onPause() { super.onPause(); tick.removeCallbacks(tickR); releaseBg() }

    private fun refreshLunar() {
        val z = Apps.tz(this)?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()
        val c = java.util.Calendar.getInstance(z)
        lunar.text = Lunar.label(c.get(java.util.Calendar.DAY_OF_MONTH), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.YEAR))
    }

    private fun releaseBg() { mp?.release(); mp = null; bg.removeAllViews() }

    private fun applyBackground() {
        releaseBg()
        val u = Uri.parse(Apps.str(this, "bg_uri") ?: return)
        try {
            if (Apps.str(this, "bg_type") == "video") {
                val sv = SurfaceView(this)
                // Độ phân giải khung hình video nền: Gốc / Full HD / 2K / 4K
                when (Apps.int(this, "res", 0)) {
                    1 -> sv.holder.setFixedSize(1920, 1080)
                    2 -> sv.holder.setFixedSize(2560, 1440)
                    3 -> sv.holder.setFixedSize(3840, 2160)
                }
                sv.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        mp = MediaPlayer().apply {
                            setDataSource(this@MainActivity, u); setSurface(h.surface); isLooping = true; setVolume(0f, 0f)
                            setOnPreparedListener { it.start() }
                            setOnErrorListener { m, _, _ -> m.release(); mp = null; runOnUiThread { Toast.makeText(this@MainActivity, "TV không phát được video nền này", Toast.LENGTH_LONG).show() }; true }
                            prepareAsync()
                        }
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) { mp?.release(); mp = null }
                })
                bg.addView(sv, -1, -1)
            } else {
                // Giải mã ảnh thu nhỏ trong luồng riêng để TV yếu không bị đơ hoặc hết RAM
                val iv = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                bg.addView(iv, -1, -1)
                thread {
                    // Độ phân giải ảnh nền: Full HD (mặc định) / 2K / 4K / Gốc
                    val (iw, ih) = when (Apps.int(this, "imgres", 0)) { 1 -> 2560 to 1440; 2 -> 3840 to 2160; 3 -> Int.MAX_VALUE to Int.MAX_VALUE; else -> 1920 to 1080 }
                    val bmp = decodeScaled(this, u, iw, ih)
                    runOnUiThread { if (bmp != null) {
                        // Sắc nét: phóng to giữ nguyên ô vuông (hợp ảnh pixel / ảnh nhỏ); Mượt: làm mịn như thường
                        if (Apps.int(this, "imgscale", 0) == 1) iv.setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, bmp).apply { setFilterBitmap(false) })
                        else iv.setImageBitmap(bmp)
                    } else Toast.makeText(this, "Không mở được ảnh nền", Toast.LENGTH_SHORT).show() }
                }
            }
        } catch (e: Exception) { Toast.makeText(this, "Không mở được nền", Toast.LENGTH_SHORT).show() }
    }


    private fun applyAll() {
        applyBackground()
        tint.setBackgroundColor(0x00000000)
        (bottomScroll.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(intArrayOf(20, 40, 80)[Apps.int(this, "cat_h", 1)])
        bottomScroll.requestLayout()
        lunar.visibility = if (Apps.bool(this, "lunar", true)) View.VISIBLE else View.GONE
        applyTexts()
        refreshSecs()
        applyOrder()
        refreshFavs()
        applyBox()
        applyBlock(this, favBox, "@favs", 0f, 1f)
        applyBlock(this, inputBox, "@inputs", 0f, 1f)
    }

    private fun inputName(i: android.media.tv.TvInputInfo): String {
        val l = (i.loadCustomLabel(this) ?: i.loadLabel(this))?.toString()?.trim().orEmpty()
        if (l.isNotEmpty()) return l
        return when (i.type) {
            android.media.tv.TvInputInfo.TYPE_HDMI -> "HDMI"
            android.media.tv.TvInputInfo.TYPE_VGA -> "VGA"
            android.media.tv.TvInputInfo.TYPE_COMPOSITE -> "AV"
            android.media.tv.TvInputInfo.TYPE_COMPONENT -> "Component"
            android.media.tv.TvInputInfo.TYPE_SCART -> "SCART"
            android.media.tv.TvInputInfo.TYPE_SVIDEO -> "S-Video"
            android.media.tv.TvInputInfo.TYPE_DVI -> "DVI"
            android.media.tv.TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
            else -> "Đầu vào khác"
        }
    }

    private fun inputChip(name: String, connected: Boolean, inputId: String, passthrough: Boolean = true): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
        setPadding(dp(14), dp(10), dp(16), dp(10)); background = chipBg(this@MainActivity)
        addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_input) }, LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(TextView(this@MainActivity).apply { text = name; textSize = 15f * Apps.fs(this@MainActivity); setTextColor(Color.WHITE); setPadding(dp(8), 0, dp(10), 0) })
        addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (connected) 0xFF3DDC84.toInt() else 0x66FFFFFF) } }, LinearLayout.LayoutParams(dp(8), dp(8)))
        setOnClickListener {
            val uri = if (passthrough) android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId)
                      else android.media.tv.TvContract.buildChannelsUriForInput(inputId)
            if (!safeStart(this@MainActivity, Intent(Intent.ACTION_VIEW, uri)))
                Toast.makeText(this@MainActivity, "Không chuyển được sang $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Chip "MiTV": mở thẳng ứng dụng xem TV/nguồn tín hiệu của Xiaomi khi hệ thống không liệt kê đầu vào MiTV riêng
    private fun mitvAppChip(): View? {
        val pm = packageManager
        val intent = MITV_PKGS.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) } ?: return null
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
            setPadding(dp(14), dp(10), dp(16), dp(10)); background = chipBg(this@MainActivity)
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_input) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            addView(TextView(this@MainActivity).apply { text = "MiTV"; textSize = 15f * Apps.fs(this@MainActivity); setTextColor(Color.WHITE); setPadding(dp(8), 0, dp(10), 0) })
            setOnClickListener { if (!safeStart(this@MainActivity, intent)) Toast.makeText(this@MainActivity, "Không mở được MiTV", Toast.LENGTH_SHORT).show() }
        }
    }

    // Liệt kê các cổng vào TV báo cho hệ thống: HDMI 1/2/3, VGA, AV... Chấm xanh = đang có thiết bị cắm
    private fun refreshInputs() {
        inputRow.removeAllViews()
        val showMitv = Apps.bool(this, "mitv", false)
        var mitvFound = false
        val list = try {
            val tm = getSystemService(Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
            tm.tvInputList
                .filter { it.isPassthroughInput || (showMitv && isMitvInput(it)) }
                .also { l -> mitvFound = showMitv && l.any { isMitvInput(it) } }
                .map { Entry(it.id, inputName(it), try { tm.getInputState(it.id) <= 1 } catch (e: Exception) { false }, it.isPassthroughInput) }
                .sortedWith(compareBy<Entry>({ !it.name.startsWith("HDMI", true) }, { it.name.lowercase() }))
        } catch (e: Exception) { emptyList() }
        val extra = if (showMitv && !mitvFound) mitvAppChip() else null
        if (list.isEmpty() && extra == null) {
            inputRow.addView(TextView(this).apply { text = "Không thấy cổng vào. Hãy dùng phím Nguồn/Input trên điều khiển."; textSize = 13f; setTextColor(0xCCFFFFFF.toInt()); setPadding(dp(12), dp(8), 0, dp(8)) })
            return
        }
        list.forEach { inputRow.addView(inputChip(it.name, it.connected, it.id, it.passthrough), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }) }
        extra?.let { inputRow.addView(it, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }) }
    }

    private class Entry(val id: String, val name: String, val connected: Boolean, val passthrough: Boolean)

    // Đầu vào do dịch vụ của Xiaomi/MiTV cung cấp (không phải cổng HDMI/AV thông thường)
    private fun isMitvInput(i: android.media.tv.TvInputInfo): Boolean {
        val pkg = i.serviceInfo.packageName.lowercase()
        return pkg.contains("mitv") || pkg.contains("xiaomi")
    }

    // ===== Mục tuỳ chỉnh: mỗi mục (do người dùng tạo, đổi tên được) là một hàng gồm tên mục + các ô ứng dụng, nằm trên hàng Yêu thích =====
    // Quản lý mục (tạo, đổi tên, thêm/bỏ/sắp xếp ứng dụng, đổi cỡ, ẩn) nằm trong Sections.kt
    private fun refreshSecs() {
        // Nhớ ô đang chọn (mục + vị trí) để dựng lại xong thì chọn lại đúng chỗ đó
        var ft: String? = null
        var f: View? = currentFocus
        while (f != null && ft == null) { ft = (f.tag as? String)?.takeIf { it.startsWith("sec:") }; f = f.parent as? View }
        secCards.clear(); secBox.removeAllViews()
        val all = Apps.load(this).associateBy { it.pkg }
        val showPlus = Apps.bool(this, "sec_plus", true)
        for (s in Secs.load(this)) {
            if (Apps.isHidden(this, Secs.key(s.id))) continue
            val mult = Secs.mult(this, s.id)
            val row = LinearLayout(this)
            val apps = s.pkgs.mapNotNull { all[it] }
            apps.forEachIndexed { i, a ->
                val v = appView(this, a, false, mult) { applyAll() }
                v.tag = "sec:${s.id}:$i"
                // Giữ OK trên ứng dụng trong mục: menu riêng của mục (chuyển trái/phải, bỏ khỏi mục...), trong đó vẫn có menu ứng dụng cũ
                v.setOnLongClickListener { secAppMenu(this, s.id, a) { applyAll() }; true }
                secCards.add(v); row.addView(v)
            }
            // Mục trống thì luôn hiện ô +, để vẫn thêm được ứng dụng bằng điều khiển
            if (showPlus || apps.isEmpty()) {
                val p = plusTile(this, mult) { secAddApps(this, s.id) { applyAll() } }
                p.tag = "sec:${s.id}:+"
                secCards.add(p); row.addView(p)
            }
            val menu = { secMenu(this, s.id) { applyAll() } }
            val block = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(14))
                addView(rowTitle(s.name, Secs.key(s.id), s.name, menu) { row }, LinearLayout.LayoutParams(-2, -2))
                addView(HorizontalScrollView(this@MainActivity).apply { isFocusable = false; isHorizontalScrollBarEnabled = false; addView(row) })
            }
            secBox.addView(block)
        }
        secBox.visibility = if (secBox.childCount == 0) View.GONE else View.VISIBLE
        // Chọn lại ô vừa dùng; nếu ô đó đã bị bỏ thì chọn ô gần nhất trong cùng mục
        val t = ft
        if (t != null) {
            val prefix = t.substringBeforeLast(":") + ":"
            val same = secCards.filter { (it.tag as? String)?.startsWith(prefix) == true }
            val want = t.substringAfterLast(":").toIntOrNull()
            val target = secCards.firstOrNull { it.tag == t } ?: if (want != null) same.getOrNull(minOf(want, same.size - 1)) else same.lastOrNull()
            target?.let { v -> v.post { v.requestFocus() } }
        }
    }

    private fun refreshFavs() {
        favRow.removeAllViews()
        val all = Apps.load(this).associateBy { it.pkg }
        Apps.favs(this).mapNotNull { all[it] }.forEach { favRow.addView(appView(this, it) { applyAll() }) }
    }

    // Nơi ở: ưu tiên tỉnh/thành người dùng đã chọn thủ công, nếu không thì dò theo IP (không cần GPS).
    // Thử lần lượt nhiều dịch vụ vì TV Xiaomi nội địa có thể chặn/lỗi một số trang
    private fun locate(): Triple<String, Double, Double> {
        Apps.str(this, "wx_lat")?.toDoubleOrNull()?.let { la -> Apps.str(this, "wx_lon")?.toDoubleOrNull()?.let { lo -> return Triple(Apps.str(this, "wx_place") ?: "?", la, lo) } }
        Apps.str(this, "wx_place")?.let { n -> VN_PLACES.firstOrNull { it.first == n }?.let { return it } }
        val now = System.currentTimeMillis()
        val old = Apps.str(this, "loc")?.split("|")?.takeIf { it.size == 3 }
        val ts = Apps.str(this, "loc_ts")?.toLongOrNull() ?: 0L
        if (old != null && now - ts < 6 * 3600000L) return Triple(old[0], old[1].toDouble(), old[2].toDouble())
        var r: Triple<String, Double, Double>? = null
        try { val j = JSONObject(httpGet("https://ipwho.is/")); if (j.has("latitude")) r = Triple(j.optString("city", "?"), j.getDouble("latitude"), j.getDouble("longitude")) } catch (e: Exception) {}
        if (r == null) try { val j = JSONObject(httpGet("https://ipapi.co/json/")); r = Triple(j.optString("city", "?"), j.getDouble("latitude"), j.getDouble("longitude")) } catch (e: Exception) {}
        if (r == null) try { val j = JSONObject(httpGet("https://ipinfo.io/json")); val l = j.getString("loc").split(","); r = Triple(j.optString("city", "?"), l[0].toDouble(), l[1].toDouble()) } catch (e: Exception) {}
        if (r == null) { if (old != null) return Triple(old[0], old[1].toDouble(), old[2].toDouble()); throw Exception("no location") }
        Apps.putStr(this, "loc", "${r.first.replace("|", " ")}|${r.second}|${r.third}"); Apps.putStr(this, "loc_ts", now.toString())
        return r
    }

    // Thời tiết: Open-Meteo (miễn phí, không cần key)
    private fun loadWeather() = thread {
        try {
            val (city, lat, lon) = locate()
            val w = JSONObject(httpGet("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true")).getJSONObject("current_weather")
            val t = w.getDouble("temperature").toInt(); val d = wmo(w.optInt("weathercode", -1))
            lastWeather = System.currentTimeMillis()
            runOnUiThread { weather.text = "$city · $t°C" + (if (d.isEmpty()) "" else " · $d") }
        } catch (e: Exception) { runOnUiThread { weather.text = "Không tải được thời tiết" } }
    }

    // Giọng nói: ưu tiên app đã chọn trong Cài đặt, rồi Kiki (nhận theo tên), rồi Google Assistant của hệ thống
    private fun voice() {
        val all = Apps.loadAll(this)
        val chosen = Apps.str(this, "voice_pkg")
        val pkg = chosen?.takeIf { p -> all.any { it.pkg == p } } ?: all.firstOrNull { it.label.contains("kiki", true) }?.pkg
            ?: all.firstOrNull { it.label.contains("小爱") || it.label.contains("xiaoai", true) || it.label.contains("xiao ai", true) }?.pkg
        if (pkg != null) { Apps.open(this, pkg); return }
        if (safeStart(this, Intent("android.intent.action.VOICE_ASSIST"), Intent(Intent.ACTION_VOICE_COMMAND), Intent(Intent.ACTION_ASSIST), Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))) return
        Toast.makeText(this, "Chưa có trợ lý giọng nói. Hãy cài Kiki, hoặc chọn trợ lý trong Cài đặt Home TV", Toast.LENGTH_LONG).show()
    }

    private fun iconBtn(res: Int, desc: String, f: () -> Unit) = ImageButton(this).apply {
        setImageResource(res); contentDescription = desc; background = focusBg(this@MainActivity); setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { f() }
    }
}

class AllAppsActivity : VActivity() {
    private lateinit var grid: GridView
    private var apps: List<AppItem> = emptyList()
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val me = this
        grid = GridView(this).apply {
            numColumns = 4 + Apps.int(me, "cols", 2); setBackgroundColor(0xEE000000.toInt()); setPadding(dp(40), dp(40), dp(40), dp(40))
            // Ô đang chọn: nền trắng mờ, thay cho màu cam mặc định của TV
            selector = GradientDrawable().apply { cornerRadius = dp(Apps.radius(me) + 4).toFloat(); setColor(0x33FFFFFF) }
            // Bấm OK / chạm = mở ứng dụng, giữ lâu = menu tuỳ chỉnh
            setOnItemClickListener { _, _, p, _ -> if (p in apps.indices) openApp(me, apps[p]) }
            setOnItemLongClickListener { _, _, p, _ -> if (p in apps.indices) showMenu(me, apps[p]) { fill() }; true }
            // Chạy vòng: PHẢI ở cuối hàng -> xuống đầu hàng kế (ô cuối cùng -> về ô đầu tiên);
            // TRÁI ở đầu hàng -> lên cuối hàng trên (ô đầu tiên -> về ô cuối cùng)
            val cols = 4 + Apps.int(me, "cols", 2)
            setOnKeyListener { _, code, e ->
                val n = apps.size; val p = selectedItemPosition
                if (e.action == KeyEvent.ACTION_DOWN && n > 1 && p in 0 until n) {
                    val t = when {
                        code == KeyEvent.KEYCODE_DPAD_RIGHT && (p % cols == cols - 1 || p == n - 1) -> (p + 1) % n
                        code == KeyEvent.KEYCODE_DPAD_LEFT && p % cols == 0 -> (p - 1 + n) % n
                        else -> -1
                    }
                    if (t >= 0) { setSelection(t); true } else false
                } else false
            }
            setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, v: View?, p: Int, id: Long) { if (p in apps.indices && Apps.bool(me, "tts", false)) Speaker.say(me, apps[p].label) }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
        }
        setContentView(grid)
    }
    override fun onResume() { super.onResume(); fill() }
    private fun fill() {
        apps = Apps.load(this)
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup) = appView(this@AllAppsActivity, apps[p], true) { fill() }
        }
    }
}

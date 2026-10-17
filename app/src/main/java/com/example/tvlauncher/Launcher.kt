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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.StateListDrawable
import android.view.KeyEvent
import android.view.MotionEvent
import java.util.Locale
import org.json.JSONObject
import java.net.URL
import kotlin.concurrent.thread

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

/** Ép giao diện tiếng Việt, kể cả khi TV Xiaomi nội địa đang để tiếng Trung */
// Kích cỡ TV: hệ số phóng to/thu nhỏ toàn bộ giao diện. 55 inch là mốc chuẩn (1.0)
val TV_SIZES = arrayOf("Tự động (theo TV)", "32 inch", "43 inch", "50 inch", "55 inch", "65 inch", "75 inch", "85 inch", "98 inch")
val TV_FACTORS = floatArrayOf(1f, 0.82f, 0.92f, 0.97f, 1f, 1.1f, 1.2f, 1.3f, 1.45f)
val FINE_LABELS = arrayOf("80%", "90%", "100%", "110%", "120%", "130%")
val FINE_FACTORS = floatArrayOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f)

fun uiScale(c: Context): Float {
    val sp = c.getSharedPreferences("cfg", 0)
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

fun isXiaomi() = Build.MANUFACTURER.equals("xiaomi", true) || Build.BRAND.equals("xiaomi", true) || Build.BRAND.equals("redmi", true)

/** Thử lần lượt từng intent, intent nào mở được thì dừng. TV Xiaomi nội địa hay thiếu màn hình hệ thống chuẩn */
fun safeStart(c: Context, vararg list: Intent?): Boolean {
    for (i in list) {
        if (i == null) continue
        try { if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); return true } catch (e: Exception) {}
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
    c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "TVLauncher/1.3")
    try { return c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
}

fun decodeScaled(c: Context, u: Uri, mw: Int, mh: Int): Bitmap? = try {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / (s * 2) >= mw && o.outHeight / (s * 2) >= mh) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }
    c.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) }
} catch (e: Throwable) { null }

fun focusBg(c: Context): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(12).toFloat(); setColor(0x55FFFFFF) })
    addState(intArrayOf(), GradientDrawable().apply { setColor(0) })
}

fun chipBg(c: Context): StateListDrawable = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0x66FFFFFF); setStroke(c.dp(2), Color.WHITE) })
    addState(intArrayOf(), GradientDrawable().apply { cornerRadius = c.dp(16).toFloat(); setColor(0x44000000); setStroke(c.dp(1), 0x88FFFFFF.toInt()) })
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
    fun radius(c: Context) = intArrayOf(0, 12, 32)[int(c, "radius", 1)]
    fun tz(c: Context): String? = arrayOf<String?>(null, "Asia/Ho_Chi_Minh", "UTC", "Asia/Tokyo", "America/New_York", "Asia/Shanghai")[int(c, "tz", 1)]
    fun open(c: Context, pkg: String) {
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
fun appView(c: Context, a: AppItem, inGrid: Boolean = false, onChange: () -> Unit): View = LinearLayout(c).apply {
    val sc = Apps.scale(c, a.pkg)
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
    setPadding(c.dp(12), c.dp(12), c.dp(12), c.dp(12))
    // Trong lưới "Tất cả ứng dụng" ô không tự nhận phím/chạm, GridView lo việc bấm và giữ
    isFocusable = !inGrid; isClickable = !inGrid
    addView(ImageView(c).apply { setImageDrawable(a.icon); layoutParams = LinearLayout.LayoutParams(c.dp((72 * sc).toInt()), c.dp((72 * sc).toInt()))
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, c.dp(Apps.radius(c)).toFloat()) } } })
    addView(TextView(c).apply { text = a.label; setTextColor(Color.WHITE); textSize = 13f * sc * Apps.fs(c); if (Apps.bool(c, "hc", false)) typeface = android.graphics.Typeface.DEFAULT_BOLD; maxLines = 1; gravity = Gravity.CENTER })
    setOnFocusChangeListener { v, f ->
        v.scaleX = if (f && !Apps.rm(c)) 1.12f else 1f; v.scaleY = v.scaleX
        val hc = Apps.bool(c, "hc", false)
        v.background = if (f) GradientDrawable().apply { cornerRadius = c.dp(Apps.radius(c) + 4).toFloat(); setColor(if (hc) 0x66FFFFFF else 0x33FFFFFF); if (hc) setStroke(c.dp(3), Color.WHITE) } else null
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

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        scaleKey = (uiScale(this) * 1000).toInt()
        val root = FrameLayout(this)
        bg = FrameLayout(this); tint = View(this)
        root.addView(bg, -1, -1); root.addView(tint, -1, -1)

        // Trái: trợ lý giọng nói + lưới tất cả ứng dụng
        val left = LinearLayout(this).apply {
            addView(iconBtn(R.drawable.ic_mic, "Trợ lý giọng nói") { voice() })
            addView(iconBtn(R.drawable.ic_apps, "Tất cả ứng dụng") { safeStart(this@MainActivity, Intent(this@MainActivity, AllAppsActivity::class.java)) })
        }
        root.addView(left, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(40), dp(24), 0, 0) })

        // Phải trên cùng: Wi-Fi + cài đặt. Đứng yên một chỗ, không bị ô giờ/thời tiết ảnh hưởng
        val icons = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(iconBtn(R.drawable.ic_wifi, "Wi-Fi") { if (!safeStart(this@MainActivity, Intent(Settings.ACTION_WIFI_SETTINGS))) openSystemSettings(this@MainActivity) })
            addView(iconBtn(R.drawable.ic_settings, "Cài đặt") { settingsMenu() })
        }
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
            setPadding(dp(16), dp(10), dp(16), dp(12)); background = chipBg(this@MainActivity)
            addView(timeRow); addView(weather)
            setOnClickListener { boxSettings() }
            setOnLongClickListener { boxMenu(); true }
        }
        root.addView(cwBox, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(84), dp(40), 0) })
        // Chọn ô rồi bấm phím TRÁI để thu nhỏ, PHẢI để phóng lớn (hoặc kéo trái/phải bằng chuột, cảm ứng)
        attachBoxResize()

        // Yêu thích
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rowTitle("Yêu thích", "@favs", "Hàng yêu thích"))
            favRow = LinearLayout(this@MainActivity)
            addView(HorizontalScrollView(this@MainActivity).apply { addView(favRow) })
            setOnLongClickListener { blockMenu(this@MainActivity, "@favs", "Hàng yêu thích") { applyAll() }; true }
        }
        favBox = box

        // Đầu vào: HDMI 1/2/3, VGA, AV... mặc định nằm DƯỚI hàng Yêu thích (đổi lên/xuống được, xem applyOrder)
        val ibox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(rowTitle("Đầu vào", "@inputs", "Hàng đầu vào"))
            inputRow = LinearLayout(this@MainActivity)
            addView(HorizontalScrollView(this@MainActivity).apply { addView(inputRow) })
            setOnLongClickListener { blockMenu(this@MainActivity, "@inputs", "Hàng đầu vào") { applyAll() }; true }
        }
        inputBox = ibox
        bottomCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(bottomCol, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(40), 0, dp(40), dp(40)) })
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
        v.setOnKeyListener { _, code, e ->
            if (!Apps.bool(this, "cw_lock", false) && e.action == KeyEvent.ACTION_DOWN && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                change(Apps.scale(this, "@clock") + (if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 0.1f else -0.1f)); true
            } else false
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
        sp.setSelection(names.indexOf(Apps.str(c, "wx_place")).coerceAtLeast(0))
        col.addView(sp)
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
        AlertDialog.Builder(c).setTitle("Giờ và thời tiết").setView(col)
            .setPositiveButton("Lưu") { _, _ ->
                val i = sp.selectedItemPosition
                if (i == 0) Apps.del(c, "wx_place") else Apps.putStr(c, "wx_place", names[i])
                Apps.putBool(c, "cw_lock", lockCb.isChecked)
                weather.text = "Đang tải thời tiết…"
                loadWeather()
                Toast.makeText(c, "Đã lưu", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Huỷ") { _, _ -> revert() }
            .setOnCancelListener { revert() }
            .show()
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
    private fun rowTitle(label: String, key: String, name: String) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f
        setPadding(dp(12), dp(4), dp(12), dp(4)); isFocusable = true; isClickable = true
        background = focusBg(this@MainActivity)
        setOnClickListener { blockMenu(this@MainActivity, key, name) { applyAll() } }
        setOnLongClickListener { blockMenu(this@MainActivity, key, name) { applyAll() }; true }
    }

    // Sắp xếp hai hàng dưới cùng: mặc định Yêu thích ở trên, Đầu vào ở dưới
    private fun applyOrder() {
        val above = Apps.inputsAbove(this)
        val first = if (above) inputBox else favBox
        val second = if (above) favBox else inputBox
        if (bottomCol.childCount != 2 || bottomCol.getChildAt(0) !== first) {
            bottomCol.removeAllViews(); bottomCol.addView(first); bottomCol.addView(second)
        }
        val gap = if (Apps.isHidden(this, "@favs") || Apps.isHidden(this, "@inputs")) 0 else dp(14)
        first.setPadding(0, 0, 0, gap); second.setPadding(0, 0, 0, 0)
    }

    private fun settingsMenu() {
        AlertDialog.Builder(this).setTitle("Cài đặt").setItems(arrayOf("Cài đặt Home TV", "Cài đặt hệ thống của TV")) { _, i ->
            if (i == 0) guard(this) { safeStart(this, Intent(this, SettingsActivity::class.java)) } else guard(this) { openSystemSettings(this) }
        }.show()
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
                    val bmp = decodeScaled(this, u, 1920, 1080)
                    runOnUiThread { if (bmp != null) iv.setImageBitmap(bmp) else Toast.makeText(this, "Không mở được ảnh nền", Toast.LENGTH_SHORT).show() }
                }
            }
        } catch (e: Exception) { Toast.makeText(this, "Không mở được nền", Toast.LENGTH_SHORT).show() }
    }


    private fun applyAll() {
        applyBackground()
        tint.setBackgroundColor(if (Apps.bool(this, "hc", false)) 0xAA000000.toInt() else intArrayOf(0x00000000, 0x88102A5A.toInt(), 0x884A1A6A.toInt(), 0x886A1A1A.toInt(), 0x881A4A2A.toInt())[Apps.int(this, "color", 0)])
        (bottomCol.layoutParams as FrameLayout.LayoutParams).bottomMargin = dp(intArrayOf(20, 40, 80)[Apps.int(this, "cat_h", 1)])
        bottomCol.requestLayout()
        lunar.visibility = if (Apps.bool(this, "lunar", true)) View.VISIBLE else View.GONE
        applyTexts()
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

    private fun inputChip(name: String, connected: Boolean, inputId: String): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
        setPadding(dp(14), dp(10), dp(16), dp(10)); background = chipBg(this@MainActivity)
        addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.ic_input) }, LinearLayout.LayoutParams(dp(22), dp(22)))
        addView(TextView(this@MainActivity).apply { text = name; textSize = 15f * Apps.fs(this@MainActivity); setTextColor(Color.WHITE); setPadding(dp(8), 0, dp(10), 0) })
        addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (connected) 0xFF3DDC84.toInt() else 0x66FFFFFF) } }, LinearLayout.LayoutParams(dp(8), dp(8)))
        setOnClickListener {
            if (!safeStart(this@MainActivity, Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput(inputId))))
                Toast.makeText(this@MainActivity, "Không chuyển được sang $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Liệt kê các cổng vào TV báo cho hệ thống: HDMI 1/2/3, VGA, AV... Chấm xanh = đang có thiết bị cắm
    private fun refreshInputs() {
        inputRow.removeAllViews()
        val list = try {
            val tm = getSystemService(Context.TV_INPUT_SERVICE) as android.media.tv.TvInputManager
            tm.tvInputList.filter { it.isPassthroughInput }
                .map { Triple(it.id, inputName(it), try { tm.getInputState(it.id) <= 1 } catch (e: Exception) { false }) }
                .sortedWith(compareBy<Triple<String, String, Boolean>>({ !it.second.startsWith("HDMI", true) }, { it.second.lowercase() }))
        } catch (e: Exception) { emptyList() }
        if (list.isEmpty()) {
            inputRow.addView(TextView(this).apply { text = "Không thấy cổng vào. Hãy dùng phím Nguồn/Input trên điều khiển."; textSize = 13f; setTextColor(0xCCFFFFFF.toInt()); setPadding(dp(12), dp(8), 0, dp(8)) })
            return
        }
        list.forEach { inputRow.addView(inputChip(it.second, it.third, it.first), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) }) }
    }

    private fun refreshFavs() {
        favRow.removeAllViews()
        val all = Apps.load(this).associateBy { it.pkg }
        Apps.favs(this).mapNotNull { all[it] }.forEach { favRow.addView(appView(this, it) { applyAll() }) }
    }

    // Nơi ở: ưu tiên tỉnh/thành người dùng đã chọn thủ công, nếu không thì dò theo IP (không cần GPS).
    // Thử lần lượt nhiều dịch vụ vì TV Xiaomi nội địa có thể chặn/lỗi một số trang
    private fun locate(): Triple<String, Double, Double> {
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
            val hc = Apps.bool(me, "hc", false)
            // Ô đang chọn: nền trắng mờ (viền trắng khi bật tương phản cao), thay cho màu cam mặc định của TV
            selector = GradientDrawable().apply { cornerRadius = dp(Apps.radius(me) + 4).toFloat(); setColor(if (hc) 0x66FFFFFF else 0x33FFFFFF); if (hc) setStroke(dp(3), Color.WHITE) }
            // Bấm OK / chạm = mở ứng dụng, giữ lâu = menu tuỳ chỉnh
            setOnItemClickListener { _, _, p, _ -> if (p in apps.indices) openApp(me, apps[p]) }
            setOnItemLongClickListener { _, _, p, _ -> if (p in apps.indices) showMenu(me, apps[p]) { fill() }; true }
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

package com.example.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Chặn quay về giao diện gốc của TV (ví dụ PatchWall tiếng Trung của Xiaomi).
 *  Android không cho chặn thẳng, nên dùng dịch vụ trợ năng: khi giao diện gốc hiện lên thì đưa ngay về TV Launcher.
 *  Cài đặt lưu ở khoá "hg_on" (bật/tắt) và "hg_off" (các gói được tích BỎ chặn, cách nhau bằng dấu phẩy) */
object HomeGuard {
    @Volatile private var allowUntil = 0L
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var cacheAt = 0L

    /** Gọi khi chính người dùng mở ứng dụng/đầu vào từ TV Launcher: tạm không chặn vài giây để không chặn nhầm lựa chọn của họ */
    fun allow(ms: Long = 4000L) { allowUntil = maxOf(allowUntil, SystemClock.uptimeMillis() + ms) }
    fun isAllowed() = SystemClock.uptimeMillis() < allowUntil

    // Nhật ký ngắn để kiểm tra vì sao chặn chưa hiệu quả (xem ở mục "Kiểm tra chặn giao diện gốc" trong Cài đặt)
    private val logLines = java.util.ArrayDeque<String>()
    @Volatile var homeKeys = 0
    fun note(s: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        synchronized(logLines) { logLines.addFirst("$t $s"); while (logLines.size > 12) logLines.removeLast() }
    }
    fun notes(): List<String> = synchronized(logLines) { logLines.toList() }

    /** Hết thời gian tạm dừng chặn (vd người dùng đã quay lại từ màn hình hệ thống) */
    fun resume() { allowUntil = 0L }

    /** Dịch vụ trợ năng của TV Launcher đang thật sự chạy (hệ thống đã kết nối dịch vụ) */
    fun running(): Boolean = HomeGuardService.connected

    /** Gói của hệ thống (cài đặt, cấp quyền, cài ứng dụng, thanh hệ thống, trình cài đặt lần đầu): không bao giờ chặn,
     *  nếu không người dùng sẽ bị đá ra giữa chừng khi đang đổi giao diện mặc định hoặc cấp quyền */
    private val SAFE_PARTS = listOf("setup", "provision", "settings", "permissioncontroller", "packageinstaller", "systemui")
    fun isSystemPkg(p: String) = p == "android" || SAFE_PARTS.any { p.contains(it, true) }

    /** Báo danh sách chặn đã đổi, lần sau đọc lại */
    fun refresh() { cacheAt = 0L }

    /** Các giao diện chính (Home) khác trên TV: (gói, tên). Không gồm TV Launcher, trình cài đặt lần đầu và các gói hệ thống (cài đặt, cấp quyền...) */
    fun homeApps(c: Context): List<Pair<String, String>> = try {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(i, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
            .filter { it != c.packageName && !isSystemPkg(it) }
            .map { p -> p to (try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }) }
            .sortedBy { it.second.lowercase() }
    } catch (e: Exception) { emptyList() }

    /** Các gói người dùng đã bỏ tích (không chặn) */
    fun excluded(c: Context): Set<String> = (Apps.str(c, "hg_off") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    /** Các gói đang bị chặn. Lưu tạm 30 giây cho đỡ tốn máy */
    fun blocked(c: Context): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (cacheAt == 0L || now - cacheAt > 30000L) {
            cache = homeApps(c).map { it.first }.filter { it !in excluded(c) }.toSet(); cacheAt = now
        }
        return cache
    }

    /** Đã được cấp quyền WRITE_SECURE_SETTINGS chưa (chỉ cấp được bằng ADB, làm một lần: adb shell pm grant <gói> android.permission.WRITE_SECURE_SETTINGS) */
    fun canWriteSecure(c: Context): Boolean = try {
        c.packageManager.checkPermission("android.permission.WRITE_SECURE_SETTINGS", c.packageName) == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }

    /** Tự bật dịch vụ trợ năng, giữ nguyên các dịch vụ khác đang bật. Cần quyền WRITE_SECURE_SETTINGS. Trả về true nếu dịch vụ đã bật */
    fun enableService(c: Context): Boolean {
        if (serviceEnabled(c)) return true
        if (!canWriteSecure(c)) return false
        return try {
            val me = ComponentName(c, HomeGuardService::class.java).flattenToString()
            val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val merged = (cur.split(":").filter { it.isNotEmpty() } + me).distinct().joinToString(":")
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged)
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            serviceEnabled(c)
        } catch (e: Exception) { false }
    }

    /** Đang bật chặn mà dịch vụ trợ năng bị tắt (TV Xiaomi hay tự tắt) thì tự bật lại, nếu đã có quyền */
    fun ensure(c: Context) {
        if ((Apps.bool(c, "hg_on", false) || Apps.bool(c, "vg_on", false)) && !serviceEnabled(c)) enableService(c)
    }

    /** Dịch vụ trợ năng của TV Launcher đã được bật trong cài đặt hệ thống chưa */
    fun serviceEnabled(c: Context): Boolean = try {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val me = ComponentName(c, HomeGuardService::class.java)
        s.split(":").any { it.isNotEmpty() && ComponentName.unflattenFromString(it) == me }
    } catch (e: Exception) { false }
}

/** Chặn trợ lý giọng nói gốc của TV (ví dụ Tiểu Ái / Xiao Ai tiếng Trung của Xiaomi): bấm nút micro trên điều khiển thì mở trợ lý thay thế.
 *  Dùng chung dịch vụ trợ năng với HomeGuard. Cài đặt lưu ở khoá "vg_on" (bật/tắt) và "vg_off" (các gói được tích BỎ chặn, cách nhau bằng dấu phẩy) */
object VoiceGuard {
    @Volatile private var skipUntil = 0L
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var cacheAt = 0L

    fun on(c: Context) = Apps.bool(c, "vg_on", false)

    /** Tạm bỏ qua sự kiện vài giây sau khi chính mình mở trợ lý thay thế, để không lặp vô hạn */
    fun skip(ms: Long = 4000L) { skipUntil = SystemClock.uptimeMillis() + ms }
    fun skipping() = SystemClock.uptimeMillis() < skipUntil
    fun refresh() { cacheAt = 0L }

    private val CN_LABELS = listOf("小爱", "语音", "xiaoai", "xiao ai")
    private val CN_PKGS = listOf("xiaoai", "voiceassist", "voice.assist", "mitv.voice", "xiaomi.voice")

    private fun isXiaoAi(label: String, pkg: String) = CN_LABELS.any { label.contains(it, true) } || CN_PKGS.any { pkg.contains(it, true) }

    /** Trợ lý thay thế sẽ được mở: đã chọn ở mục "Trợ lý giọng nói" > Kiki. null = chưa có (sẽ thử Google Assistant) */
    fun replacement(c: Context): String? {
        val all = Apps.loadAll(c)
        val chosen = Apps.str(c, "voice_pkg")
        return chosen?.takeIf { p -> all.any { it.pkg == p } } ?: all.firstOrNull { it.label.contains("kiki", true) }?.pkg
    }

    /** Các trợ lý giọng nói trên TV có thể chặn: (gói, tên). Không gồm TV Launcher, Google, gói hệ thống và trợ lý thay thế */
    fun assistants(c: Context): List<Pair<String, String>> = try {
        val pm = c.packageManager
        val pk = LinkedHashSet<String>()
        listOf(Intent("android.intent.action.VOICE_ASSIST"), Intent(Intent.ACTION_VOICE_COMMAND), Intent(Intent.ACTION_ASSIST)).forEach { i ->
            pm.queryIntentActivities(i, 0).forEach { r -> r.activityInfo?.packageName?.let { pk.add(it) } }
        }
        pm.queryIntentServices(Intent("android.service.voice.VoiceInteractionService"), 0).forEach { r -> r.serviceInfo?.packageName?.let { pk.add(it) } }
        Apps.loadAll(c).forEach { a -> if (isXiaoAi(a.label, a.pkg)) pk.add(a.pkg) }
        val rep = replacement(c)
        pk.filter { it != c.packageName && it != rep && !it.startsWith("com.google.") && !HomeGuard.isSystemPkg(it) }
            .map { p -> p to (try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (e: Exception) { p }) }
            .filter { !it.second.contains("kiki", true) }
            .sortedBy { it.second.lowercase() }
    } catch (e: Exception) { emptyList() }

    /** Các gói người dùng đã bỏ tích (không chặn) */
    fun excluded(c: Context): Set<String> = (Apps.str(c, "vg_off") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    /** Các gói đang bị chặn. Lưu tạm 30 giây cho đỡ tốn máy */
    fun blocked(c: Context): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (cacheAt == 0L || now - cacheAt > 30000L) {
            cache = assistants(c).map { it.first }.filter { it !in excluded(c) }.toSet(); cacheAt = now
        }
        return cache
    }

    /** Mở trợ lý giọng nói: đã chọn trong Cài đặt > Kiki > Google Assistant. Khi CHƯA bật chặn còn thử thêm Tiểu Ái và các lối mặc định của hệ thống.
     *  Trả về true nếu đã mở được. Dùng cho cả nút micro trong TV Launcher lẫn nút micro trên điều khiển */
    fun launch(c: Context): Boolean {
        val rep = replacement(c)
        if (rep != null) { Apps.open(c, rep); return true }
        val pm = c.packageManager
        for (g in listOf("com.google.android.googlequicksearchbox", "com.google.android.katniss")) {
            for (a in listOf("android.intent.action.VOICE_ASSIST", Intent.ACTION_ASSIST)) {
                val i = Intent(a).setPackage(g)
                if (pm.resolveActivity(i, 0) != null && safeStart(c, i)) return true
            }
        }
        if (!on(c)) {
            Apps.loadAll(c).firstOrNull { isXiaoAi(it.label, it.pkg) }?.let { Apps.open(c, it.pkg); return true }
            if (safeStart(c, Intent("android.intent.action.VOICE_ASSIST"), Intent(Intent.ACTION_VOICE_COMMAND), Intent(Intent.ACTION_ASSIST), Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))) return true
        }
        return false
    }
}

class HomeGuardService : AccessibilityService() {
    private var last = 0L

    companion object {
        /** true khi hệ thống đã kết nối dịch vụ (dùng để báo "bật chặn thành công") */
        @Volatile var connected = false
    }

    override fun onServiceConnected() { super.onServiceConnected(); connected = true }
    override fun onUnbind(intent: Intent?): Boolean { connected = false; return super.onUnbind(intent) }
    override fun onDestroy() { connected = false; super.onDestroy() }

    private var homeDownTaken = false    // phím Home đang nhấn xuống đã bị nuốt => lúc nhả cũng phải nuốt, để dòng sự kiện phím không bị lệch
    private var voiceDownTaken = false   // tương tự cho phím micro
    private var lastVoice = 0L

    // Phím micro / trợ lý trên điều khiển (tuỳ điều khiển gửi mã nào) và phím không cần ghi vào nhật ký
    private val VOICE_KEYS = setOf(KeyEvent.KEYCODE_VOICE_ASSIST, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_SEARCH)
    private val QUIET_KEYS = setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
    private val VOICE_CLS = listOf("voice", "assist", "xiaoai", "speech", "asr")

    /** Mở TV Launcher. Trả về true nếu mở được */
    private fun goLauncher(why: String): Boolean = try {
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        true
    } catch (x: Exception) { HomeGuard.note("$why: LỖI không mở được TV Launcher (${x.javaClass.simpleName})"); false }

    private fun isActivity(pkg: String, cls: String): Boolean =
        try { packageManager.getActivityInfo(ComponentName(pkg, cls), 0); true } catch (x: Exception) { false }

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        val k = ev.keyCode
        if (k == KeyEvent.KEYCODE_HOME) return onHomeKey(ev)
        if (k in VOICE_KEYS) return onVoiceKey(ev)
        // Ghi lại các phím lạ để biết nút micro của điều khiển gửi mã gì (xem ở mục "Kiểm tra chặn")
        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 && k !in QUIET_KEYS && (Apps.bool(this, "hg_on", false) || VoiceGuard.on(this)))
            HomeGuard.note("Phím khác: ${KeyEvent.keyCodeToString(k)} ($k)")
        return false
    }

    /** Bắt thẳng nút Home trên điều khiển: nếu nút Home sẽ mở một giao diện bị chặn thì nuốt phím và mở TV Launcher,
     *  giao diện gốc không kịp hiện lên. (Cần cờ flagRequestFilterKeyEvents trong home_guard.xml) */
    private fun onHomeKey(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_UP) { val t = homeDownTaken; homeDownTaken = false; return t }
        if (ev.repeatCount > 0) return homeDownTaken
        HomeGuard.homeKeys++
        homeDownTaken = false
        if (!Apps.bool(this, "hg_on", false)) { HomeGuard.note("Nút Home: bỏ qua (chặn đang tắt)"); return false }
        if (HomeGuard.isAllowed()) { HomeGuard.note("Nút Home: bỏ qua (đang tạm dừng chặn)"); return false }
        val cur = defaultHomePkg(this)
        if (cur == packageName) { HomeGuard.note("Nút Home: TV Launcher đã là mặc định, để hệ thống xử lý"); return false }
        if (cur != null && cur != "android" && cur !in HomeGuard.blocked(this)) { HomeGuard.note("Nút Home: giao diện mặc định $cur không bị chặn"); return false }
        homeDownTaken = goLauncher("Nút Home")
        if (homeDownTaken) HomeGuard.note("Nút Home: ĐÃ BẮT, mở TV Launcher (mặc định hiện tại: $cur)")
        return homeDownTaken
    }

    /** Bắt nút micro trên điều khiển: khi bật chặn voice gốc thì nuốt phím và mở trợ lý thay thế */
    private fun onVoiceKey(ev: KeyEvent): Boolean {
        if (ev.action == KeyEvent.ACTION_UP) { val t = voiceDownTaken; voiceDownTaken = false; return t }
        if (ev.repeatCount > 0) return voiceDownTaken
        voiceDownTaken = false
        val name = KeyEvent.keyCodeToString(ev.keyCode)
        if (!VoiceGuard.on(this)) { HomeGuard.note("Phím $name: bỏ qua (chặn voice đang tắt)"); return false }
        VoiceGuard.skip()
        val ok = VoiceGuard.launch(this)
        if (!ok) Toast.makeText(this, "Đã chặn voice gốc nhưng chưa có trợ lý thay thế. Hãy cài Kiki hoặc chọn trợ lý trong Cài đặt TV Launcher", Toast.LENGTH_LONG).show()
        HomeGuard.note("Phím $name: ĐÃ BẮT, " + (if (ok) "mở trợ lý thay thế" else "không có trợ lý thay thế"))
        voiceDownTaken = true   // vẫn nuốt phím để voice gốc không mở, dù chưa có trợ lý thay thế
        return true
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return
        val cls = e.className?.toString() ?: ""
        val now = SystemClock.uptimeMillis()

        // Trợ lý giọng nói gốc hiện lên (kể cả khi hiện kiểu cửa sổ nổi): mở trợ lý thay thế
        if (VoiceGuard.on(this) && pkg in VoiceGuard.blocked(this)) {
            val why = when {
                VoiceGuard.skipping() -> "bỏ qua (vừa mở trợ lý thay thế)"
                !(isActivity(pkg, cls) || VOICE_CLS.any { cls.contains(it, true) }) -> "bỏ qua (không phải màn hình trợ lý)"
                now - lastVoice < 1500L -> "bỏ qua (chống lặp)"
                else -> null
            }
            HomeGuard.note("Voice $pkg ${cls.substringAfterLast('.')}: " + (why ?: "CHẶN, mở trợ lý thay thế"))
            if (why == null) {
                lastVoice = now
                VoiceGuard.skip()
                if (!VoiceGuard.launch(this)) {
                    Toast.makeText(this, "Đã chặn voice gốc nhưng chưa có trợ lý thay thế. Hãy cài Kiki hoặc chọn trợ lý trong Cài đặt TV Launcher", Toast.LENGTH_LONG).show()
                    goLauncher("Voice")
                }
            }
            return
        }

        val why = when {
            !Apps.bool(this, "hg_on", false) -> "bỏ qua (chặn đang tắt)"
            HomeGuard.isAllowed() -> "bỏ qua (đang tạm dừng chặn)"
            HomeGuard.isSystemPkg(pkg) -> "bỏ qua (gói hệ thống)"
            pkg !in HomeGuard.blocked(this) -> "bỏ qua (không thuộc danh sách chặn)"
            // Chỉ tính khi là một màn hình (Activity) thật của gói đó, bỏ qua hộp thoại / thông báo nổi
            !isActivity(pkg, cls) -> "bỏ qua (không phải màn hình Activity)"
            now - last < 800L -> "bỏ qua (chống lặp)"   // chống lặp liên tục
            else -> null
        }
        HomeGuard.note("$pkg ${cls.substringAfterLast('.')}: " + (why ?: "KÉO VỀ TV Launcher"))
        if (why != null) return
        last = now
        goLauncher(pkg)
    }

    override fun onInterrupt() {}
}

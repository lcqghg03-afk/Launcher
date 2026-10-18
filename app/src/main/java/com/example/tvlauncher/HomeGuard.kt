package com.example.tvlauncher

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Chặn quay về giao diện gốc của TV (ví dụ PatchWall tiếng Trung của Xiaomi).
 *  Android không cho chặn thẳng, nên dùng dịch vụ trợ năng: khi giao diện gốc hiện lên thì đưa ngay về TV Launcher.
 *  Cài đặt lưu ở khoá "hg_on" (bật/tắt) và "hg_off" (các gói được tích BỎ chặn, cách nhau bằng dấu phẩy) */
object HomeGuard {
    @Volatile private var allowUntil = 0L
    @Volatile private var cache: Set<String> = emptySet()
    @Volatile private var cacheAt = 0L

    /** Gọi khi chính người dùng mở ứng dụng/đầu vào từ TV Launcher: tạm không chặn vài giây để không chặn nhầm lựa chọn của họ */
    fun allow(ms: Long = 4000L) { allowUntil = SystemClock.uptimeMillis() + ms }
    fun isAllowed() = SystemClock.uptimeMillis() < allowUntil

    /** Báo danh sách chặn đã đổi, lần sau đọc lại */
    fun refresh() { cacheAt = 0L }

    /** Các giao diện chính (Home) khác trên TV: (gói, tên). Không gồm TV Launcher và trình cài đặt lần đầu của máy */
    fun homeApps(c: Context): List<Pair<String, String>> = try {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(i, 0).mapNotNull { it.activityInfo?.packageName }.distinct()
            .filter { it != c.packageName && !it.contains("setup", true) && !it.contains("provision", true) }
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

    /** Dịch vụ trợ năng của TV Launcher đã được bật trong cài đặt hệ thống chưa */
    fun serviceEnabled(c: Context): Boolean = try {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val me = ComponentName(c, HomeGuardService::class.java)
        s.split(":").any { it.isNotEmpty() && ComponentName.unflattenFromString(it) == me }
    } catch (e: Exception) { false }
}

class HomeGuardService : AccessibilityService() {
    private var last = 0L

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Apps.bool(this, "hg_on", false) || HomeGuard.isAllowed()) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in HomeGuard.blocked(this)) return
        // Chỉ tính khi là một màn hình (Activity) thật của gói đó, bỏ qua hộp thoại / thông báo nổi
        val cls = e.className?.toString() ?: return
        try { packageManager.getActivityInfo(ComponentName(pkg, cls), 0) } catch (x: Exception) { return }
        // Chống lặp liên tục
        val now = SystemClock.uptimeMillis()
        if (now - last < 800L) return
        last = now
        try {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        } catch (x: Exception) {}
    }

    override fun onInterrupt() {}
}


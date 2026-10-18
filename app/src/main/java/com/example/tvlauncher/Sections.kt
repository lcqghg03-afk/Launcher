package com.example.tvlauncher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

/** Một mục do người dùng tự tạo trên màn hình chính: có tên (đổi được) và danh sách ứng dụng theo thứ tự hiển thị */
class Sec(val id: String, val name: String, val pkgs: List<String>)

/** Mục tuỳ chỉnh trên màn hình chính. Lưu ở khoá "secs" dạng JSON: [{"id","n","a":[gói ứng dụng...]}].
 *  Ẩn/hiện mục dùng khoá "@sec_<id>" trong danh sách ẩn chung (Apps.isHidden), cỡ biểu tượng lưu ở khoá "ssz_<id>" */
object Secs {
    const val MAX_NAME = 30
    val SIZE_LABELS = arrayOf("Rất nhỏ", "Nhỏ", "Vừa", "Lớn", "Rất lớn")
    private val MULT = floatArrayOf(0.6f, 0.8f, 1f, 1.25f, 1.5f)

    fun key(id: String) = "@sec_$id"

    fun load(c: Context): List<Sec> = try {
        val a = JSONArray(Apps.str(c, "secs") ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("id")
            if (id.isEmpty()) return@mapNotNull null
            val p = o.optJSONArray("a")
            val l = ArrayList<String>()
            if (p != null) for (k in 0 until p.length()) l.add(p.getString(k))
            Sec(id, o.optString("n"), l)
        }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, l: List<Sec>) {
        val a = JSONArray()
        l.forEach { s -> a.put(JSONObject().put("id", s.id).put("n", s.name).put("a", JSONArray(s.pkgs))) }
        Apps.putStr(c, "secs", a.toString())
    }

    fun get(c: Context, id: String): Sec? = load(c).firstOrNull { it.id == id }

    fun size(c: Context, id: String) = Apps.int(c, "ssz_$id", 2).coerceIn(0, MULT.size - 1)
    fun mult(c: Context, id: String) = MULT[size(c, id)]

    /** Sửa một mục rồi lưu lại: f nhận mục hiện tại và trả về mục mới */
    fun update(c: Context, id: String, f: (Sec) -> Sec) = save(c, load(c).map { if (it.id == id) f(it) else it })

    fun create(c: Context, name: String): Sec {
        val l = load(c)
        var id: String
        do { id = "s" + (System.currentTimeMillis() + (0..999).random()).toString(36) } while (l.any { it.id == id })
        val s = Sec(id, name, emptyList())
        save(c, l + s)
        return s
    }

    fun delete(c: Context, id: String) {
        save(c, load(c).filter { it.id != id })
        Apps.setHidden(c, key(id), false)
        Apps.del(c, "ssz_$id")
    }

    /** Đưa cả mục lên (-1) hoặc xuống (+1) một bậc */
    fun move(c: Context, id: String, delta: Int) {
        val l = load(c).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) return
        l.add(j, l.removeAt(i))
        save(c, l)
    }
}

private fun secToast(c: Context, m: String) = Toast.makeText(c, m, Toast.LENGTH_SHORT).show()
private fun secLabel(c: Context, pkg: String) = Apps.loadAll(c).firstOrNull { it.pkg == pkg }?.label ?: pkg

/** Hộp thoại nhập tên mục. ok nhận tên đã gọt gọn; cancel chạy khi bấm Huỷ hoặc Back */
private fun secNameDialog(c: Context, title: String, start: String, ok: (String) -> Unit, cancel: () -> Unit) {
    val et = EditText(c).apply { hint = "Tên mục (VD: Phim, Game, Nhạc)"; setSingleLine(true); setText(start); setSelection(text.length) }
    val dlg = GlassBuilder(c).setTitle(title)
        .setView(LinearLayout(c).apply { setPadding(c.dp(24), c.dp(8), c.dp(24), 0); addView(et, LinearLayout.LayoutParams(-1, -2)) })
        .setPositiveButton("Lưu", null).setNegativeButton("Huỷ") { _, _ -> cancel() }.setOnCancelListener { cancel() }.create()
    dlg.show(); glassify(dlg)
    dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val n = et.text.toString().replace(Regex("\\s+"), " ").trim().take(Secs.MAX_NAME)
        if (n.isEmpty()) { secToast(c, "Nhập tên mục"); return@setOnClickListener }
        dlg.dismiss(); ok(n)
    }
}

/** Danh sách mục + nút tạo mục mới. done chạy khi đóng hộp thoại (Cài đặt dùng để dựng lại danh sách) */
fun secListDialog(c: Context, done: () -> Unit) {
    val l = Secs.load(c)
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    l.forEach { s ->
        val hid = Apps.isHidden(c, Secs.key(s.id))
        labels.add(s.name + " · " + s.pkgs.size + " ứng dụng" + (if (hid) " · đang ẩn" else ""))
        acts.add { secMenu(c, s.id) { secListDialog(c, done) } }
    }
    labels.add("+ Tạo mục mới"); acts.add { secCreate(c) { secListDialog(c, done) } }
    GlassBuilder(c).setTitle("Mục trên màn hình chính (${l.size})")
        .setItems(labels.toTypedArray()) { _, i -> acts[i]() }
        .setNegativeButton("Đóng") { _, _ -> done() }.setOnCancelListener { done() }.show()
}

/** Tạo mục mới rồi chọn luôn ứng dụng cho mục đó */
fun secCreate(c: Context, back: () -> Unit) {
    secNameDialog(c, "Tạo mục mới", "", { n ->
        val s = Secs.create(c, n)
        secToast(c, "Đã tạo mục $n. Hãy chọn ứng dụng cho mục")
        secAddApps(c, s.id, back)
    }, back)
}

/** Tích chọn nhiều ứng dụng (chưa có trong mục) để thêm vào mục */
fun secAddApps(c: Context, id: String, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    val apps = Apps.load(c).filter { it.pkg !in s.pkgs }
    if (apps.isEmpty()) { secToast(c, "Không còn ứng dụng nào để thêm"); back(); return }
    val checked = BooleanArray(apps.size)
    GlassBuilder(c).setTitle("Thêm ứng dụng vào ${s.name}")
        .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, v -> checked[i] = v }
        .setPositiveButton("Thêm") { _, _ ->
            val sel = apps.filterIndexed { i, _ -> checked[i] }.map { it.pkg }
            if (sel.isEmpty()) secToast(c, "Chưa tích chọn ứng dụng nào")
            else { Secs.update(c, id) { Sec(it.id, it.name, it.pkgs + sel) }; secToast(c, "Đã thêm ${sel.size} ứng dụng") }
            back()
        }
        .setNegativeButton("Huỷ") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

/** Tích chọn ứng dụng để bỏ khỏi mục (chỉ bỏ khỏi mục, không gỡ cài đặt) */
fun secRemoveApps(c: Context, id: String, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    if (s.pkgs.isEmpty()) { secToast(c, "Mục này chưa có ứng dụng"); back(); return }
    val names = s.pkgs.map { secLabel(c, it) }.toTypedArray()
    val checked = BooleanArray(names.size)
    GlassBuilder(c).setTitle("Bỏ khỏi ${s.name} (không gỡ cài đặt)")
        .setMultiChoiceItems(names, checked) { _, i, v -> checked[i] = v }
        .setPositiveButton("Bỏ khỏi mục") { _, _ ->
            val keep = s.pkgs.filterIndexed { i, _ -> !checked[i] }
            val n = s.pkgs.size - keep.size
            if (n == 0) secToast(c, "Chưa tích chọn ứng dụng nào")
            else { Secs.update(c, id) { Sec(it.id, it.name, keep) }; secToast(c, "Đã bỏ $n ứng dụng khỏi mục") }
            back()
        }
        .setNegativeButton("Huỷ") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

/** Dời ứng dụng từ vị trí from sang vị trí to trong mục */
fun secMoveApp(c: Context, id: String, from: Int, to: Int) {
    Secs.update(c, id) {
        val l = it.pkgs.toMutableList()
        if (from in l.indices && to in l.indices) l.add(to, l.removeAt(from))
        Sec(it.id, it.name, l)
    }
}

/** Sắp xếp thứ tự ứng dụng: chọn một ứng dụng rồi chuyển trái/phải/đầu/cuối, làm liên tục cho tới khi bấm Xong */
fun secOrderDialog(c: Context, id: String, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    if (s.pkgs.size < 2) { secToast(c, "Cần ít nhất 2 ứng dụng để sắp xếp"); back(); return }
    val names = s.pkgs.mapIndexed { i, p -> "${i + 1}. ${secLabel(c, p)}" }.toTypedArray()
    GlassBuilder(c).setTitle("Sắp xếp ứng dụng trong ${s.name}")
        .setItems(names) { _, i -> secMoveMenu(c, id, i) { secOrderDialog(c, id, back) } }
        .setNegativeButton("Xong") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

private fun secMoveMenu(c: Context, id: String, i: Int, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    val n = s.pkgs.size
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    fun row(t: String, to: Int) { labels.add(t); acts.add { secMoveApp(c, id, i, to); back() } }
    if (i > 0) { row("Chuyển lên đầu", 0); row("Chuyển sang trái một ô", i - 1) }
    if (i < n - 1) { row("Chuyển sang phải một ô", i + 1); row("Chuyển xuống cuối", n - 1) }
    GlassBuilder(c).setTitle(secLabel(c, s.pkgs[i])).setItems(labels.toTypedArray()) { _, k -> acts[k]() }
        .setNegativeButton("Quay lại") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

/** Cỡ biểu tượng ứng dụng trong mục (thật sự đổi cỡ, các hàng bên dưới tự dịch chỗ) */
fun secSizeDialog(c: Context, id: String, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    GlassBuilder(c).setTitle("Kích thước biểu tượng: ${s.name}")
        .setSingleChoiceItems(Secs.SIZE_LABELS, Secs.size(c, id)) { d, i -> Apps.putInt(c, "ssz_$id", i); d.dismiss(); back() }
        .setNegativeButton("Huỷ") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

private fun secDelete(c: Context, id: String, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return back()
    GlassBuilder(c).setTitle("Xoá mục ${s.name}?")
        .setMessage("Chỉ xoá mục này khỏi màn hình chính. Các ứng dụng trong mục vẫn còn trên TV.")
        .setPositiveButton("Xoá mục") { _, _ -> Secs.delete(c, id); back() }
        .setNegativeButton("Huỷ") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

/** Menu của một mục: thêm/xoá/sắp xếp ứng dụng, đổi tên, đổi cỡ, ẩn, chuyển lên xuống, xoá mục.
 *  back chạy sau mỗi thao tác (Cài đặt: mở lại danh sách mục; màn hình chính: dựng lại giao diện) */
fun secMenu(c: Context, id: String, back: () -> Unit) {
    val l = Secs.load(c)
    val idx = l.indexOfFirst { it.id == id }
    if (idx < 0) { back(); return }
    val s = l[idx]
    val hid = Apps.isHidden(c, Secs.key(id))
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    fun row(t: String, f: () -> Unit) { labels.add(t); acts.add(f) }
    row("Thêm ứng dụng vào mục") { secAddApps(c, id, back) }
    if (s.pkgs.isNotEmpty()) row("Bỏ ứng dụng khỏi mục") { secRemoveApps(c, id, back) }
    if (s.pkgs.size > 1) row("Sắp xếp thứ tự ứng dụng") { secOrderDialog(c, id, back) }
    row("Đổi tên mục") { secNameDialog(c, "Đổi tên mục", s.name, { n -> Secs.update(c, id) { Sec(it.id, n, it.pkgs) }; back() }, back) }
    row("Kích thước biểu tượng: " + Secs.SIZE_LABELS[Secs.size(c, id)]) { secSizeDialog(c, id, back) }
    row(if (hid) "Hiện mục trên màn hình chính" else "Ẩn mục khỏi màn hình chính") { Apps.setHidden(c, Secs.key(id), !hid); back() }
    if (idx > 0) row("Chuyển mục lên trên") { Secs.move(c, id, -1); back() }
    if (idx < l.size - 1) row("Chuyển mục xuống dưới") { Secs.move(c, id, 1); back() }
    row("Xoá mục") { secDelete(c, id, back) }
    GlassBuilder(c).setTitle(s.name).setItems(labels.toTypedArray()) { _, i -> acts[i]() }
        .setNegativeButton("Quay lại") { _, _ -> back() }.setOnCancelListener { back() }.show()
}

/** Menu khi giữ OK trên một ứng dụng nằm trong mục ở màn hình chính */
fun secAppMenu(c: Context, id: String, a: AppItem, back: () -> Unit) {
    val s = Secs.get(c, id) ?: return
    val i = s.pkgs.indexOf(a.pkg)
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    fun row(t: String, f: () -> Unit) { labels.add(t); acts.add(f) }
    row("Mở ứng dụng") { openApp(c, a) }
    if (i > 0) row("Chuyển sang trái") { secMoveApp(c, id, i, i - 1); back() }
    if (i in 0 until s.pkgs.size - 1) row("Chuyển sang phải") { secMoveApp(c, id, i, i + 1); back() }
    row("Bỏ khỏi mục này") { Secs.update(c, id) { Sec(it.id, it.name, it.pkgs.filter { p -> p != a.pkg }) }; back() }
    row("Thêm ứng dụng vào mục") { secAddApps(c, id, back) }
    row("Tuỳ chỉnh mục: ${s.name}") { secMenu(c, id, back) }
    row("Ẩn, khoá, gỡ cài đặt ứng dụng…") { showMenu(c, a, back) }
    GlassBuilder(c).setTitle(a.label).setItems(labels.toTypedArray()) { _, k -> acts[k]() }.show()
}

/** Từ menu của một ứng dụng (màn hình chính / Tất cả ứng dụng): chọn mục để thêm ứng dụng này vào, hoặc tạo mục mới */
fun secPickAdd(c: Context, a: AppItem, back: () -> Unit) {
    val l = Secs.load(c)
    val labels = ArrayList<String>(); val acts = ArrayList<() -> Unit>()
    l.forEach { s ->
        val has = a.pkg in s.pkgs
        labels.add(s.name + (if (has) " (đã có)" else ""))
        acts.add {
            if (has) secToast(c, "Ứng dụng đã có trong mục này")
            else { Secs.update(c, s.id) { Sec(it.id, it.name, it.pkgs + a.pkg) }; secToast(c, "Đã thêm vào ${s.name}") }
            back()
        }
    }
    labels.add("+ Tạo mục mới")
    acts.add {
        secNameDialog(c, "Tạo mục mới", "", { n ->
            val s = Secs.create(c, n)
            Secs.update(c, s.id) { Sec(it.id, it.name, listOf(a.pkg)) }
            secToast(c, "Đã tạo mục $n và thêm ${a.label}"); back()
        }, back)
    }
    GlassBuilder(c).setTitle("Thêm ${a.label} vào mục").setItems(labels.toTypedArray()) { _, i -> acts[i]() }
        .setNegativeButton("Huỷ", null).show()
}

/** Ô dấu + cuối hàng của mục: bấm để thêm ứng dụng vào mục. Cùng kích thước và kiểu viền với ô ứng dụng */
fun plusTile(c: Context, mult: Float, onClick: () -> Unit): View = LinearLayout(c).apply {
    val sc = Apps.scale(c, "+") * mult
    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
    setPadding(c.dp(12), c.dp(12), c.dp(12), c.dp(12))
    isFocusable = true; isClickable = true
    val sz = c.dp((72 * sc).toInt())
    addView(TextView(c).apply {
        text = "+"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); textSize = 30f * sc
        background = GradientDrawable().apply { cornerRadius = c.dp(Apps.radius(c)).toFloat(); setColor(0x22FFFFFF); setStroke(c.dp(2), 0x88FFFFFF.toInt()) }
    }, LinearLayout.LayoutParams(sz, sz))
    addView(TextView(c).apply { text = "Thêm"; setTextColor(Color.WHITE); textSize = 13f * sc * Apps.fs(c); maxLines = 1; gravity = Gravity.CENTER })
    background = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { cornerRadius = c.dp(Apps.radius(c) + 4).toFloat(); setColor(0x33FFFFFF) })
        addState(intArrayOf(), GradientDrawable().apply { setColor(0) })
    }
    noSysHighlight()
    setOnFocusChangeListener { v, f ->
        v.scaleX = if (f && !Apps.rm(c)) 1.12f else 1f; v.scaleY = v.scaleX
    }
    setOnClickListener { onClick() }
}

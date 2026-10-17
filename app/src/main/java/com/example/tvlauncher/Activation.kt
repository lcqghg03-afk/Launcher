package com.example.tvlauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.widget.*
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Dán địa chỉ Web App của Google Apps Script (file ActivationScript.gs) vào đây */
const val ACT_URL = ""

object Act {
    /** Mã máy: cố định cho mỗi TV, KHÔNG mất khi xoá dữ liệu ứng dụng (chỉ đổi khi khôi phục cài đặt gốc) */
    fun deviceId(c: Context): String {
        val a = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "0"
        val h = MessageDigest.getInstance("SHA-256").digest(("tvl:$a").toByteArray()).joinToString("") { "%02X".format(it) }
        return h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12)
    }

    /** Đã kích hoạt cho đúng TV này chưa. Xoá dữ liệu ứng dụng sẽ làm mất dấu này => bị khoá lại */
    // Nếu ACT_URL để trống thì tính năng khoá TẮT (app dùng bình thường)
    fun ok(c: Context) = ACT_URL.isEmpty() || Apps.str(c, "act_dev") == deviceId(c)
}

/** Màn hình khoá: hiện mã máy, nhập key do chủ ứng dụng cấp. Key chỉ dùng được 1 lần cho mỗi TV */
class ActivationActivity : VActivity() {
    private lateinit var status: TextView
    private lateinit var keyBox: EditText
    private lateinit var btn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = Act.deviceId(this)
        fun t(s: String, sz: Float, col: Int = Color.WHITE) = TextView(this).apply { text = s; textSize = sz; setTextColor(col); gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) }
        fun button(s: String, click: () -> Unit) = Button(this).apply { text = s; isFocusable = true; setOnClickListener { click() } }
        status = t("", 15f, 0xFFFFB74D.toInt())
        keyBox = EditText(this).apply {
            hint = "Nhập key kích hoạt"; setHintTextColor(0x88FFFFFF.toInt()); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; gravity = Gravity.CENTER; textSize = 20f
            setSingleLine(true)
        }
        btn = button("Kích hoạt") { activate(id) }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(24), dp(40), dp(24))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(0xFF1C1C24.toInt()) }
            addView(t("TV Launcher chưa được kích hoạt", 24f, 0xFF4D6BFF.toInt()))
            addView(t("Hãy gửi MÃ MÁY bên dưới cho chủ ứng dụng để nhận key kích hoạt.", 16f))
            addView(t(id, 32f))
            addView(keyBox, LinearLayout.LayoutParams(dp(420), -2).apply { topMargin = dp(12) })
            addView(btn, LinearLayout.LayoutParams(dp(420), -2))
            addView(status)
            addView(button("Chuyển đầu vào (HDMI/AV)") { pickInput(this@ActivationActivity) }, LinearLayout.LayoutParams(dp(420), -2).apply { topMargin = dp(12) })
            addView(button("Cài đặt hệ thống") { openSystemSettings(this@ActivationActivity) }, LinearLayout.LayoutParams(dp(420), -2))
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(0xFF101018.toInt())
            addView(ScrollView(context).apply { isFillViewport = true; addView(FrameLayout(context).apply { addView(box, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)) }) }, -1, -1)
        })
        btn.requestFocus()
    }

    private fun activate(id: String) {
        val key = keyBox.text.toString().trim().uppercase()
        if (key.isEmpty()) { status.text = "Hãy nhập key"; return }
        btn.isEnabled = false; status.text = "Đang kiểm tra key…"
        thread {
            val r = try { httpGet("$ACT_URL?act=1&id=${URLEncoder.encode(id, "UTF-8")}&key=${URLEncoder.encode(key, "UTF-8")}").trim() } catch (e: Exception) { "net" }
            runOnUiThread {
                btn.isEnabled = true
                when {
                    r.startsWith("ok") -> { Apps.putStr(this, "act_dev", id); Toast.makeText(this, "Kích hoạt thành công", Toast.LENGTH_LONG).show(); finish() }
                    r == "used" -> status.text = "Key đã được sử dụng. Hãy liên hệ chủ ứng dụng để mở khoá lại"
                    r == "bad" -> status.text = "Key không đúng hoặc chưa được cấp cho TV này"
                    else -> status.text = "Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại"
                }
            }
        }
    }

    // Không cho thoát khỏi màn hình khoá bằng phím Back
    override fun onKeyDown(keyCode: Int, e: KeyEvent?): Boolean = if (keyCode == KeyEvent.KEYCODE_BACK) true else super.onKeyDown(keyCode, e)
}

package com.example.tvlauncher

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Ô giờ + thời tiết độc lập.
 * - Kích thước đổi bằng scaleX/scaleY quanh góc phải-trên => KHÔNG đẩy/ảnh hưởng Wifi, Cài đặt hay view khác.
 * - Bấm vào ô (hoặc nhấn OK trên remote) để chọn tỉnh/thành và kéo thanh trượt kích thước.
 */
class ClockWeatherView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : FrameLayout(ctx, attrs) {

    private val prefs = ctx.getSharedPreferences("clock_weather", Context.MODE_PRIVATE)
    private val tvWeather: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() { loadWeather(); handler.postDelayed(this, 15 * 60 * 1000L) }
    }

    init {
        LayoutInflater.from(ctx).inflate(R.layout.view_clock_weather, this, true)
        tvWeather = findViewById(R.id.cwWeather)
        findViewById<TextClock>(R.id.cwDate).apply {
            val f = "EEEE, d 'tháng' M, yyyy"
            format24Hour = f; format12Hour = f
            textLocale = Locale("vi", "VN")
        }
        // Gốc scale: góc phải-trên, ô lớn ra phía trái/dưới, không đụng icon wifi/cài đặt.
        post { pivotX = width.toFloat(); pivotY = 0f; applyScale(prefs.getFloat("scale", 1f)) }
        findViewById<View>(R.id.cwRoot).setOnClickListener { showSettings() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(refresh) }
    override fun onDetachedFromWindow() { handler.removeCallbacks(refresh); super.onDetachedFromWindow() }

    private fun applyScale(s: Float) { pivotX = width.toFloat(); pivotY = 0f; scaleX = s; scaleY = s }

    private fun currentPlace(): Place {
        val name = prefs.getString("place", "Hà Nội")
        return PROVINCES.firstOrNull { it.name == name } ?: PROVINCES[0]
    }

    private fun showSettings() {
        val c = context
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 0) }

        box.addView(TextView(c).apply { text = "Tỉnh / thành phố" })
        val spinner = Spinner(c)
        spinner.adapter = ArrayAdapter(c, android.R.layout.simple_spinner_dropdown_item, PROVINCES.map { it.name })
        spinner.setSelection(PROVINCES.indexOf(currentPlace()))
        box.addView(spinner)

        box.addView(TextView(c).apply { text = "Kích thước (gạt trái = nhỏ, phải = lớn)"; setPadding(0, 32, 0, 0) })
        val bar = SeekBar(c).apply {
            max = 100 // 0..100 => scale 0.6..1.8
            progress = (((prefs.getFloat("scale", 1f) - 0.6f) / 1.2f) * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    val s = 0.6f + 1.2f * p / 100f
                    applyScale(s); prefs.edit().putFloat("scale", s).apply() // xem trước trực tiếp
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        box.addView(bar)

        AlertDialog.Builder(c).setTitle("Giờ & thời tiết").setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                prefs.edit().putString("place", PROVINCES[spinner.selectedItemPosition].name).apply()
                loadWeather()
            }.setNegativeButton("Đóng", null).show()
    }

    private fun loadWeather() {
        val p = currentPlace()
        thread {
            val text = try {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                        "&current=temperature_2m,weather_code&timezone=auto")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000; conn.readTimeout = 8000
                val cur = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("current")
                "${p.name} · ${Math.round(cur.getDouble("temperature_2m"))}°C · ${describe(cur.getInt("weather_code"))}"
            } catch (e: Exception) { "${p.name} · không có mạng" }
            post { tvWeather.text = text }
        }
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Trời quang"; 1, 2 -> "Ít mây"; 3 -> "Nhiều mây"; 45, 48 -> "Sương mù"
        in 51..57 -> "Mưa phùn"; in 61..67 -> "Mưa"; in 71..77 -> "Tuyết"
        in 80..82 -> "Mưa rào"; in 95..99 -> "Dông"; else -> "—"
    }
}

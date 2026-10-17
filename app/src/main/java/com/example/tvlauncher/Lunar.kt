package com.example.tvlauncher

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Âm lịch Việt Nam (múi giờ GMT+7), thuật toán của Hồ Ngọc Đức */
object Lunar {
    private const val TZ = 7.0
    private val CAN = arrayOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = arrayOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    private fun jd(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var j = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (j < 2299161) j = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return j
    }

    private fun newMoon(k: Int): Int {
        val t = k / 1236.85; val t2 = t * t; val t3 = t2 * t; val dr = PI / 180
        var j1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        j1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * dr)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mp = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c = (0.1734 - 0.000393 * t) * sin(m * dr) + 0.0021 * sin(2 * dr * m)
        c = c - 0.4068 * sin(mp * dr) + 0.0161 * sin(dr * 2 * mp)
        c -= 0.0004 * sin(dr * 3 * mp)
        c = c + 0.0104 * sin(dr * 2 * f) - 0.0051 * sin(dr * (m + mp))
        c = c - 0.0074 * sin(dr * (m - mp)) + 0.0004 * sin(dr * (2 * f + m))
        c = c - 0.0004 * sin(dr * (2 * f - m)) - 0.0006 * sin(dr * (2 * f + mp))
        c = c + 0.0010 * sin(dr * (2 * f - mp)) + 0.0005 * sin(dr * (2 * mp + m))
        val dt = if (t < -11) 0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        else -0.000278 + 0.000265 * t + 0.000262 * t2
        return floor(j1 + c - dt + 0.5 + TZ / 24).toInt()
    }

    private fun sunLongitude(jdn: Int): Int {
        val t = (jdn - 2451545.5 - TZ / 24) / 36525; val t2 = t * t; val dr = PI / 180
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(dr * m)
        dl += (0.019993 - 0.000101 * t) * sin(dr * 2 * m) + 0.000290 * sin(dr * 3 * m)
        var l = (l0 + dl) * dr
        l -= PI * 2 * floor(l / (PI * 2))
        return floor(l / PI * 6).toInt()
    }

    private fun lunarMonth11(yy: Int): Int {
        val off = jd(31, 12, yy) - 2415021
        val k = (off / 29.530588853).toInt()
        var nm = newMoon(k)
        if (sunLongitude(nm) >= 9) nm = newMoon(k - 1)
        return nm
    }

    private fun leapMonthOffset(a11: Int): Int {
        val k = ((a11 - 2415021.076998695) / 29.530588853 + 0.5).toInt()
        var i = 1
        var arc = sunLongitude(newMoon(k + i))
        var last: Int
        do { last = arc; i++; arc = sunLongitude(newMoon(k + i)) } while (arc != last && i < 14)
        return i - 1
    }

    /** Trả về [ngày, tháng, năm âm, 1 nếu tháng nhuận] */
    fun convert(dd: Int, mm: Int, yy: Int): IntArray {
        val dn = jd(dd, mm, yy)
        val k = ((dn - 2415021.076998695) / 29.530588853).toInt()
        var ms = newMoon(k + 1)
        if (ms > dn) ms = newMoon(k)
        var a11 = lunarMonth11(yy)
        var b11 = a11
        var ly: Int
        if (a11 >= ms) { ly = yy; a11 = lunarMonth11(yy - 1) } else { ly = yy + 1; b11 = lunarMonth11(yy + 1) }
        val ld = dn - ms + 1
        val diff = (ms - a11) / 29
        var leap = 0
        var lm = diff + 11
        if (b11 - a11 > 365) {
            val lo = leapMonthOffset(a11)
            if (diff >= lo) { lm = diff + 10; if (diff == lo) leap = 1 }
        }
        if (lm > 12) lm -= 12
        if (lm >= 11 && diff < 4) ly -= 1
        return intArrayOf(ld, lm, ly, leap)
    }

    fun label(dd: Int, mm: Int, yy: Int): String {
        val r = convert(dd, mm, yy)
        val y = r[2]
        return "Âm lịch: ${r[0]}/${r[1]}${if (r[3] == 1) " nhuận" else ""} · ${CAN[(y + 6) % 10]} ${CHI[(y + 8) % 12]}"
    }
}

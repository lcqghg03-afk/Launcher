package com.example.tvlauncher

data class Place(val name: String, val lat: Double, val lon: Double)

// 34 tỉnh/thành (sau sáp nhập 2025), toạ độ trung tâm hành chính (xấp xỉ).
// Muốn thêm tên cũ (vd: Bình Dương, Vũng Tàu...) chỉ cần thêm một dòng Place(...).
val PROVINCES = listOf(
    Place("Hà Nội", 21.0285, 105.8542), Place("TP. Hồ Chí Minh", 10.7769, 106.7009),
    Place("Hải Phòng", 20.8449, 106.6881), Place("Đà Nẵng", 16.0544, 108.2022),
    Place("Huế", 16.4637, 107.5909), Place("Cần Thơ", 10.0452, 105.7469),
    Place("Tuyên Quang", 21.8233, 105.2141), Place("Lào Cai", 21.7229, 104.9113),
    Place("Thái Nguyên", 21.5928, 105.8442), Place("Phú Thọ", 21.3227, 105.4019),
    Place("Bắc Ninh", 21.1861, 106.0763), Place("Hưng Yên", 20.6464, 106.0511),
    Place("Ninh Bình", 20.2506, 105.9745), Place("Thanh Hóa", 19.8067, 105.7852),
    Place("Nghệ An", 18.6796, 105.6813), Place("Hà Tĩnh", 18.3428, 105.9057),
    Place("Quảng Trị", 17.4690, 106.6222), Place("Quảng Ngãi", 15.1205, 108.7923),
    Place("Gia Lai", 13.7829, 109.2196), Place("Khánh Hòa", 12.2388, 109.1967),
    Place("Lâm Đồng", 11.9404, 108.4583), Place("Đắk Lắk", 12.6667, 108.0378),
    Place("Đồng Nai", 10.9574, 106.8427), Place("Tây Ninh", 11.3100, 106.0983),
    Place("Vĩnh Long", 10.2537, 105.9722), Place("Đồng Tháp", 10.4938, 105.6882),
    Place("An Giang", 10.3759, 105.4185), Place("Cà Mau", 9.1769, 105.1524),
    Place("Điện Biên", 21.3860, 103.0230), Place("Lai Châu", 22.3964, 103.4703),
    Place("Sơn La", 21.3256, 103.9188), Place("Lạng Sơn", 21.8537, 106.7615),
    Place("Quảng Ninh", 20.9517, 107.0807), Place("Cao Bằng", 22.6657, 106.2570)
)

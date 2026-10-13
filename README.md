# 🔋 BatteryGuard

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android_15+-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin_2.1-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack_Compose_M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Engine-Shizuku_API-FF6F00?style=for-the-badge" alt="Shizuku" />
  <img src="https://img.shields.io/badge/Package_Size-~4.8_MB-success?style=for-the-badge" alt="Package Size" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue?style=for-the-badge" alt="License" />
</p>

<p align="center">
  <strong>Vệ sĩ pin & Tối ưu hóa hệ thống Android thông minh không cần Root</strong><br>
  <em>Giám sát mức tiêu thụ điện năng chuyên sâu, tự động dừng app ngầm hao pin, đóng băng ứng dụng 1 chạm và tối ưu hóa đặc quyền cho Xiaomi HyperOS / MIUI.</em>
</p>

---

## 🌟 Giới thiệu tổng quan (Overview)

**BatteryGuard** là giải pháp quản lý điện năng và tối ưu hóa hệ thống thế hệ mới dành cho Android. Được xây dựng hoàn toàn bằng **Jetpack Compose (Material 3)** hiện đại và tận dụng sức mạnh đặc quyền của **[Shizuku](https://shizuku.rikka.app/)**, BatteryGuard cho phép bạn can thiệp sâu vào các tiến trình hệ thống, kiểm soát mức tiêu hao pin chi tiết đến từng UID mà **hoàn toàn không yêu cầu quyền Root**.

Đặc biệt, BatteryGuard được tối ưu hóa chuyên sâu để khắc phục triệt để các vấn đề phổ biến trên **Xiaomi HyperOS & MIUI**, chẳng hạn như trễ thông báo ngân hàng, hao pin khi tắt màn hình, xung đột trợ lý ảo XiaoAI và các hạn chế ngặt nghèo về quyền chạy nền.

---

## ✨ Tính năng nổi bật (Key Features)

### 1. 🛡️ Giám sát & Phân tích pin thời gian thực (Deep Drain Analytics)
- **Đo lường chính xác:** Theo dõi tốc độ xả pin (%/giờ và mAh) chi tiết cho từng ứng dụng qua hệ thống BatteryStats và Dumpsys.
- **Phân tách thông minh:** Tách bạch rõ ràng mức tiêu thụ khi **tắt màn hình (Screen-off)** và khi **sử dụng tích cực (Active Use)** để chỉ điểm chính xác các ứng dụng chạy ngầm lén lút.
- **Hỗ trợ đa người dùng:** Định danh phân biệt chính xác theo UID và User ID (hỗ trợ Dual Apps, Không gian thứ hai, Work Profile).

### 2. ⚡ Tự động hóa dọn dẹp chạy ngầm (Smart Automation Engine)
- **Chu kỳ quét linh hoạt:** Tùy chọn chu kỳ quét thông minh 1 chạm (1m, 3m, 5m, 10m, 15m, 30m).
- **Cơ chế an toàn tuyệt đối:** Tự động bỏ qua các ứng dụng đang phát nhạc, podcast, dẫn đường GPS hoặc có Foreground Service tích cực để không làm gián đoạn trải nghiệm của bạn.
- **Tự động buộc dừng (Auto Force-Stop):** Can thiệp dừng an toàn các ứng dụng nền tiêu tốn pin vượt ngưỡng thiết lập.

### 3. ❄️ Đóng băng & Rã đông ứng dụng 1 chạm (App Freeze & Unfreeze)
- **Vô hiệu hóa triệt để:** Đóng băng các ứng dụng ít sử dụng thông qua Shizuku (`pm disable / hide`), triệt tiêu 100% tài nguyên CPU, RAM và mạng khi không dùng.
- **Rã đông tức thì:** Kích hoạt lại app ngay lập tức chỉ với một lần nhấn.
- **Menu tác vụ ngữ cảnh:** Nhấn giữ vào bất kỳ app nào trong danh sách để mở menu thao tác nhanh: Buộc dừng, Đóng băng/Rã đông, Thêm/Bỏ ngoại lệ với logic hiển thị 1 chiều thông minh.

### 4. 🔑 Cấp quyền hệ thống nâng cao 1 chạm (Permission Granter)
- Cấp tức thì các quyền đặc quyền hệ thống Android mà không cần kết nối máy tính gõ lệnh ADB phức tạp:
  - `WRITE_SECURE_SETTINGS` (Thay đổi cài đặt bảo mật hệ thống)
  - `PACKAGE_USAGE_STATS` (Đọc dữ liệu sử dụng ứng dụng)
  - `BATTERY_STATS` (Phân tích chỉ số tiêu thụ điện năng)
  - `SYSTEM_ALERT_WINDOW` (Hiển thị cửa sổ nổi)
  - `DUMP` (Trích xuất log chẩn đoán hệ thống)

### 5. 🚀 Bộ công cụ tối ưu hóa chuyên sâu Xiaomi (HyperOS / MIUI Suite)
- **Chặn xung đột XiaoAI:** Khóa hoàn toàn trợ lý ảo XiaoAI tự động kích hoạt đè khi sử dụng phím tắt hoặc cử chỉ dịch màn hình.
- **Tối ưu hóa thông báo Ngân hàng & Tài chính:** Đồng bộ cấu hình ưu tiên cao, ngăn HyperOS kill các dịch vụ push thông báo, đảm bảo nhận biến động số dư tức thì 100%.
- **Bỏ qua tối ưu pin & Tự khởi chạy:** Quản lý Autostart và Whitelist pin hệ thống chỉ với một nút bấm.

### 6. 🪓 Siêu nhẹ & Tối ưu hiệu năng cao (Ultra-compact & Efficient)
- Đóng gói với cấu hình **R8 Full Mode & Resource Shrinking**, thu nhỏ kích thước file APK xuống chỉ còn **~4.8 MB** (giảm gần 14 lần so với bản chưa nén).
- Tải ứng dụng nhanh, tiêu thụ tài nguyên máy cực thấp, không chứa quảng cáo hay mã theo dõi.

---

## 🛠️ Kiến trúc kỹ thuật (Tech Stack & Architecture)

BatteryGuard được xây dựng tuân thủ nghiêm ngặt mô hình **Clean Architecture** và các tiêu chuẩn phát triển Android hiện đại nhất của Google:

```
app/
├── data/
│   ├── db/                 # Room Database (Usage Logs, Frozen Apps, Exceptions)
│   ├── preferences/        # Jetpack DataStore (Cấu hình tự động hóa, chu kỳ)
│   └── repository/         # Triển khai Repository trung gian
├── domain/
│   ├── battery/            # Thuật toán tính toán mức xả pin, UID resolution
│   └── model/              # Domain Models (AppBatteryInfo, BatteryStatus)
├── ui/
│   ├── components/         # Material 3 UI Components (Card, Pill Bar, BottomSheet)
│   ├── navigation/         # Jetpack Compose Navigation
│   ├── screen/             # Màn hình chính (Home, Apps, Automation, Settings, Granter)
│   └── theme/              # Material 3 Theme & Color Schemes
└── util/                   # XiaomiHelper, PackageHelper, ShizukuBridge, ShellExecutor
```

- **Ngôn ngữ:** Kotlin 2.1 (Kotlin Coroutines & StateFlow)
- **Giao diện:** Jetpack Compose (BOM 2024.12), Material 3, Material Icons Extended
- **Dependency Injection:** Dagger Hilt 2.53
- **Cơ sở dữ liệu:** Room DB 2.6
- **Lưu trữ cấu hình:** Jetpack DataStore Preferences
- **Đặc quyền Shizuku:** `dev.rikka.shizuku:api:13.1.5`
- **Hidden API Bypass:** `org.lsposed.hiddenapibypass:4.3`
- **Biểu đồ hiển thị:** Vico Charts M3 2.0
- **Tối ưu hóa mã nguồn:** ProGuard / R8 Minification + Resource Shrinking

---

## 📋 Yêu cầu hệ thống (Prerequisites)

- Thiết bị chạy hệ điều hành **Android 15+** (API Level 35 trở lên).
- Ứng dụng **[Shizuku](https://shizuku.rikka.app/)** đã được cài đặt và đang chạy (hỗ trợ cả chế độ không Root thông qua Wireless Debugging / Ghép nối Wi-Fi, hoặc qua Root / KernelSU / APatch).

---

## 🚀 Hướng dẫn cài đặt & Bắt đầu nhanh (Getting Started)

### Cách 1: Cài đặt từ bản phát hành (Khuyên dùng)
1. Tải file APK mới nhất (`BatteryGuard-release.apk`) từ trang [Releases](../../releases).
2. Cài đặt file APK lên thiết bị của bạn.
3. Mở ứng dụng **Shizuku**, đảm bảo dịch vụ Shizuku đang chạy (`Shizuku is running`).
4. Khởi chạy **BatteryGuard** và bấm **Cho phép** khi hộp thoại yêu cầu quyền Shizuku xuất hiện.

### Cách 2: Tự biên dịch từ mã nguồn (Build from source)
Yêu cầu: JDK 17+ và Android SDK 36.

```bash
# Clone kho lưu trữ
git clone https://github.com/vanlinh0392-art/BatteryGuard.git
cd BatteryGuard

# Cấp quyền thực thi cho Gradle wrapper
chmod +x ./gradlew

# Biên dịch bản Release nén R8 siêu nhẹ
./gradlew assembleRelease

# File APK sau khi build sẽ nằm tại:
# app/build/outputs/apk/release/app-release.apk
```

---

## 💡 Hướng dẫn sử dụng (Usage Guide)

| Tab | Mô tả & Cách thao tác |
| :--- | :--- |
| **🏠 Tổng quan (Home)** | Theo dõi mức pin hiện tại, nhiệt độ, tốc độ xả pin thời gian thực và biểu đồ lịch sử xả pin. |
| **📱 Ứng dụng (Apps)** | Xem chi tiết mức pin tiêu thụ của từng app. <br>• **Chạm nhanh (Click):** Vào menu cấp quyền nâng cao cho app.<br>• **Nhấn giữ (Long press):** Mở menu tác vụ nhanh (Buộc dừng, Đóng băng/Rã đông, Bật/Tắt ngoại lệ). |
| **⚡ Tự động (Automation)** | Cấu hình tính năng tự động dừng ứng dụng hao pin. Chọn nhanh chu kỳ quét với thanh Pill Bar 1 chạm và quản lý các điều kiện bảo vệ an toàn. |
| **⚙️ Cài đặt (Settings)** | Bộ tinh chỉnh chuyên sâu dành riêng cho thiết bị Xiaomi/HyperOS, sao lưu dữ liệu và kiểm tra cập nhật ứng dụng. |

---

## 🔒 Cam kết bảo mật & An toàn (Privacy & Security)

- **100% Offline & Mã nguồn mở:** Ứng dụng không yêu cầu quyền INTERNET để thu thập dữ liệu cá nhân hay gửi telemetry ra máy chủ bên ngoài.
- **Không can thiệp sâu nguy hiểm:** Sử dụng các API chuẩn của hệ điều hành thông qua Shizuku, không can thiệp sửa đổi phân vùng hệ thống (`/system`), không làm mất chứng nhận SafetyNet / Play Integrity.
- **Bảo vệ ứng dụng trọng yếu:** Tự động loại trừ các dịch vụ hệ thống cốt lõi và cung cấp cơ chế Whitelist để người dùng bảo vệ ứng dụng cần thiết.

---

## 🤝 Đóng góp (Contributing)

Mọi đóng góp nhằm cải thiện ứng dụng đều được hoan nghênh nồng nhiệt!
1. Fork dự án.
2. Tạo nhánh tính năng mới (`git checkout -b feature/AmazingFeature`).
3. Commit các thay đổi (`git commit -m 'feat: Add some AmazingFeature'`).
4. Đẩy lên nhánh của bạn (`git push origin feature/AmazingFeature`).
5. Mở một **Pull Request** trên GitHub.

---

## 📄 Giấy phép (License)

Dự án được phân phối dưới giấy phép **Apache License 2.0**. Xem chi tiết tại tệp [LICENSE](LICENSE).

<p align="center">
  Được phát triển với tình yêu dành cho hiệu năng và tối ưu hóa Android. ❤️
</p>

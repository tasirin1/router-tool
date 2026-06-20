# Router Tool - Android

Aplikasi Android untuk mengelola router Tenda (192.168.0.1) dengan 6 fitur utama.

## Fitur

1. 🔄 **Restart Router** — Restart router dari HP
2. 🌐 **Cek Koneksi** — Cek apakah router merespon
3. 🔑 **Ganti Password Admin** — Ganti password login router
4. 📶 **Atur WiFi** — Buka halaman pengaturan WiFi
5. ℹ️ **Info Router** — Lihat info sistem router
6. 🚦 **Speed Control** — Atur kecepatan internet

## Cara Build

### Via GitHub Actions (recommended)
1. Fork / push repo ke GitHub
2. GitHub Actions akan build otomatis
3. Download APK dari Actions → Artifacts

### Via Android Studio
```bash
git clone https://github.com/USERNAME/router-tool.git
cd router-tool
./gradlew assembleRelease
```

APK ada di `app/build/outputs/apk/release/`

## Catatan
- Router harus terhubung ke jaringan yang sama
- Default: 192.168.0.1 (Tenda)
- Aplikasi menggunakan HTTP (tidak perlu root)

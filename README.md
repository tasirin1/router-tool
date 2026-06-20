# Router Tool - Android

Aplikasi Android untuk mengelola router Tenda (192.168.0.1) dengan 6 fitur utama.

**Support:** HP 📱 & Android TV 📺 (Android 5.0+)

## Fitur

1. 🔄 **Restart Router** — Restart router dari HP/TV
2. 🌐 **Cek Koneksi** — Cek apakah router merespon
3. 🔑 **Ganti Password Admin** — Ganti password login router
4. 📶 **Atur WiFi** — Buka halaman pengaturan WiFi
5. ℹ️ **Info Router** — Lihat info sistem router
6. 🚦 **Speed Control** — Atur kecepatan internet

## Cara Build

### Via GitHub Actions (recommended)
Push ke GitHub → Actions build otomatis → Download APK dari Artifacts

### Via Android Studio
```bash
git clone https://github.com/tasirin1/router-tool.git
cd router-tool
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/`

## Catatan
- Router harus terhubung ke jaringan yang sama
- Default IP: 192.168.0.1 (Tenda)
- Aplikasi menggunakan HTTP (tidak perlu root)
- Navigasi remote di Android TV: D-pad + OK

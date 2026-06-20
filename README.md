# Router Tool - Android

![Build](https://github.com/tasirin1/router-tool/actions/workflows/build.yml/badge.svg)

Aplikasi Android untuk mengelola router Tenda (192.168.0.1) dengan 6 fitur utama.

**Support:** HP 📱 & Android TV 📺 (Android 5.0+ / API 21+)

## Fitur

1. 🔄 **Restart Router** — Restart router dari HP/TV dengan 1 tombol
2. 🌐 **Cek Koneksi** — Cek apakah router merespon
3. 🔑 **Ganti Password Admin** — Ganti password login router admin
4. 📶 **Atur WiFi** — Buka halaman pengaturan WiFi router
5. ℹ️ **Info Router** — Lihat info sistem & status router
6. 🚦 **Speed Control** — Atur kecepatan internet (bandwidth)

## Download APK

1. Buka [GitHub Actions](https://github.com/tasirin1/router-tool/actions)
2. Klik workflow terbaru yang hijau (✅)
3. Scroll ke bawah → **Artifacts** → Download **RouterTool-APK**
4. Install APK di HP/Android TV

## Cara Build Sendiri

### Via GitHub Actions (Mudah)
Push ke GitHub → Actions build otomatis → Download dari Artifacts

### Via Android Studio
```bash
git clone https://github.com/tasirin1/router-tool.git
cd router-tool
./gradlew assembleRelease
```
APK: `app/build/outputs/apk/release/app-release.apk`

## Catatan
- Router harus terhubung ke jaringan yang sama (wifi LAN)
- Default IP: `192.168.0.1` (Tenda)
- Aplikasi HTTP (tidak perlu root)
- Navigasi remote Android TV: D-pad + OK
- WebView built-in untuk akses halaman admin

# Kaca — Android App

Kirim layar Android ke Mac via WiFi. Capture screen via **MediaProjection API**, kompres ke **JPEG**, kirim frame-by-frame lewat **TCP** ke Mac receiver.

## Cara Kerja

```
┌────────────────────────┐         WiFi / LAN         ┌─────────────────────┐
│  Kaca Android App      │  ────── JPEG stream ─────> │  Mac (Kaca Receiver)│
│                        │                            │                     │
│  • MediaProjection     │   frame format:            │  • Render di window │
│  • ImageReader         │   [ZMIR][width][height]    │  • FPS ~30 (720p)   │
│  • JPEG compress       │   [jpeg_len][jpeg_data]    │                     │
└────────────────────────┘                            └─────────────────────┘
```

## Fitur

- **Scan QR** — scan QR dari Mac, host & port terisi otomatis, langsung connect
- **Dua mode koneksi** — QR (cepat) atau manual (isi sendiri)
- **Kualitas bisa diatur** — 10–100%, sesuaikan dengan bandwidth
- **Foreground service** — streaming tetap jalan meski app di-minimize
- **Auto-scale** — resolusi layar otomatis discale ke maksimal 1280px

## Requirements

- Android 8.0+ (API 26)
- Izin **Screen Capture** (muncul otomatis saat start)

## Build

```bash
git clone https://github.com/hasan197/kaca-android.git
cd kaca-android
./build-android.sh
```

Output: `dist/kaca-android.apk`.

### Cara build manual

Buka folder ini di **Android Studio**, tunggu Gradle sync, klik **Run**.

## Cara Pakai

### Mode QR (recommended)

```
1. Jalankan Kaca Mac receiver → QR code muncul di window
2. Buka app Kaca di HP
3. Tap "Scan QR Code"
4. Scan QR yang ada di window Mac
5. Konfirmasi izin Screen Capture → mirror langsung jalan
```

### Mode Manual

```
1. Jalankan Kaca Mac receiver — catat IP yang muncul di terminal
2. Tap "▼ Isi Manual" di app Kaca
3. Isi Host IP (dari Mac) dan Port (default 27183)
4. Atur kualitas JPEG (75 default, turunkan kalau lemot)
5. Tap "Mulai Mirror"
6. Konfirmasi izin Screen Capture
```

### Selama streaming

- **Status** — indikator hijau + info tujuan di layar
- **Stop** — tap "Berhenti" atau tutup app
- **Minimize** — streaming lanjut di background (notifikasi)

## Tips & Troubleshooting

| Masalah | Solusi |
|---------|--------|
| **Gagal connect** | Pastikan Mac & HP di WiFi yang sama. Cek IP Mac benar |
| **QR tidak terbaca** | Dekatkan HP ke layar Mac, pastikan QR di window tidak terlalu kecil |
| **Layar patah-patah** | Turunkan kualitas ke 50% atau 30% |
| **Streaming berhenti sendiri** | Nonaktifkan battery optimization untuk Kaca: Settings > Apps > Kaca > Battery > Unrestricted |
| **Error "pendingResultData is null"** | Restart app, pastikan izin Screen Capture di-allow |
| **Mirror lambat** | Gunakan WiFi 5 GHz, atau turunkan kualitas JPEG |

## Struktur Project

```
kaca-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/kaca/android/
│       │   ├── MainActivity.kt      # UI + QR scan + kontrol
│       │   └── MirrorService.kt     # Screen capture + streaming
│       └── res/
│           ├── layout/activity_main.xml
│           ├── drawable/            # Ikon, shapes
│           ├── values/              # Tema, warna, string
│           └── mipmap-anydpi-v26/   # Adaptive icon
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── build-android.sh
```

## Lisensi

MIT

# Ruang Lega v2.0 (No-Slop Edition)

Aplikasi pembersih berkas sistem dan sampah memori mendalam untuk Android tanpa root dan tanpa PC, memanfaatkan hak akses shell lewat [Shizuku](https://github.com/RikkaApps/Shizuku).

Dirancang dengan antarmuka **No-Slop** (bersih, fungsional, kontras tinggi, hemat daya OLED, dan tanpa dekorasi generik yang membebani memori).

---

## ⚡ Fitur Utama v2.0

* **Universal & Otomatis**: Berjalan di semua perangkat Android 8.0+ (Samsung, Xiaomi/POCO, OPPO, Vivo, Realme, Pixel, dll). Deteksi otomatis perangkat MIUI/HyperOS untuk log vendor.
* **Pembersihan Cache Ganda (Internal + Eksternal)**:
  * Memangkas cache sistem (`pm trim-caches 999G`).
  * Menyapu bersih subfolder cache eksternal (`/sdcard/Android/data/*/cache/`).
  * Mengosongkan cache internal aplikasi boros (`/data/data/*/cache/` & `code_cache`).
* **Pembersihan Thumbnail & Media Junk**:
  * Mengosongkan folder thumbnail yang sering membengkak puluhan GB (`/sdcard/DCIM/.thumbnails`, `Pictures/.thumbnails`, dll).
  * Membersihkan berkas sampah tersembunyi (`.trashed`).
* **Pembersihan Log Sistem**:
  * Mengosongkan buffer memori RAM logcat (`logcat -b all -c`).
  * Membersihkan folder debug log vendor (seperti `/sdcard/MIUI/debug_log`).
* **Reset Kompilasi Dexopt (ART)** *(Opsional)*:
  * Mengembalikan artefak kompilasi aplikasi ke bawaan (`cmd package compile --reset -a`).
* **Panel Kendali & Konsol Live**:
  * Storage Meter Bar real-time dengan persentase.
  * Pill status Shizuku interaktif (Hijau = Siap, Kuning = Perlu Izin, Merah = Nonaktif).
  * Checkbox selektif & tombol **Pindai Ukuran** sebelum eksekusi pembersihan.
  * Konsol log langsung dengan fitur Salin Log & otomatis simpan ke folder Download.

---

## 🚀 Cara Pemasangan & Persiapan

1. Pasang aplikasi **Shizuku** dari Play Store atau GitHub.
2. Aktifkan Shizuku melalui **Wireless Debugging** (Opsi Pengembang di HP Android).
3. Buka **Ruang Lega**, ketuk tombol status Shizuku untuk memberikan izin akses.
4. Tekan **Pindai Ukuran** untuk memeriksa estimasi kapasitas sampah.
5. Centang kategori yang ingin dibersihkan, lalu ketuk **Bersihkan Terpilih**.

---

## 🛠️ Untuk Pengembang

* **Bahasa**: Kotlin, Android SDK 34 (MinSdk 26).
* **UI**: Native Android View tanpa library bloatware, bobot APK ~800 KB.
* **Build**: Otomatis dikompilasi melalui GitHub Actions (`.github/workflows/build.yml`).

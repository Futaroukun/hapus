# Ruang Lega v2.2 (Universal Pro Edition)

Aplikasi pembersih berkas sistem dan pengoptimalan penyimpanan mendalam untuk **semua perangkat Android** tanpa root dan tanpa PC, memanfaatkan hak akses shell tingkat sistem melalui [Shizuku](https://github.com/RikkaApps/Shizuku).

Dibangun dengan filosofi **No-Slop** (bersih, fungsional, hemat daya layar OLED, kontras tinggi, dan tanpa animasi/dekorasi AI yang memperlambat sistem).

---

## ⚡ Fitur Lengkap v2.2 (Multi-Device)

### 1. Pembersihan Ruang (Storage Sweeper)
* **Cache Aplikasi Universal**:
  * Pangkas cache sistem (`pm trim-caches 999G`).
  * Sapu bersih cache eksternal (`/sdcard/Android/data/*/cache/`).
  * Mengosongkan cache internal aplikasi boros (`/data/data/*/cache/` & `code_cache`).
* **Sisa Uninstall (App Corpses)**:
  * Memindai dan menghapus folder terlantar di `Android/data` & `Android/obb` dari aplikasi yang sudah pernah dicopot.
* **Thumbnail & Cache Galeri**:
  * Mengosongkan folder thumbnail yang sering memakan puluhan GB (`/sdcard/DCIM/.thumbnails`, `Pictures/.thumbnails`, dll) serta sampah `.trashed`.
* **Media Chat Junk (WhatsApp & Telegram)**:
  * Membersihkan cache stiker WhatsApp, status lama, dan cache media perpesanan Telegram.
* **Berkas APK Installer**:
  * Menghapus file `.apk` mentah yang tertinggal di folder `Download`.
* **Log Sistem Multi-Vendor**:
  * Menghapus log debug bawaan vendor (Samsung dumpstate, Xiaomi MIUI log, Oppo ColorOS log, Vivo log, Transsion log, dan buffer logcat RAM).
* **Reset Kompilasi Dexopt (ART)** *(Opsional)*:
  * Mengembalikan artefak kompilasi aplikasi ke bawaan (`cmd package compile --reset -a`).

### 2. Alat Diagnostik & Performa Cepat
* **🔍 Cek Berkas >50MB**: Memindai dan meranking 25 berkas terbesar di penyimpanan internal.
* **⚡ Legakan RAM**: Menghentikan paksa (*force-stop*) aplikasi pihak ketiga yang aktif di background.
* **📂 Hapus Direktori Kosong**: Menyapu folder-folder kosong terlantar di `/sdcard`.
* **📊 Info Partisi Disk**: Membaca tabel alokasi partisi sistem dan emulated storage.

---

## 🎨 Tampilan No-Slop Pro
* **Palet Warna Slate OLED**: Basis `#090C10` dan `#121722` berkontras tinggi dengan aksen Emerald `#10B981` dan Cyan `#06B6D4`.
* **Hero Storage Meter**: Angka ruang bebas ukuran besar dengan progress bar dual-color dan kalkulator estimasi total sampah.
* **Interactive Tap-to-Toggle Cards**: Cukup ketuk di mana saja pada area kartu untuk mencentang/menonaktifkan target.
* **Terminal Konsol Pro**: Tampilan terminal developer dengan tombol Salin Log dan Bersihkan Tampilan.

---

## 🚀 Panduan Pakai
1. Aktifkan **Shizuku** via Wireless Debugging di HP Anda.
2. Buka **Ruang Lega**, ketuk pill status Shizuku untuk memberikan izin.
3. Tekan **🔍 Pindai Ukuran** untuk kalkulasi kapasitas sampah di seluruh kategori.
4. Pilih kategori yang ingin dibersihkan, lalu ketuk **🧹 Bersihkan Terpilih**.

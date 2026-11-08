package id.pribadi.berkassistem

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter

object CleanerEngine {

    data class DiskInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val pctUsed: Int
    )

    fun getDiskInfo(): DiskInfo {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)
        val pct = if (total > 0L) ((used * 100L) / total).toInt() else 0
        return DiskInfo(total, free, used, pct)
    }

    fun getDeviceInfo(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val release = Build.VERSION.RELEASE
        val sdk = Build.VERSION.SDK_INT
        val arch = if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown"
        return "$manufacturer $model • Android $release (API $sdk) • $arch"
    }

    fun ukuranFolder(path: String): Long {
        val out = Shell.jalankan("du -sk '$path' 2>/dev/null").out
        val kb = out.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L
        return kb * 1024L
    }

    fun daftarFolderBerisi(perintah: String): List<Pair<String, Long>> {
        val baris = Shell.jalankan(perintah).out.lines().filter { it.startsWith("/") }
        return baris.map { path ->
            Pair(path, ukuranFolder(path))
        }.filter { it.second > 0L }
    }

    // ================= SCAN ESTIMASI REAL-TIME =================

    fun hitungEstimasiCache(ctx: Context): Long {
        var total = 0L
        try {
            val paket = Stat.ambil(ctx)
            total += paket.sumOf { it.cache }
        } catch (_: Throwable) {}
        val extFolders = daftarFolderBerisi("ls -d /sdcard/Android/data/*/cache 2>/dev/null")
        total += extFolders.sumOf { it.second }
        return total
    }

    fun hitungEstimasiMediaJunk(): Long {
        var total = 0L
        val targets = listOf(
            "/sdcard/DCIM/.thumbnails",
            "/sdcard/Pictures/.thumbnails",
            "/sdcard/Movies/.thumbnails",
            "/sdcard/Android/data/com.miui.gallery/cache",
            "/sdcard/Android/data/com.sec.android.gallery3d/cache",
            "/sdcard/Android/data/com.coloros.gallery3d/cache",
            "/sdcard/Android/data/com.vivo.gallery/cache"
        )
        for (t in targets) {
            total += ukuranFolder(t)
        }
        return total
    }

    fun hitungEstimasiChatMedia(): Long {
        var total = 0L
        val targets = listOf(
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/WhatsApp/Media/.Statuses",
            "/sdcard/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/Android/data/org.telegram.messenger/cache"
        )
        for (t in targets) {
            total += ukuranFolder(t)
        }
        return total
    }

    fun hitungEstimasiCorpses(ctx: Context): Long {
        return scanAppCorpses(ctx).sumOf { it.second }
    }

    fun hitungEstimasiApks(): Long {
        return scanOrphanApks().sumOf { it.second }
    }

    fun hitungEstimasiLog(): Long {
        var total = 0L
        val logFolders = listOf(
            "/sdcard/MIUI/debug_log",
            "/sdcard/MIUI/.debug_log",
            "/sdcard/oppo_log",
            "/sdcard/vivo_log",
            "/sdcard/ColorOS"
        )
        for (f in logFolders) {
            total += ukuranFolder(f)
        }
        return total
    }

    // ================= 1. PEMBERSIH CACHE LENGKAP =================

    fun bersihkanCacheAplikasi(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Membersihkan cache seluruh aplikasi (Universal)...")

        val rTrim = Shell.jalankan("pm trim-caches 999G")
        log("  • pm trim-caches selesai (status: ${rTrim.kode})")

        val extFolders = daftarFolderBerisi("ls -d /sdcard/Android/data/*/cache 2>/dev/null")
        var countExt = 0
        for ((folder, _) in extFolders) {
            Shell.jalankan("rm -rf '$folder'/* '$folder'/.[!.]* 2>/dev/null")
            countExt++
        }
        log("  • Menyapu $countExt folder cache eksternal (/sdcard/Android/data)")

        val rInt = Shell.jalankan("rm -rf /data/data/*/cache/* /data/data/*/code_cache/* 2>/dev/null")
        log("  • Mengosongkan cache internal (/data/data/*/cache)")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Cache aplikasi bersih. Ruang bebas +${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= 2. PEMBERSIH APP CORPSES (SISA UNINSTALL) =================

    fun scanAppCorpses(ctx: Context): List<Pair<String, Long>> {
        val installed = Shell.jalankan("pm list packages").out.lines()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val dataDirs = Shell.jalankan("ls -d /sdcard/Android/data/* /sdcard/Android/obb/* 2>/dev/null").out.lines()
            .filter { it.startsWith("/") }

        val corpses = mutableListOf<Pair<String, Long>>()
        for (dir in dataDirs) {
            val pkg = dir.substringAfterLast("/")
            if (pkg.contains(".") && !installed.contains(pkg)) {
                val size = ukuranFolder(dir)
                if (size > 0L) {
                    corpses.add(Pair(dir, size))
                }
            }
        }
        return corpses
    }

    fun bersihkanAppCorpses(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Memeriksa folder sisa aplikasi yang sudah di-uninstall...")
        val corpses = scanAppCorpses(ctx)
        if (corpses.isEmpty()) {
            log("  • Berkas sisa aplikasi: Bersih (0 B)")
            return 0L
        }
        var count = 0
        for ((dir, size) in corpses) {
            val pkg = dir.substringAfterLast("/")
            Shell.jalankan("rm -rf '$dir' 2>/dev/null")
            log("  • Dihapus sisa $pkg (${Formatter.formatFileSize(ctx, size)})")
            count++
        }
        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Berhasil menghapus $count sisa aplikasi. Bebas: +${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= 3. PEMBERSIH THUMBNAIL & MEDIA JUNK =================

    fun bersihkanMediaJunk(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Membersihkan thumbnail & cache media galeri...")

        val targets = listOf(
            "/sdcard/DCIM/.thumbnails",
            "/sdcard/Pictures/.thumbnails",
            "/sdcard/Movies/.thumbnails",
            "/sdcard/Android/data/com.miui.gallery/cache",
            "/sdcard/Android/data/com.sec.android.gallery3d/cache",
            "/sdcard/Android/data/com.coloros.gallery3d/cache",
            "/sdcard/Android/data/com.vivo.gallery/cache"
        )

        for (t in targets) {
            val size = ukuranFolder(t)
            if (size > 0L) {
                Shell.jalankan("rm -rf '$t'/* '$t'/.[!.]* 2>/dev/null")
                log("  • Menghapus ${Formatter.formatFileSize(ctx, size)} di $t")
            }
        }

        Shell.jalankan("find /sdcard -type d -name '.trashed' -exec rm -rf {} + 2>/dev/null")
        log("  • Mengosongkan berkas sampah .trashed galeri")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Pembersihan media junk selesai. Bebas: +${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= 4. PEMBERSIH MEDIA CHAT (WA & TELEGRAM) =================

    fun bersihkanChatMedia(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Membersihkan cache media stiker WhatsApp & Telegram...")

        val targets = listOf(
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
            "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/WhatsApp/Media/.Statuses",
            "/sdcard/WhatsApp/Media/WhatsApp Stickers",
            "/sdcard/Android/data/org.telegram.messenger/cache"
        )

        var count = 0
        for (t in targets) {
            val size = ukuranFolder(t)
            if (size > 0L) {
                Shell.jalankan("rm -rf '$t'/* 2>/dev/null")
                log("  • Dibersihkan ${Formatter.formatFileSize(ctx, size)} di $t")
                count++
            }
        }

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Selesai membersihkan media chat junk. Bebas: +${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= 5. PEMBERSIH INSTALLER APK MENTAH =================

    fun scanOrphanApks(): List<Pair<String, Long>> {
        val lines = Shell.jalankan("find /sdcard/Download -maxdepth 3 -type f -name '*.apk' 2>/dev/null").out.lines()
            .filter { it.startsWith("/") }
        return lines.map { Pair(it, ukuranFolder(it)) }.filter { it.second > 0L }
    }

    fun bersihkanOrphanApks(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Menghapus installer APK mentah di folder Download...")
        val apks = scanOrphanApks()
        if (apks.isEmpty()) {
            log("  • Berkas APK installer: Bersih (0 B)")
            return 0L
        }
        var count = 0
        for ((apk, size) in apks) {
            val name = apk.substringAfterLast("/")
            Shell.jalankan("rm -f '$apk' 2>/dev/null")
            log("  • Dihapus APK: $name (${Formatter.formatFileSize(ctx, size)})")
            count++
        }
        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Dihapus $count file APK. Bebas: +${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= 6. PEMBERSIH LOG MULTI-VENDOR =================

    fun bersihkanLog(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("[START] Membersihkan log sistem & dumpstate multi-vendor...")

        val logFolders = listOf(
            "/sdcard/MIUI/debug_log",
            "/sdcard/MIUI/.debug_log",
            "/sdcard/oppo_log",
            "/sdcard/vivo_log",
            "/sdcard/ColorOS"
        )

        for (f in logFolders) {
            val size = ukuranFolder(f)
            if (size > 0L) {
                Shell.jalankan("rm -rf '$f'/* '$f'/.[!.]* 2>/dev/null")
                log("  • Dihapus log vendor $f (${Formatter.formatFileSize(ctx, size)})")
            }
        }

        Shell.jalankan("rm -rf /sdcard/Android/data/*/files/logs/* 2>/dev/null")
        val rLogcat = Shell.jalankan("logcat -b all -c")
        log("  • Buffer RAM logcat dikosongkan (status: ${rLogcat.kode})")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("[DONE] Pembersihan log sistem selesai.")
        return didapat
    }

    // ================= 7. RESET DEXOPT (ART COMPILER) =================

    fun resetDexopt(ctx: Context, log: (String) -> Unit) {
        log("[START] Mereset kompilasi ART/Dexopt ke bawaan sistem...")
        log("  • Menjalankan 'cmd package compile --reset -a'...")
        val r = Shell.jalankan("cmd package compile --reset -a")
        log("[DONE] Reset dexopt selesai (status: ${r.kode})")
        if (r.out.isNotEmpty()) {
            val lines = r.out.lines().take(4)
            lines.forEach { log("    $it") }
        }
    }

    // ================= ALAT DIAGNOSTIK & OPTIMASI CEPAT =================

    fun scanLargeFiles(ctx: Context, log: (String) -> Unit) {
        log("[DIAGNOSTIK] Memindai 25 berkas terbesar (>50 MB) di /sdcard...")
        val out = Shell.jalankan("find /sdcard -type f -size +50M -exec du -sk {} + 2>/dev/null | sort -rn | head -n 25").out
        val lines = out.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            log("  • Tidak ditemukan berkas di atas 50 MB.")
            return
        }
        log("Ditemukan ${lines.size} berkas raksasa:")
        for (l in lines) {
            val parts = l.trim().split(Regex("\\s+"), limit = 2)
            val kb = parts.getOrNull(0)?.toLongOrNull() ?: 0L
            val path = parts.getOrNull(1) ?: ""
            if (path.isNotEmpty()) {
                val formattedSize = Formatter.formatFileSize(ctx, kb * 1024L).padStart(10)
                log("  $formattedSize  $path")
            }
        }
    }

    fun bersihkanFolderKosong(ctx: Context, log: (String) -> Unit) {
        log("[TOOL] Memindai dan menghapus folder kosong terlantar di /sdcard...")
        val r = Shell.jalankan("find /sdcard -mindepth 1 -maxdepth 3 -type d -empty -not -path '*/.*' -delete 2>/dev/null")
        log("[DONE] Direktori kosong berhasil dibersihkan (status: ${r.kode}).")
    }

    fun legakanRam(ctx: Context, log: (String) -> Unit) {
        log("[TOOL] Melegakan memori RAM: Force stop aplikasi pihak ketiga di background...")
        val thirdParty = Shell.jalankan("pm list packages -3").out.lines()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it != ctx.packageName }

        var count = 0
        for (pkg in thirdParty) {
            Shell.jalankan("am force-stop $pkg")
            count++
        }
        log("[DONE] Berhasil menghentikan $count aplikasi pihak ketiga di latar belakang.")
    }

    fun infoPartisi(ctx: Context, log: (String) -> Unit) {
        log("[DIAGNOSTIK] Membaca tabel partisi storage...")
        val out = Shell.jalankan("df -h /data /storage/emulated/0 2>/dev/null").out
        out.lines().forEach { if (it.isNotBlank()) log("  $it") }
    }
}

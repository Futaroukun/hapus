package id.pribadi.berkassistem

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter

object CleanerEngine {

    data class DiskInfo(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)

    fun getDiskInfo(): DiskInfo {
        val path = Environment.getDataDirectory().path
        val stat = StatFs(path)
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = (total - free).coerceAtLeast(0L)
        return DiskInfo(total, free, used)
    }

    fun isXiaomiDevice(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val b = Build.BRAND.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
               b.contains("xiaomi") || b.contains("redmi") || b.contains("poco")
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

    // ================= SCAN ESTIMASI =================

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
            "/sdcard/Android/data/com.sec.android.gallery3d/cache"
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
        if (isXiaomiDevice()) {
            val logFolders = listOf("/sdcard/MIUI/debug_log", "/sdcard/MIUI/.debug_log")
            for (f in logFolders) {
                total += ukuranFolder(f)
            }
        }
        return total
    }

    // ================= PEMBERSIH APP CORPSES (SISA UNINSTALL) =================

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
        log("Mencari berkas sisa aplikasi yang sudah di-uninstall...")
        val corpses = scanAppCorpses(ctx)
        if (corpses.isEmpty()) {
            log("  - Berkas sisa aplikasi: Bersih (0 B)")
            return 0L
        }
        var count = 0
        for ((dir, size) in corpses) {
            val pkg = dir.substringAfterLast("/")
            Shell.jalankan("rm -rf '$dir' 2>/dev/null")
            log("  - Dihapus sisa $pkg (${Formatter.formatFileSize(ctx, size)})")
            count++
        }
        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan $count sisa aplikasi. Bebas: ${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= PEMBERSIH INSTALLER APK MENTAH =================

    fun scanOrphanApks(): List<Pair<String, Long>> {
        val lines = Shell.jalankan("find /sdcard/Download -maxdepth 3 -type f -name '*.apk' 2>/dev/null").out.lines()
            .filter { it.startsWith("/") }
        return lines.map { Pair(it, ukuranFolder(it)) }.filter { it.second > 0L }
    }

    fun bersihkanOrphanApks(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("Membersihkan installer APK di folder Download...")
        val apks = scanOrphanApks()
        if (apks.isEmpty()) {
            log("  - Berkas APK installer: Bersih (0 B)")
            return 0L
        }
        var count = 0
        for ((apk, size) in apks) {
            val name = apk.substringAfterLast("/")
            Shell.jalankan("rm -f '$apk' 2>/dev/null")
            log("  - Dihapus installer: $name (${Formatter.formatFileSize(ctx, size)})")
            count++
        }
        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan $count file APK. Bebas: ${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    // ================= PEMBERSIHAN STANDAR =================

    fun bersihkanCacheAplikasi(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("Memulai pembersihan cache aplikasi...")

        val rTrim = Shell.jalankan("pm trim-caches 999G")
        log("  - pm trim-caches selesai (kode: ${rTrim.kode})")

        val extFolders = daftarFolderBerisi("ls -d /sdcard/Android/data/*/cache 2>/dev/null")
        var countExt = 0
        for ((folder, _) in extFolders) {
            Shell.jalankan("rm -rf '$folder'/* '$folder'/.[!.]* 2>/dev/null")
            countExt++
        }
        log("  - Membersihkan $countExt folder cache eksternal (/sdcard/Android/data)")

        val rInt = Shell.jalankan("rm -rf /data/data/*/cache/* /data/data/*/code_cache/* 2>/dev/null")
        log("  - Membersihkan cache internal (/data/data/*/cache) [kode: ${rInt.kode}]")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan cache. Bebas: ${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    fun bersihkanMediaJunk(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("Memulai pembersihan thumbnail & media sampah...")

        val targets = listOf(
            "/sdcard/DCIM/.thumbnails",
            "/sdcard/Pictures/.thumbnails",
            "/sdcard/Movies/.thumbnails",
            "/sdcard/Android/data/com.miui.gallery/cache",
            "/sdcard/Android/data/com.sec.android.gallery3d/cache"
        )

        for (t in targets) {
            val size = ukuranFolder(t)
            if (size > 0L) {
                Shell.jalankan("rm -rf '$t'/* '$t'/.[!.]* 2>/dev/null")
                log("  - Menghapus ${Formatter.formatFileSize(ctx, size)} di $t")
            }
        }

        Shell.jalankan("find /sdcard -type d -name '.trashed' -exec rm -rf {} + 2>/dev/null")
        log("  - Mengosongkan folder sampah .trashed")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan media junk. Bebas: ${Formatter.formatFileSize(ctx, didapat)}")
        return didapat
    }

    fun bersihkanLog(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("Memulai pembersihan log sistem...")

        if (isXiaomiDevice()) {
            val logFolders = listOf("/sdcard/MIUI/debug_log", "/sdcard/MIUI/.debug_log")
            for (f in logFolders) {
                val size = ukuranFolder(f)
                if (size > 0L) {
                    Shell.jalankan("rm -rf '$f'/* '$f'/.[!.]* 2>/dev/null")
                    log("  - Menghapus log MIUI: $f (${Formatter.formatFileSize(ctx, size)})")
                }
            }
        }

        val rLogcat = Shell.jalankan("logcat -b all -c")
        log("  - Buffer logcat dikosongkan (kode: ${rLogcat.kode})")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan log sistem.")
        return didapat
    }

    fun resetDexopt(ctx: Context, log: (String) -> Unit) {
        log("Menjalankan reset kompilasi dexopt (ART compiler)...")
        log("Harap tunggu, proses ini memakan waktu beberapa saat...")
        val r = Shell.jalankan("cmd package compile --reset -a")
        log("Reset dexopt selesai (kode: ${r.kode})")
        if (r.out.isNotEmpty()) {
            val lines = r.out.lines().take(5)
            lines.forEach { log("  $it") }
        }
    }

    // ================= UTILITAS TAMBAHAN: LARGE FILES & BOOST RAM =================

    fun scanLargeFiles(ctx: Context, log: (String) -> Unit) {
        log("Memindai berkas besar (>100 MB) di penyimpanan internal...")
        val out = Shell.jalankan("find /sdcard -type f -size +100M -exec du -sk {} + 2>/dev/null | sort -rn | head -n 25").out
        val lines = out.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            log("  - Tidak ditemukan berkas di atas 100 MB.")
            return
        }
        log("Top berkas terbesar (>100 MB):")
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

    fun legakanRam(ctx: Context, log: (String) -> Unit) {
        log("Melegakan RAM: Menghentikan aplikasi pihak ketiga di latar belakang...")
        val thirdParty = Shell.jalankan("pm list packages -3").out.lines()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() && it != ctx.packageName }

        var count = 0
        for (pkg in thirdParty) {
            Shell.jalankan("am force-stop $pkg")
            count++
        }
        log("Berhasil force-stop $count aplikasi pihak ketiga di background.")
    }
}

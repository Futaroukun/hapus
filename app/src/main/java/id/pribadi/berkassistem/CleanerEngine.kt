package id.pribadi.berkassistem

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import java.io.File

object CleanerEngine {

    data class DiskInfo(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)

    data class TargetKategori(
        val id: String,
        val judul: String,
        val deskripsi: String,
        var estimasiByte: Long = 0L,
        var terpilih: Boolean = true,
        val perluPerhatian: Boolean = false
    )

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

    // ================= SCANNING =================

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

    // ================= PEMBERSIHAN =================

    fun bersihkanCacheAplikasi(ctx: Context, log: (String) -> Unit): Long {
        val sebelum = getDiskInfo().freeBytes
        log("Memulai pembersihan cache aplikasi...")

        val rTrim = Shell.jalankan("pm trim-caches 999G")
        log("  - pm trim-caches selesai (kode: ${rTrim.kode})")

        val extFolders = daftarFolderBerisi("ls -d /sdcard/Android/data/*/cache 2>/dev/null")
        var countExt = 0
        for ((folder, size) in extFolders) {
            Shell.jalankan("rm -rf '$folder'/* '$folder'/.[!.]* 2>/dev/null")
            countExt++
        }
        log("  - Membersihkan $countExt folder cache eksternal (/sdcard/Android/data)")

        val rInt = Shell.jalankan("rm -rf /data/data/*/cache/* /data/data/*/code_cache/* 2>/dev/null")
        log("  - Membersihkan cache internal (/data/data/*/cache) [kode: ${rInt.kode}]")

        val sesudah = getDiskInfo().freeBytes
        val didapat = (sesudah - sebelum).coerceAtLeast(0L)
        log("Selesai membersihkan cache. Ruang bebas bertambah: ${Formatter.formatFileSize(ctx, didapat)}")
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
        log("Harap tunggu, proses ini memakan waktu 10-60 detik...")
        val r = Shell.jalankan("cmd package compile --reset -a", timeoutDetik = 120)
        log("Reset dexopt selesai (kode: ${r.kode})")
        if (r.out.isNotEmpty()) {
            val lines = r.out.lines().take(5)
            lines.forEach { log("  $it") }
        }
    }
}

package id.pribadi.berkassistem

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager

/** Membaca ukuran kode, data, dan cache tiap aplikasi (angka langsung dari sistem, bukan cache lama). */
object Stat {

    class Paket(
        val nama: String,
        val label: String,
        val app: Long,
        val data: Long,
        val cache: Long
    )

    fun ambil(ctx: Context): List<Paket> {
        val ssm = ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val pm = ctx.packageManager
        val user = Process.myUserHandle()
        val hasil = ArrayList<Paket>()
        for (info in pm.getInstalledApplications(0)) {
            try {
                val s = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user)
                hasil.add(
                    Paket(
                        info.packageName,
                        pm.getApplicationLabel(info).toString(),
                        s.appBytes,
                        s.dataBytes,
                        s.cacheBytes
                    )
                )
            } catch (e: Exception) {
                // aplikasi ini dilewati
            }
        }
        return hasil
    }
}

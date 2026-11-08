package id.pribadi.berkassistem

import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Shell runner via Shizuku ADB access with timeout & error handling.
 */
object Shell {

    data class Hasil(val kode: Int, val out: String, val err: String)

    private val newProcessMethod by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        } catch (e: Throwable) {
            null
        }
    }

    fun jalankan(perintah: String, timeoutDetik: Long = 60): Hasil {
        val method = newProcessMethod ?: return Hasil(-1, "", "Shizuku newProcess tidak ditemukan")
        val proses = method.invoke(null, arrayOf("sh", "-c", perintah), null, null) as Process

        var err = ""
        var out = ""

        val pembacaErr = Thread {
            try {
                err = proses.errorStream.bufferedReader().readText()
            } catch (_: Throwable) {}
        }
        val pembacaOut = Thread {
            try {
                out = proses.inputStream.bufferedReader().readText()
            } catch (_: Throwable) {}
        }

        pembacaErr.start()
        pembacaOut.start()

        val selesai = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            proses.waitFor(timeoutDetik, TimeUnit.SECONDS)
        } else {
            proses.waitFor()
            true
        }

        if (!selesai) {
            proses.destroy()
            return Hasil(-1, out.trim(), "Proses melewati batas waktu (${timeoutDetik} detik)")
        }

        pembacaErr.join(1000)
        pembacaOut.join(1000)

        val kode = try { proses.exitValue() } catch (_: Throwable) { 0 }
        return Hasil(kode, out.trim(), err.trim())
    }
}

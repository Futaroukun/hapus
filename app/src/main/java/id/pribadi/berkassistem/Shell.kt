package id.pribadi.berkassistem

import rikka.shizuku.Shizuku

/**
 * Shell runner via Shizuku ADB access.
 * Menggunakan pembacaan stream & blocking waitFor standar Shizuku
 * untuk menghindari crash IllegalArgumentException: process hasn't exited.
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
        } catch (_: Throwable) {
            null
        }
    }

    fun jalankan(perintah: String): Hasil {
        val method = newProcessMethod ?: return Hasil(-1, "", "Shizuku newProcess tidak ditemukan")
        return try {
            val proses = method.invoke(null, arrayOf("sh", "-c", perintah), null, null) as Process

            var err = ""
            val pembacaErr = Thread {
                try {
                    err = proses.errorStream.bufferedReader().readText()
                } catch (_: Throwable) {}
            }
            pembacaErr.start()

            val out = try {
                proses.inputStream.bufferedReader().readText()
            } catch (_: Throwable) {
                ""
            }

            try {
                pembacaErr.join(3000)
            } catch (_: Throwable) {}

            val kode = try {
                proses.waitFor()
            } catch (_: Throwable) {
                0
            }

            Hasil(kode, out.trim(), err.trim())
        } catch (e: Throwable) {
            val msg = e.cause?.message ?: e.message ?: "Unknown shell error"
            Hasil(-1, "", msg)
        }
    }
}

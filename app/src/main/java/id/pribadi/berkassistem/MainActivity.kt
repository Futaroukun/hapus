package id.pribadi.berkassistem

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.text.method.ScrollingMovementMethod
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var mainScroll: ScrollView
    private lateinit var tvDeviceInfo: TextView
    private lateinit var tvShizukuPill: TextView
    private lateinit var tvStoragePct: TextView
    private lateinit var pbStorage: ProgressBar
    private lateinit var tvFreeStorage: TextView
    private lateinit var tvTotalStorage: TextView

    private lateinit var cbCache: CheckBox
    private lateinit var cbMedia: CheckBox
    private lateinit var cbLogs: CheckBox
    private lateinit var cbDexopt: CheckBox

    private lateinit var tvSizeCache: TextView
    private lateinit var tvSizeMedia: TextView
    private lateinit var tvSizeLogs: TextView

    private lateinit var btnScan: Button
    private lateinit var btnClean: Button

    private lateinit var layoutProgress: LinearLayout
    private lateinit var tvStatusAction: TextView
    private lateinit var tvConsole: TextView
    private lateinit var btnCopyLog: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val bufferLog = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val logFileName = "RuangLega-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

    private val listenerBinder = Shizuku.OnBinderReceivedListener { runOnUiThread { refreshShizukuStatus() } }
    private val listenerBinderDead = Shizuku.OnBinderDeadListener { runOnUiThread { refreshShizukuStatus() } }
    private val listenerPermission = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refreshShizukuStatus() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()

        Shizuku.addBinderReceivedListenerSticky(listenerBinder)
        Shizuku.addBinderDeadListener(listenerBinderDead)
        Shizuku.addRequestPermissionResultListener(listenerPermission)

        refreshShizukuStatus()
        refreshStorageInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuStatus()
        refreshStorageInfo()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(listenerBinder)
        Shizuku.removeBinderDeadListener(listenerBinderDead)
        Shizuku.removeRequestPermissionResultListener(listenerPermission)
        executor.shutdown()
        super.onDestroy()
    }

    private fun initViews() {
        mainScroll = findViewById(R.id.main_scroll)
        tvDeviceInfo = findViewById(R.id.tv_device_info)
        tvShizukuPill = findViewById(R.id.tv_shizuku_pill)
        tvStoragePct = findViewById(R.id.tv_storage_pct)
        pbStorage = findViewById(R.id.pb_storage)
        tvFreeStorage = findViewById(R.id.tv_free_storage)
        tvTotalStorage = findViewById(R.id.tv_total_storage)

        cbCache = findViewById(R.id.cb_cache)
        cbMedia = findViewById(R.id.cb_media)
        cbLogs = findViewById(R.id.cb_logs)
        cbDexopt = findViewById(R.id.cb_dexopt)

        tvSizeCache = findViewById(R.id.tv_size_cache)
        tvSizeMedia = findViewById(R.id.tv_size_media)
        tvSizeLogs = findViewById(R.id.tv_size_logs)

        btnScan = findViewById(R.id.btn_scan)
        btnClean = findViewById(R.id.btn_clean)

        layoutProgress = findViewById(R.id.layout_progress)
        tvStatusAction = findViewById(R.id.tv_status_action)
        tvConsole = findViewById(R.id.tv_console)
        tvConsole.movementMethod = ScrollingMovementMethod()
        btnCopyLog = findViewById(R.id.btn_copy_log)

        val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        tvDeviceInfo.text = "$brand ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }

    private fun setupListeners() {
        tvShizukuPill.setOnClickListener {
            handleShizukuPillClick()
        }

        btnScan.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("Shizuku belum siap. Buka dan aktifkan Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            startScan()
        }

        btnClean.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("Shizuku belum siap. Buka dan aktifkan Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            confirmAndClean()
        }

        btnCopyLog.setOnClickListener {
            val text = synchronized(bufferLog) { bufferLog.toString() }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("RuangLega Log", text))
            Toast.makeText(this, "Log disalin ke papan klip", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isShizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    private fun refreshShizukuStatus() {
        try {
            when {
                !Shizuku.pingBinder() -> {
                    tvShizukuPill.text = "● Shizuku: Nonaktif"
                    tvShizukuPill.setTextColor(0xFFEF4444.toInt())
                }
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> {
                    tvShizukuPill.text = "● Shizuku: Perlu Izin"
                    tvShizukuPill.setTextColor(0xFFF59E0B.toInt())
                }
                else -> {
                    tvShizukuPill.text = "● Shizuku: Aktif"
                    tvShizukuPill.setTextColor(0xFF10B981.toInt())
                }
            }
        } catch (_: Throwable) {
            tvShizukuPill.text = "● Shizuku: Gagal"
            tvShizukuPill.setTextColor(0xFFEF4444.toInt())
        }
    }

    private fun handleShizukuPillClick() {
        try {
            if (!Shizuku.pingBinder()) {
                val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    appendLog("Aplikasi Shizuku belum terpasang.")
                }
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(101)
            } else {
                Toast.makeText(this, "Shizuku siap digunakan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            appendLog("Error Shizuku: ${e.message}")
        }
    }

    private fun refreshStorageInfo() {
        val disk = CleanerEngine.getDiskInfo()
        val pct = if (disk.totalBytes > 0) ((disk.usedBytes * 100) / disk.totalBytes).toInt() else 0

        tvStoragePct.text = "$pct%"
        pbStorage.progress = pct
        tvFreeStorage.text = "Ruang Bebas: ${Formatter.formatFileSize(this, disk.freeBytes)}"
        tvTotalStorage.text = "Total: ${Formatter.formatFileSize(this, disk.totalBytes)}"
    }

    private fun setBusy(busy: Boolean, actionText: String = "") {
        runOnUiThread {
            btnScan.isEnabled = !busy
            btnClean.isEnabled = !busy
            cbCache.isEnabled = !busy
            cbMedia.isEnabled = !busy
            cbLogs.isEnabled = !busy
            cbDexopt.isEnabled = !busy

            if (busy) {
                layoutProgress.visibility = View.VISIBLE
                tvStatusAction.text = actionText
            } else {
                layoutProgress.visibility = View.GONE
            }
        }
    }

    private fun appendLog(msg: String) {
        val line = synchronized(bufferLog) {
            val formatted = "${timeFormat.format(Date())} $msg"
            bufferLog.append(formatted).append('\n')
            formatted
        }
        runOnUiThread {
            tvConsole.append(line + "\n")
            val scrollAmount = tvConsole.layout?.getLineTop(tvConsole.lineCount) ?: 0
            if (scrollAmount > tvConsole.height) {
                tvConsole.scrollTo(0, scrollAmount - tvConsole.height)
            }
        }
    }

    private fun startScan() {
        setBusy(true, "Memindai kapasitas sampah...")
        appendLog("=== MULAI PEMINDAIAN ===")

        executor.execute {
            try {
                val sizeCache = CleanerEngine.hitungEstimasiCache(this)
                runOnUiThread {
                    tvSizeCache.text = Formatter.formatFileSize(this, sizeCache)
                }
                appendLog("Estimasi cache aplikasi: ${Formatter.formatFileSize(this, sizeCache)}")

                val sizeMedia = CleanerEngine.hitungEstimasiMediaJunk()
                runOnUiThread {
                    tvSizeMedia.text = Formatter.formatFileSize(this, sizeMedia)
                }
                appendLog("Estimasi media junk & thumbnails: ${Formatter.formatFileSize(this, sizeMedia)}")

                val sizeLogs = CleanerEngine.hitungEstimasiLog()
                runOnUiThread {
                    tvSizeLogs.text = Formatter.formatFileSize(this, sizeLogs)
                }
                appendLog("Estimasi log sistem: ${Formatter.formatFileSize(this, sizeLogs)}")

                val totalEstimasi = sizeCache + sizeMedia + sizeLogs
                appendLog("Total potensi pembebasan ruang: ${Formatter.formatFileSize(this, totalEstimasi)}")
            } catch (e: Throwable) {
                appendLog("Gagal memindai: ${e.message}")
            } finally {
                setBusy(false)
                runOnUiThread { refreshStorageInfo() }
                saveLogFile()
            }
        }
    }

    private fun confirmAndClean() {
        val items = mutableListOf<String>()
        if (cbCache.isChecked) items.add("• Cache Aplikasi (Internal & Eksternal)")
        if (cbMedia.isChecked) items.add("• Thumbnail & Media Sampah")
        if (cbLogs.isChecked) items.add("• Log Sistem & Buffer Logcat")
        if (cbDexopt.isChecked) items.add("• Reset Kompilasi Dexopt (ART)")

        if (items.isEmpty()) {
            Toast.makeText(this, "Pilih minimal satu target pembersihan", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pembersihan")
            .setMessage("Target yang akan dibersihkan:\n\n" + items.joinToString("\n") + "\n\nLanjutkan?")
            .setPositiveButton("Bersihkan") { _, _ ->
                executeCleaning()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeCleaning() {
        setBusy(true, "Membersihkan target yang dipilih...")
        appendLog("=== MEMULAI PROSES PEMBERSIHAN ===")

        val doCache = cbCache.isChecked
        val doMedia = cbMedia.isChecked
        val doLogs = cbLogs.isChecked
        val doDexopt = cbDexopt.isChecked

        executor.execute {
            val sebelum = CleanerEngine.getDiskInfo().freeBytes
            try {
                if (doCache) {
                    CleanerEngine.bersihkanCacheAplikasi(this) { appendLog(it) }
                }
                if (doMedia) {
                    CleanerEngine.bersihkanMediaJunk(this) { appendLog(it) }
                }
                if (doLogs) {
                    CleanerEngine.bersihkanLog(this) { appendLog(it) }
                }
                if (doDexopt) {
                    CleanerEngine.resetDexopt(this) { appendLog(it) }
                }

                val sesudah = CleanerEngine.getDiskInfo().freeBytes
                val bebasDidapat = (sesudah - sebelum).coerceAtLeast(0L)
                appendLog("=== PEMBERSIHAN SELESAI ===")
                appendLog("Total ruang yang berhasil dibebaskan: ${Formatter.formatFileSize(this, bebasDidapat)}")
            } catch (e: Throwable) {
                appendLog("Kesalahan saat membersihkan: ${e.message}")
            } finally {
                setBusy(false)
                runOnUiThread {
                    refreshStorageInfo()
                    Toast.makeText(this, "Pembersihan selesai", Toast.LENGTH_SHORT).show()
                }
                saveLogFile()
            }
        }
    }

    private fun saveLogFile() {
        try {
            val dir = getExternalFilesDir(null) ?: filesDir
            val tempFile = File(dir, "temp_log.txt")
            val content = synchronized(bufferLog) { bufferLog.toString() }
            tempFile.writeText(content)

            val dest = "/sdcard/Download/$logFileName"
            val res = Shell.jalankan("cp '${tempFile.absolutePath}' '$dest'")
            if (res.kode == 0) {
                appendLog("Log tersimpan di: $dest")
            }
        } catch (_: Throwable) {}
    }
}

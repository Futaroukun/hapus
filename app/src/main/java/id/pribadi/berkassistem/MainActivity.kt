package id.pribadi.berkassistem

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
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
    private lateinit var tvEstTotalJunk: TextView

    // Quick Tools
    private lateinit var btnLargeFiles: Button
    private lateinit var btnBoostRam: Button
    private lateinit var btnCleanEmptyDirs: Button
    private lateinit var btnDiskInfo: Button

    // Card Containers
    private lateinit var cardCache: LinearLayout
    private lateinit var cardCorpses: LinearLayout
    private lateinit var cardMedia: LinearLayout
    private lateinit var cardChat: LinearLayout
    private lateinit var cardApks: LinearLayout
    private lateinit var cardLogs: LinearLayout
    private lateinit var cardDexopt: LinearLayout

    // Target Checkboxes
    private lateinit var cbCache: CheckBox
    private lateinit var cbCorpses: CheckBox
    private lateinit var cbMedia: CheckBox
    private lateinit var cbChat: CheckBox
    private lateinit var cbApks: CheckBox
    private lateinit var cbLogs: CheckBox
    private lateinit var cbDexopt: CheckBox

    // Target Size Labels
    private lateinit var tvSizeCache: TextView
    private lateinit var tvSizeCorpses: TextView
    private lateinit var tvSizeMedia: TextView
    private lateinit var tvSizeChat: TextView
    private lateinit var tvSizeApks: TextView
    private lateinit var tvSizeLogs: TextView

    // Primary Actions
    private lateinit var btnScan: Button
    private lateinit var btnClean: Button

    // Progress & Console
    private lateinit var layoutProgress: LinearLayout
    private lateinit var tvStatusAction: TextView
    private lateinit var tvConsole: TextView
    private lateinit var btnClearLog: TextView
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
        tvEstTotalJunk = findViewById(R.id.tv_est_total_junk)

        btnLargeFiles = findViewById(R.id.btn_large_files)
        btnBoostRam = findViewById(R.id.btn_boost_ram)
        btnCleanEmptyDirs = findViewById(R.id.btn_clean_empty_dirs)
        btnDiskInfo = findViewById(R.id.btn_disk_info)

        cardCache = findViewById(R.id.card_cache)
        cardCorpses = findViewById(R.id.card_corpses)
        cardMedia = findViewById(R.id.card_media)
        cardChat = findViewById(R.id.card_chat)
        cardApks = findViewById(R.id.card_apks)
        cardLogs = findViewById(R.id.card_logs)
        cardDexopt = findViewById(R.id.card_dexopt)

        cbCache = findViewById(R.id.cb_cache)
        cbCorpses = findViewById(R.id.cb_corpses)
        cbMedia = findViewById(R.id.cb_media)
        cbChat = findViewById(R.id.cb_chat)
        cbApks = findViewById(R.id.cb_apks)
        cbLogs = findViewById(R.id.cb_logs)
        cbDexopt = findViewById(R.id.cb_dexopt)

        tvSizeCache = findViewById(R.id.tv_size_cache)
        tvSizeCorpses = findViewById(R.id.tv_size_corpses)
        tvSizeMedia = findViewById(R.id.tv_size_media)
        tvSizeChat = findViewById(R.id.tv_size_chat)
        tvSizeApks = findViewById(R.id.tv_size_apks)
        tvSizeLogs = findViewById(R.id.tv_size_logs)

        btnScan = findViewById(R.id.btn_scan)
        btnClean = findViewById(R.id.btn_clean)

        layoutProgress = findViewById(R.id.layout_progress)
        tvStatusAction = findViewById(R.id.tv_status_action)
        tvConsole = findViewById(R.id.tv_console)
        tvConsole.movementMethod = ScrollingMovementMethod()
        btnClearLog = findViewById(R.id.btn_clear_log)
        btnCopyLog = findViewById(R.id.btn_copy_log)

        tvDeviceInfo.text = CleanerEngine.getDeviceInfo()
    }

    private fun setupListeners() {
        tvShizukuPill.setOnClickListener { handleShizukuPillClick() }

        // Card tap-to-toggle
        cardCache.setOnClickListener { cbCache.toggle() }
        cardCorpses.setOnClickListener { cbCorpses.toggle() }
        cardMedia.setOnClickListener { cbMedia.toggle() }
        cardChat.setOnClickListener { cbChat.toggle() }
        cardApks.setOnClickListener { cbApks.toggle() }
        cardLogs.setOnClickListener { cbLogs.toggle() }
        cardDexopt.setOnClickListener { cbDexopt.toggle() }

        // Quick Tool 1: Large Files
        btnLargeFiles.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            setBusy(true, "Memindai 25 berkas terbesar...")
            executor.execute {
                try {
                    CleanerEngine.scanLargeFiles(this) { appendLog(it) }
                } catch (e: Throwable) {
                    appendLog("[ERR] Gagal memindai berkas besar: ${e.message}")
                } finally {
                    setBusy(false)
                }
            }
        }

        // Quick Tool 2: Boost RAM
        btnBoostRam.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            setBusy(true, "Melegakan memori RAM...")
            executor.execute {
                try {
                    CleanerEngine.legakanRam(this) { appendLog(it) }
                    runOnUiThread { Toast.makeText(this, "RAM berhasil dilegakan", Toast.LENGTH_SHORT).show() }
                } catch (e: Throwable) {
                    appendLog("[ERR] Gagal melegakan RAM: ${e.message}")
                } finally {
                    setBusy(false)
                }
            }
        }

        // Quick Tool 3: Clean Empty Dirs
        btnCleanEmptyDirs.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            setBusy(true, "Menghapus direktori kosong...")
            executor.execute {
                try {
                    CleanerEngine.bersihkanFolderKosong(this) { appendLog(it) }
                    runOnUiThread { Toast.makeText(this, "Direktori kosong dibersihkan", Toast.LENGTH_SHORT).show() }
                } catch (e: Throwable) {
                    appendLog("[ERR] Gagal: ${e.message}")
                } finally {
                    setBusy(false)
                }
            }
        }

        // Quick Tool 4: Disk Partition Info
        btnDiskInfo.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            setBusy(true, "Membaca tabel partisi...")
            executor.execute {
                try {
                    CleanerEngine.infoPartisi(this) { appendLog(it) }
                } catch (e: Throwable) {
                    appendLog("[ERR] Gagal: ${e.message}")
                } finally {
                    setBusy(false)
                }
            }
        }

        // Primary: Scan
        btnScan.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            startScan()
        }

        // Primary: Clean
        btnClean.setOnClickListener {
            if (!isShizukuReady()) {
                appendLog("[WARN] Shizuku belum aktif. Berikan izin Shizuku terlebih dahulu.")
                return@setOnClickListener
            }
            confirmAndClean()
        }

        // Console toolbar
        btnClearLog.setOnClickListener {
            synchronized(bufferLog) { bufferLog.clear() }
            tvConsole.text = ""
        }

        btnCopyLog.setOnClickListener {
            val text = synchronized(bufferLog) { bufferLog.toString() }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("RuangLega Log", text))
            Toast.makeText(this, "Log disalin ke clipboard", Toast.LENGTH_SHORT).show()
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
                    appendLog("[ERR] Aplikasi Shizuku belum terpasang.")
                }
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(101)
            } else {
                Toast.makeText(this, "Shizuku siap digunakan", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            appendLog("[ERR] Error Shizuku: ${e.message}")
        }
    }

    private fun refreshStorageInfo() {
        val disk = CleanerEngine.getDiskInfo()
        tvStoragePct.text = "${disk.pctUsed}% Terpakai"
        pbStorage.progress = disk.pctUsed
        tvFreeStorage.text = Formatter.formatFileSize(this, disk.freeBytes)
        tvTotalStorage.text = "Total Kapasitas: ${Formatter.formatFileSize(this, disk.totalBytes)}"
    }

    private fun setBusy(busy: Boolean, actionText: String = "") {
        runOnUiThread {
            btnScan.isEnabled = !busy
            btnClean.isEnabled = !busy
            btnLargeFiles.isEnabled = !busy
            btnBoostRam.isEnabled = !busy
            btnCleanEmptyDirs.isEnabled = !busy
            btnDiskInfo.isEnabled = !busy

            cbCache.isEnabled = !busy
            cbCorpses.isEnabled = !busy
            cbMedia.isEnabled = !busy
            cbChat.isEnabled = !busy
            cbApks.isEnabled = !busy
            cbLogs.isEnabled = !busy
            cbDexopt.isEnabled = !busy

            cardCache.isClickable = !busy
            cardCorpses.isClickable = !busy
            cardMedia.isClickable = !busy
            cardChat.isClickable = !busy
            cardApks.isClickable = !busy
            cardLogs.isClickable = !busy
            cardDexopt.isClickable = !busy

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
        setBusy(true, "Memindai seluruh kategori sampah...")
        appendLog("======================================")
        appendLog("[SCAN] MEMULAI PEMINDAIAN LENGKAP...")

        executor.execute {
            try {
                val sizeCache = CleanerEngine.hitungEstimasiCache(this)
                runOnUiThread { tvSizeCache.text = Formatter.formatFileSize(this, sizeCache) }
                appendLog("  • Cache Aplikasi: ${Formatter.formatFileSize(this, sizeCache)}")

                val sizeCorpses = CleanerEngine.hitungEstimasiCorpses(this)
                runOnUiThread { tvSizeCorpses.text = Formatter.formatFileSize(this, sizeCorpses) }
                appendLog("  • Sisa Aplikasi: ${Formatter.formatFileSize(this, sizeCorpses)}")

                val sizeMedia = CleanerEngine.hitungEstimasiMediaJunk()
                runOnUiThread { tvSizeMedia.text = Formatter.formatFileSize(this, sizeMedia) }
                appendLog("  • Thumbnails & Galeri: ${Formatter.formatFileSize(this, sizeMedia)}")

                val sizeChat = CleanerEngine.hitungEstimasiChatMedia()
                runOnUiThread { tvSizeChat.text = Formatter.formatFileSize(this, sizeChat) }
                appendLog("  • Media Chat WA/Telegram: ${Formatter.formatFileSize(this, sizeChat)}")

                val sizeApks = CleanerEngine.hitungEstimasiApks()
                runOnUiThread { tvSizeApks.text = Formatter.formatFileSize(this, sizeApks) }
                appendLog("  • Installer APK Mentah: ${Formatter.formatFileSize(this, sizeApks)}")

                val sizeLogs = CleanerEngine.hitungEstimasiLog()
                runOnUiThread { tvSizeLogs.text = Formatter.formatFileSize(this, sizeLogs) }
                appendLog("  • Log Sistem Vendor: ${Formatter.formatFileSize(this, sizeLogs)}")

                val totalEstimasi = sizeCache + sizeCorpses + sizeMedia + sizeChat + sizeApks + sizeLogs
                runOnUiThread {
                    tvEstTotalJunk.text = "Potensi Bersih: ${Formatter.formatFileSize(this, totalEstimasi)}"
                }
                appendLog("[OK] Total potensi ruang yang bisa dibebaskan: ${Formatter.formatFileSize(this, totalEstimasi)}")
                appendLog("======================================")
            } catch (e: Throwable) {
                appendLog("[ERR] Gagal memindai: ${e.message}")
            } finally {
                setBusy(false)
                runOnUiThread { refreshStorageInfo() }
                saveLogFile()
            }
        }
    }

    private fun confirmAndClean() {
        val items = mutableListOf<String>()
        if (cbCache.isChecked) items.add("• Cache Aplikasi Lengkap")
        if (cbCorpses.isChecked) items.add("• Sisa Aplikasi (App Corpses)")
        if (cbMedia.isChecked) items.add("• Thumbnails & Cache Galeri")
        if (cbChat.isChecked) items.add("• Media Chat (WA & Telegram)")
        if (cbApks.isChecked) items.add("• File APK di Download")
        if (cbLogs.isChecked) items.add("• Log Sistem & Logcat Buffer")
        if (cbDexopt.isChecked) items.add("• Reset Kompilasi Dexopt (ART)")

        if (items.isEmpty()) {
            Toast.makeText(this, "Pilih minimal satu target pembersihan", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pembersihan")
            .setMessage("Target yang dipilih:\n\n" + items.joinToString("\n") + "\n\nEksekusi pembersihan sekarang?")
            .setPositiveButton("Bersihkan") { _, _ ->
                executeCleaning()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeCleaning() {
        setBusy(true, "Membersihkan target yang dipilih...")
        appendLog("======================================")
        appendLog("[CLEAN] MEMULAI PROSES PEMBERSIHAN...")

        val doCache = cbCache.isChecked
        val doCorpses = cbCorpses.isChecked
        val doMedia = cbMedia.isChecked
        val doChat = cbChat.isChecked
        val doApks = cbApks.isChecked
        val doLogs = cbLogs.isChecked
        val doDexopt = cbDexopt.isChecked

        executor.execute {
            val sebelum = CleanerEngine.getDiskInfo().freeBytes
            try {
                if (doCache) CleanerEngine.bersihkanCacheAplikasi(this) { appendLog(it) }
                if (doCorpses) CleanerEngine.bersihkanAppCorpses(this) { appendLog(it) }
                if (doMedia) CleanerEngine.bersihkanMediaJunk(this) { appendLog(it) }
                if (doChat) CleanerEngine.bersihkanChatMedia(this) { appendLog(it) }
                if (doApks) CleanerEngine.bersihkanOrphanApks(this) { appendLog(it) }
                if (doLogs) CleanerEngine.bersihkanLog(this) { appendLog(it) }
                if (doDexopt) CleanerEngine.resetDexopt(this) { appendLog(it) }

                val sesudah = CleanerEngine.getDiskInfo().freeBytes
                val bebasDidapat = (sesudah - sebelum).coerceAtLeast(0L)
                appendLog("[SUCCESS] PEMBERSIHAN SELESAI!")
                appendLog("Total ruang yang berhasil dibebaskan: ${Formatter.formatFileSize(this, bebasDidapat)}")
                appendLog("======================================")
            } catch (e: Throwable) {
                appendLog("[ERR] Kesalahan pembersihan: ${e.message}")
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
                appendLog("[LOG] Log lengkap tersimpan di: $dest")
            }
        } catch (_: Throwable) {}
    }
}

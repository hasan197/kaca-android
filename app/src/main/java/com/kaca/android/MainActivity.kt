package com.kaca.android

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etQuality: EditText
    private lateinit var btnStart: MaterialButton
    private lateinit var btnScan: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private lateinit var vStatusDot: ImageView
    private lateinit var vStatusPulse: View
    private lateinit var tvTabScan: TextView
    private lateinit var tvTabManual: TextView
    private lateinit var vTabIndicator: View
    private lateinit var layoutScanTab: View
    private lateinit var layoutManualTab: View

    private var projectionManager: MediaProjectionManager? = null
    private var pendingQrHost: String? = null
    private var pendingQrPort: String? = null
    private var activeTab = 0

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MirrorService.pendingResultCode = result.resultCode
            MirrorService.pendingResultData = result.data
            MirrorService.lastError = ""

            pendingQrHost?.let { host ->
                pendingQrPort?.let { port ->
                    pendingQrHost = null
                    pendingQrPort = null
                    startMirror(host, port.toIntOrNull() ?: 27183, 75)
                    return@let
                }
            }
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 27183
            val quality = etQuality.text.toString().trim().toIntOrNull() ?: 75
            startMirror(host, port, quality)
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Izin screen capture ditolak", Snackbar.LENGTH_SHORT).show()
        }
    }

    private val qrScanLauncher = registerForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val text = result.contents
        if (text != null) {
            val qrText = text.trim()
            val host: String
            val port: String
            if (qrText.contains(":")) {
                val parts = qrText.split(":", limit = 2)
                host = parts[0]
                port = parts[1]
            } else {
                host = qrText
                port = "27183"
            }

            pendingQrHost = host
            pendingQrPort = port

            Snackbar.make(findViewById(android.R.id.content), "QR: $host:$port — menghubungkan...", Snackbar.LENGTH_SHORT).show()

            val intent = projectionManager?.createScreenCaptureIntent()
            if (intent != null) {
                screenCaptureLauncher.launch(intent)
            }
        } else {
            Snackbar.make(findViewById(android.R.id.content), "QR tidak terbaca — coba lagi", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etQuality = findViewById(R.id.etQuality)
        btnStart = findViewById(R.id.btnStart)
        btnScan = findViewById(R.id.btnScan)
        tvStatus = findViewById(R.id.tvStatus)
        tvError = findViewById(R.id.tvError)
        vStatusDot = findViewById(R.id.vStatusDot)
        vStatusPulse = findViewById(R.id.vStatusPulse)
        tvTabScan = findViewById(R.id.tvTabScan)
        tvTabManual = findViewById(R.id.tvTabManual)
        vTabIndicator = findViewById(R.id.vTabIndicator)
        layoutScanTab = findViewById(R.id.layoutScanTab)
        layoutManualTab = findViewById(R.id.layoutManualTab)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val prefs = getSharedPreferences("kaca", MODE_PRIVATE)
        etHost.setText(prefs.getString("host", ""))
        etPort.setText(prefs.getString("port", "27183"))
        etQuality.setText(prefs.getString("quality", "75"))

        updateStatusUI()

        tvTabScan.setOnClickListener { selectTab(0) }
        tvTabManual.setOnClickListener { selectTab(1) }

        btnScan.setOnClickListener {
            Log.i("Kaca", "Scan QR clicked")
            try {
                qrScanLauncher.launch(com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt("Scan QR code dari Mac")
                    setBeepEnabled(false)
                })
            } catch (e: Exception) {
                Log.e("Kaca", "Scan launch failed", e)
                Snackbar.make(findViewById(android.R.id.content), "Gagal buka kamera: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }

        btnStart.setOnClickListener {
            val host = etHost.text.toString().trim()
            val port = etPort.text.toString().trim().toIntOrNull() ?: 27183
            val quality = etQuality.text.toString().trim().toIntOrNull() ?: 75

            if (host.isEmpty()) {
                etHost.error = "Host IP wajib diisi"
                etHost.requestFocus()
                return@setOnClickListener
            }

            getSharedPreferences("kaca", MODE_PRIVATE).edit()
                .putString("host", host)
                .putString("port", port.toString())
                .putString("quality", quality.toString())
                .apply()

            pendingQrHost = host
            pendingQrPort = port.toString()

            val intent = projectionManager?.createScreenCaptureIntent() ?: return@setOnClickListener
            screenCaptureLauncher.launch(intent)
        }
    }

    private fun selectTab(index: Int) {
        if (index == activeTab) return
        activeTab = index

        val isScan = index == 0
        layoutScanTab.visibility = if (isScan) View.VISIBLE else View.GONE
        layoutManualTab.visibility = if (isScan) View.GONE else View.VISIBLE

        val tabWidth = tvTabScan.width
        val indicatorTargetX = if (isScan) 0f else (tabWidth.toFloat())

        vTabIndicator.animate()
            .translationX(indicatorTargetX)
            .setDuration(250)
            .start()

        tvTabScan.setTextColor(ContextCompat.getColor(this,
            if (isScan) R.color.brand_blue else R.color.neutral_400))
        tvTabManual.setTextColor(ContextCompat.getColor(this,
            if (isScan) R.color.neutral_400 else R.color.brand_blue))
    }

    private fun startMirror(host: String, port: Int, quality: Int) {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_START
            putExtra(MirrorService.EXTRA_HOST, host)
            putExtra(MirrorService.EXTRA_PORT, port)
            putExtra(MirrorService.EXTRA_QUALITY, quality)
        }
        tvStatus.text = "Memulai..."
        setStatusDot("idle")
        startForegroundService(intent)

        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ checkServiceState(h) }, 300)
    }

    private fun checkServiceState(h: Handler) {
        updateStatusUI()
        if (MirrorService.isRunning || MirrorService.lastError.isNotEmpty()) return
        h.postDelayed({ checkServiceState(h) }, 500)
    }

    private fun stopMirror() {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_STOP
        }
        startService(intent)
        updateStatusUI()
    }

    private fun updateStatusUI() {
        val running = MirrorService.isRunning
        val err = MirrorService.lastError
        tvStatus.text = when {
            running -> "MENGIRIM KE ${MirrorService.currentTarget}"
            err.isNotEmpty() -> "TERJADI ERROR"
            else -> "SISTEM SIAP"
        }
        if (err.isNotEmpty()) {
            tvError.text = err
            tvError.visibility = View.VISIBLE
            setStatusDot("error")
        } else if (running) {
            tvError.visibility = View.GONE
            setStatusDot("active")
        } else {
            tvError.visibility = View.GONE
            setStatusDot("idle")
        }
    }

    private fun setStatusDot(state: String) {
        val colorRes = when (state) {
            "active" -> R.color.status_active
            "error" -> R.color.status_error
            else -> R.color.status_idle
        }
        vStatusDot.setColorFilter(ContextCompat.getColor(this, colorRes))
        vStatusPulse.background?.setColorFilter(ContextCompat.getColor(this, colorRes), android.graphics.PorterDuff.Mode.SRC_IN)
        vStatusPulse.isVisible = state == "active"
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    companion object {
        private const val REQUEST_CODE_PROJECTION = 1001
    }
}

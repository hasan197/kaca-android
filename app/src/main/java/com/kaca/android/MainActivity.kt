package com.kaca.android

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etQuality: EditText
    private lateinit var btnStart: MaterialButton
    private lateinit var tvFooterStatus: TextView
    private lateinit var vFooterDot: View
    private lateinit var tvConnectingDetail: TextView

    private lateinit var stateInitial: View
    private lateinit var stateScanning: View
    private lateinit var stateManual: View
    private lateinit var stateConnecting: View

    private lateinit var vBgBlobTop: View
    private lateinit var vBgBlobBottom: View

    private lateinit var vScanLine: View

    private var projectionManager: MediaProjectionManager? = null
    private var pendingQrHost: String? = null
    private var pendingQrPort: String? = null
    private val scanHandler = Handler(Looper.getMainLooper())

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
            showState("initial")
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
            showState("initial")
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
        tvFooterStatus = findViewById(R.id.tvFooterStatus)
        vFooterDot = findViewById(R.id.vFooterDot)
        tvConnectingDetail = findViewById(R.id.tvConnectingDetail)

        stateInitial = findViewById(R.id.stateInitial)
        stateScanning = findViewById(R.id.stateScanning)
        stateManual = findViewById(R.id.stateManual)
        stateConnecting = findViewById(R.id.stateConnecting)

        vBgBlobTop = findViewById(R.id.vBgBlobTop)
        vBgBlobBottom = findViewById(R.id.vBgBlobBottom)
        vScanLine = findViewById(R.id.vScanLine)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val prefs = getSharedPreferences("kaca", MODE_PRIVATE)
        etHost.setText(prefs.getString("host", ""))
        etPort.setText(prefs.getString("port", "27183"))
        etQuality.setText(prefs.getString("quality", "75"))

        updateFooterStatus()
        startBlobAnimation()

        findViewById<View>(R.id.btnScanAction).setOnClickListener {
            showState("scanning")
            startScanLineAnimation()
            scanHandler.postDelayed({
                try {
                    qrScanLauncher.launch(com.journeyapps.barcodescanner.ScanOptions().apply {
                        setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                        setPrompt("Scan QR code dari Mac")
                        setBeepEnabled(false)
                    })
                } catch (e: Exception) {
                    Log.e("Kaca", "Scan launch failed", e)
                    showState("initial")
                    Snackbar.make(findViewById(android.R.id.content), "Gagal buka kamera: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }, 600)
        }

        findViewById<View>(R.id.btnManualAction).setOnClickListener {
            showState("manual")
        }

        findViewById<View>(R.id.btnCancelScan).setOnClickListener {
            showState("initial")
        }

        findViewById<View>(R.id.btnBackManual).setOnClickListener {
            showState("initial")
        }

        findViewById<View>(R.id.btnCancelConnect).setOnClickListener {
            showState("initial")
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
            showState("connecting")
            tvConnectingDetail.text = "Menyiapkan stream terenkripsi ke Mac Anda ($host:$port)"
            screenCaptureLauncher.launch(intent)
        }
    }

    private fun showState(state: String) {
        stateInitial.visibility = if (state == "initial") View.VISIBLE else View.GONE
        stateScanning.visibility = if (state == "scanning") View.VISIBLE else View.GONE
        stateManual.visibility = if (state == "manual") View.VISIBLE else View.GONE
        stateConnecting.visibility = if (state == "connecting") View.VISIBLE else View.GONE
    }

    private fun startBlobAnimation() {
        vBgBlobTop.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .translationX(40f).translationY(30f)
            .setDuration(8000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                vBgBlobTop.animate()
                    .scaleX(1f).scaleY(1f)
                    .translationX(0f).translationY(0f)
                    .setDuration(8000)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { startBlobAnimation() }
                    .start()
            }
            .start()

        vBgBlobBottom.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .translationX(-30f).translationY(-40f)
            .setDuration(10000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                vBgBlobBottom.animate()
                    .scaleX(1f).scaleY(1f)
                    .translationX(0f).translationY(0f)
                    .setDuration(10000)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction { }
                    .start()
            }
            .start()
    }

    private fun startScanLineAnimation() {
        vScanLine.translationY = 0f
        vScanLine.animate()
            .translationY(240f)
            .setDuration(2000)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                vScanLine.translationY = 0f
                startScanLineAnimation()
            }
            .start()
    }

    private fun startMirror(host: String, port: Int, quality: Int) {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_START
            putExtra(MirrorService.EXTRA_HOST, host)
            putExtra(MirrorService.EXTRA_PORT, port)
            putExtra(MirrorService.EXTRA_QUALITY, quality)
        }
        startForegroundService(intent)

        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ checkServiceState(h) }, 300)
    }

    private fun checkServiceState(h: Handler) {
        updateFooterStatus()
        if (MirrorService.isRunning || MirrorService.lastError.isNotEmpty()) {
            showState("initial")
            return
        }
        h.postDelayed({ checkServiceState(h) }, 500)
    }

    private fun stopMirror() {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_STOP
        }
        startService(intent)
        updateFooterStatus()
    }

    private fun updateFooterStatus() {
        val running = MirrorService.isRunning
        val err = MirrorService.lastError
        tvFooterStatus.text = when {
            running -> "MENGIRIM KE ${MirrorService.currentTarget}"
            err.isNotEmpty() -> "ERROR: $err"
            else -> "SISTEM STANDBY"
        }
        val dotColor = when {
            err.isNotEmpty() -> R.color.status_error
            running -> R.color.status_active
            else -> R.color.status_idle
        }
        vFooterDot.setBackgroundTintList(ContextCompat.getColorStateList(this, dotColor))
    }

    override fun onResume() {
        super.onResume()
        updateFooterStatus()
    }

    override fun onPause() {
        super.onPause()
    }
}

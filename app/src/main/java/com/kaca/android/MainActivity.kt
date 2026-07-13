package com.kaca.android

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etQuality: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private lateinit var vStatusDot: ImageView

    private var projectionManager: MediaProjectionManager? = null

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MirrorService.pendingResultCode = result.resultCode
            MirrorService.pendingResultData = result.data
            MirrorService.lastError = ""
            startMirror()
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
            if (qrText.contains(":")) {
                val parts = qrText.split(":", limit = 2)
                etHost.setText(parts[0])
                etPort.setText(parts[1])
            } else {
                etHost.setText(qrText)
                etPort.setText("27183")
            }
            Snackbar.make(findViewById(android.R.id.content), "QR: $qrText", Snackbar.LENGTH_SHORT).show()
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
        btnStop = findViewById(R.id.btnStop)
        btnScan = findViewById(R.id.btnScan)
        tvStatus = findViewById(R.id.tvStatus)
        tvError = findViewById(R.id.tvError)
        vStatusDot = findViewById(R.id.vStatusDot)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val prefs = getSharedPreferences("kaca", MODE_PRIVATE)
        etHost.setText(prefs.getString("host", ""))
        etPort.setText(prefs.getString("port", "27183"))
        etQuality.setText(prefs.getString("quality", "75"))

        updateStatusUI()

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

            val intent = projectionManager?.createScreenCaptureIntent() ?: return@setOnClickListener
            screenCaptureLauncher.launch(intent)
        }

        btnStop.setOnClickListener {
            stopMirror()
        }

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
    }

    private fun startMirror() {
        val host = etHost.text.toString().trim()
        val port = etPort.text.toString().trim().toIntOrNull() ?: 27183
        val quality = etQuality.text.toString().trim().toIntOrNull() ?: 75

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
            running -> "Mengirim ke ${MirrorService.currentTarget}"
            err.isNotEmpty() -> "Terjadi error — lihat detail di bawah"
            else -> "Status: berhenti"
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
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
    }

    private fun setStatusDot(state: String) {
        val colorRes = when (state) {
            "active" -> R.color.status_active
            "error" -> R.color.status_error
            else -> R.color.status_idle
        }
        vStatusDot.setColorFilter(ContextCompat.getColor(this, colorRes))
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    companion object {
        private const val REQUEST_CODE_PROJECTION = 1001
    }
}

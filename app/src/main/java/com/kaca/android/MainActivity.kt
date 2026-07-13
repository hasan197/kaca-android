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
import android.widget.TextView
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etQuality: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView

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
            Toast.makeText(this, "Permission ditolak", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "QR: $qrText", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "QR scan gagal (null)", Toast.LENGTH_SHORT).show()
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

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Load saved settings
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
                Toast.makeText(this, "Isi host IP dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save settings
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
                Toast.makeText(this, "Gagal buka scanner: ${e.message}", Toast.LENGTH_LONG).show()
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
        startForegroundService(intent)

        // Poll service state — service start async, kita perlu update UI setelahnya
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
            running -> "Status: MENGIRIM ke ${MirrorService.currentTarget}"
            err.isNotEmpty() -> "Error: terjadi error — lihat kotak merah di bawah"
            else -> "Status: berhenti"
        }
        if (err.isNotEmpty()) {
            tvError.text = err
            tvError.visibility = View.VISIBLE
        } else {
            tvError.visibility = View.GONE
        }
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
    }

    override fun onResume() {
        super.onResume()
        updateStatusUI()
    }

    companion object {
        private const val REQUEST_CODE_PROJECTION = 1001
    }
}

package com.kaca.android

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.kaca.android.ui.AppState
import com.kaca.android.ui.MainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var projectionManager: MediaProjectionManager? = null
    private var pendingQrHost: String? = null
    private var pendingQrPort: String? = null

    private var appState = mutableStateOf(AppState.Initial)
    private var discoveredMac = mutableStateOf<DiscoveredMac?>(null)

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MirrorService.pendingResultCode = result.resultCode
            MirrorService.pendingResultData = result.data
            MirrorService.lastError = ""

            pendingQrHost?.let { host ->
                pendingQrPort?.let hostPort@{ port ->
                    pendingQrHost = null
                    pendingQrPort = null
                    appState.value = AppState.Connecting
                    startMirroring(host, port.toIntOrNull() ?: 27183, 75)
                    return@hostPort
                }
            }
        } else {
            appState.value = AppState.Initial
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

            Snackbar.make(findViewById(android.R.id.content), "QR: $host:$port", Snackbar.LENGTH_SHORT).show()

            val intent = projectionManager?.createScreenCaptureIntent()
            if (intent != null) {
                screenCaptureLauncher.launch(intent)
            }
        } else {
            appState.value = AppState.Initial
            Snackbar.make(findViewById(android.R.id.content), "QR tidak terbaca", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        appState.value = if (MirrorService.isConnected(this)) AppState.Connected else AppState.Initial

        // Auto-discover Mac via UDP multicast
        lifecycleScope.launch {
            MulticastDiscoveryManager.discoveries(this).collect { mac ->
                discoveredMac.value = mac
            }
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                MainScreen(
                    state = appState.value,
                    onStateChange = { appState.value = it },
                    discoveredMac = discoveredMac.value,
                    onScanQr = {
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
                    },
                    onManualConnect = { host, port, quality ->
                        if (host.isEmpty()) return@MainScreen
                        getSharedPreferences("kaca", MODE_PRIVATE).edit()
                            .putString("host", host)
                            .putString("port", port.toString())
                            .putString("quality", quality.toString())
                            .apply()

                        pendingQrHost = host
                        pendingQrPort = port.toString()
                        val intent = projectionManager?.createScreenCaptureIntent() ?: return@MainScreen
                        screenCaptureLauncher.launch(intent)
                    },
                    onStopMirror = {
                        stopMirror()
                    },
                    onCancel = {},
                    onRecentDevice = {},
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
                KacaLogDialog()
            }
        }
    }

    private fun startMirroring(host: String, port: Int, quality: Int) {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_START
            putExtra(MirrorService.EXTRA_HOST, host)
            putExtra(MirrorService.EXTRA_PORT, port)
            putExtra(MirrorService.EXTRA_QUALITY, quality)
        }
        startForegroundService(intent)

        val h = Handler(Looper.getMainLooper())
        h.postDelayed({ checkServiceState(h, 0) }, 300)
    }

    private fun checkServiceState(h: Handler, retries: Int) {
        if (MirrorService.lastError.isNotEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), "Error: ${MirrorService.lastError}", Snackbar.LENGTH_LONG).show()
            appState.value = AppState.Initial
            return
        }
        if (MirrorService.sessionActive) {
            appState.value = AppState.Connected
            return
        }
        if (retries > 60) {
            appState.value = AppState.Initial
            return
        }
        h.postDelayed({ checkServiceState(h, retries + 1) }, 500)
    }

    private fun stopMirror() {
        val intent = Intent(this, MirrorService::class.java).apply {
            action = MirrorService.ACTION_STOP
        }
        startService(intent)
        appState.value = AppState.Initial
    }
}
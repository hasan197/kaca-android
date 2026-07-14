package com.kaca.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class MirrorService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private var socket: Socket? = null
    private var socketOut: DataOutputStream? = null

    @Volatile
    private var running = false

    /// Apakah socket saat ini hidup & bisa dipakai untuk write?
    /// Di-set false saat write error atau remote menutup koneksi.
    /// Main loop memeriksa flag ini untuk trigger reconnect.
    @Volatile
    private var socketAlive = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST)
                if (host == null || host.isEmpty()) return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 27183)
                val quality = intent.getIntExtra(EXTRA_QUALITY, 75)

                currentTarget = "$host:$port"
                isRunning = true
                startForegroundCompat()

                thread {
                    try {
                        startStreaming(host, port, quality)
                    } catch (e: Throwable) {
                        setError("streaming error: ${e::class.java.simpleName}: ${e.message}")
                        stopSelfSafely()
                    }
                }
            }
            ACTION_STOP -> {
                stopSelfSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun startStreaming(
        host: String,
        port: Int,
        quality: Int,
    ) {
        // 1. Setup MediaProjection (one-time — tidak perlu re-request saat reconnect)
        val resultCode = pendingResultCode
        val data = pendingResultData
        if (data == null) {
            setError("pendingResultData is null")
            return
        }
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        mediaProjection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (e: Throwable) {
            setError("getMediaProjection failed: ${e::class.java.simpleName}: ${e.message}")
            stopSelfSafely()
            return
        }
        if (mediaProjection == null) {
            setError("getMediaProjection returned null")
            stopSelfSafely()
            return
        }

        // Android 15 requires a callback registered on MediaProjection before creating VirtualDisplay
        mediaProjection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped by system")
                stopSelfSafely()
            }
        }, null)

        // 2. Determine screen size (max 720p untuk jaga bandwidth)
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        // Scale down jika terlalu besar
        val maxDim = 1280
        var targetW = screenW
        var targetH = screenH
        if (maxOf(screenW, screenH) > maxDim) {
            val scale = maxDim.toFloat() / maxOf(screenW, screenH)
            targetW = (screenW * scale).toInt().coerceAtLeast(1)
            targetH = (screenH * scale).toInt().coerceAtLeast(1)
        }

        // imageReader butuh ukuran genap
        targetW = targetW and 0x7FFFFFFE
        targetH = targetH and 0x7FFFFFFE

        Log.i(TAG, "virtual display size: ${targetW}x${targetH}")

        // 3. ImageReader (one-time — tetap capture frame bahkan saat reconnect)
        workerThread = HandlerThread("ImageReader").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)

        imageReader = ImageReader.newInstance(targetW, targetH, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            val image = try {
                reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            } catch (e: Exception) {
                Log.w(TAG, "acquireLatestImage failed: ${e.message}")
                return@setOnImageAvailableListener
            }
            try {
                processFrame(image, targetW, targetH, quality)
            } catch (e: Exception) {
                Log.w(TAG, "processFrame error: ${e.message}")
            } finally {
                image.close()
            }
        }, workerHandler)

        // 4. VirtualDisplay (one-time)
        val density = metrics.densityDpi
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "Kaca",
            targetW, targetH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        running = true

        // 5. Connection loop dengan auto-reconnect
        var retryCount = 0
        val maxRetries = 3

        while (running && !Thread.currentThread().isInterrupted) {
            try {
                connectAndStream(host, port)
                // connectAndStream returned normally = remote closed cleanly
                // Reset retry count kalau sebelumnya sukses stream lama
                if (socketAlive) {
                    retryCount = 0
                }
            } catch (e: Exception) {
                Log.w(TAG, "stream interrupted: ${e::class.java.simpleName}: ${e.message}")

                // Pastikan socket lama di-cleanup
                socketAlive = false
                socketOut = null
                try { socket?.close() } catch (_: Exception) {}
                socket = null

                if (!running) break

                retryCount++
                if (retryCount > maxRetries) {
                    setError("Connection lost after $maxRetries retries: ${e.message}")
                    break
                }

                // Backoff: 1s, 2s, 4s
                val backoffMs = 1000L * (1 shl (retryCount - 1))
                Log.i(TAG, "reconnecting in ${backoffMs}ms (attempt $retryCount/$maxRetries)")
                updateNotification("Reconnecting... (attempt $retryCount/$maxRetries)")
                try {
                    Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }

        stopSelfSafely()
    }

    @Throws(IOException::class)
    private fun connectAndStream(host: String, port: Int) {
        Log.i(TAG, "connecting to $host:$port")
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 10_000)
        s.tcpNoDelay = true
        s.soTimeout = 0
        socket = s
        val out = DataOutputStream(s.getOutputStream().buffered())

        synchronized(this) {
            socketOut = out
            socketAlive = true
        }

        saveRecentConnection(this, host, port)
        updateNotification("Mengirim layar ke $currentTarget")

        // Handshake: kirim Hello("KACA"), terima Hello("OK")
        writeMessage(out, MSG_HELLO, "KACA".toByteArray())
        val (helloType, helloPayload) = readMessage(s)
        if (helloType != MSG_HELLO || String(helloPayload) != "OK") {
            throw IOException("handshake failed: unexpected response")
        }
        sessionActive = true
        Log.i(TAG, "streaming started")

        // Message loop: baca pesan masuk dari server
        val input = DataInputStream(s.getInputStream())
        val headerBuf = ByteArray(PROTOCOL_HEADER_LEN)
        while (running && socketAlive && !Thread.currentThread().isInterrupted) {
            try {
                input.readFully(headerBuf)
                val msgType = headerBuf[0].toInt() and 0xFF
                val payloadLen = readIntBE(headerBuf, 1)
                if (payloadLen > MAX_PAYLOAD) {
                    throw IOException("payload too large: $payloadLen")
                }
                val payload = ByteArray(payloadLen)
                if (payloadLen > 0) input.readFully(payload)

                handleServerMessage(msgType, payload, out)
            } catch (e: java.io.EOFException) {
                throw IOException("socket closed by remote")
            }
        }

        synchronized(this) {
            socketOut = null
            socketAlive = false
        }
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun handleServerMessage(msgType: Int, payload: ByteArray, out: DataOutputStream) {
        when (msgType) {
            MSG_PING -> {
                try {
                    writeMessage(out, MSG_PING, "PONG".toByteArray())
                } catch (_: IOException) {}
            }
            MSG_CLIPBOARD_TEXT -> {
                val text = String(payload, Charsets.UTF_8)
                Log.i(TAG, "clipboard from mac: ${text.take(100)}")
                // TODO: Phase 1.1 — set clipboard
            }
            MSG_FIND_PHONE -> {
                Log.i(TAG, "find phone requested")
                // TODO: Phase 3.4 — play alarm sound
            }
            else -> {
                Log.w(TAG, "unhandled server message: type=$msgType len=${payload.size}")
            }
        }
    }

    /// Encode message: [type(1) | len(4) | payload(N)]
    @Throws(IOException::class)
    private fun writeMessage(out: DataOutputStream, type: Int, payload: ByteArray) {
        synchronized(out) {
                    out.writeByte(type)
                    out.writeInt(payload.size)
            out.write(payload)
            out.flush()
        }
    }

    /// Baca satu message dari socket (blocking, untuk handshake).
    /// Return (type, payload) atau throw IOException.
    @Throws(IOException::class)
    private fun readMessage(s: Socket): Pair<Int, ByteArray> {
        val input = DataInputStream(s.getInputStream())
        val header = ByteArray(PROTOCOL_HEADER_LEN)
        input.readFully(header)
        val msgType = header[0].toInt() and 0xFF
        val payloadLen = readIntBE(header, 1)
        if (payloadLen > MAX_PAYLOAD) {
            throw IOException("payload too large: $payloadLen")
        }
        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) input.readFully(payload)
        return Pair(msgType, payload)
    }

    /// Read big-endian u32 dari byte array mulai dari offset.
    private fun readIntBE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
               ((buf[offset + 1].toInt() and 0xFF) shl 16) or
               ((buf[offset + 2].toInt() and 0xFF) shl 8) or
               (buf[offset + 3].toInt() and 0xFF)
    }

    private fun processFrame(image: Image, width: Int, height: Int, quality: Int) {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        // Convert RGBA → ARGB Bitmap
        val bmp = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buffer)

        // Crop padding
        val cropped = if (rowPadding > 0) {
            Bitmap.createBitmap(bmp, 0, 0, width, height)
        } else {
            bmp
        }

        // Compress ke JPEG
        val baos = ByteArrayOutputStream(64 * 1024)
        cropped.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val jpegBytes = baos.toByteArray()

        if (!socketAlive) return
        val out = socketOut ?: return
        // Encode frame payload: width(4) | height(4) | jpeg_len(4) | jpeg
        val framePayload = ByteArrayOutputStream(12 + jpegBytes.size)
        DataOutputStream(framePayload).apply {
            writeInt(width)
            writeInt(height)
            writeInt(jpegBytes.size)
            write(jpegBytes)
        }
        val frameBytes = framePayload.toByteArray()
        try {
            writeMessage(out, MSG_FRAME, frameBytes)
        } catch (e: Exception) {
            Log.w(TAG, "socket write failed: ${e.message}")
            socketAlive = false
        }

        // Cleanup
        if (cropped !== bmp) cropped.recycle()
        bmp.recycle()
    }

    private fun stopSelfSafely() {
        running = false
        socketAlive = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        try { workerThread?.quitSafely() } catch (_: Exception) {}
        workerThread = null
        workerHandler = null
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        socketOut = null
        isRunning = false
        sessionActive = false
        currentTarget = ""
        clearConnectedState(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /// Update teks notifikasi foreground service.
    /// Dipakai saat status berubah: "Mengirim layar..." → "Reconnecting..." → "Mengirim layar..."
    private fun updateNotification(text: String) {
        try {
            val channelId = "kaca_channel"
            val notif: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Kaca aktif")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelfSafely()
    }

    private fun startForegroundCompat() {
        val channelId = "kaca_channel"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Kaca Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Kaca aktif")
            .setContentText("Mengirim layar ke $currentTarget")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    companion object {
        const val ACTION_START = "com.kaca.START"
        const val ACTION_STOP = "com.kaca.STOP"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
        const val EXTRA_QUALITY = "quality"

        const val NOTIF_ID = 9001

        private const val TAG = "Kaca"

        // Protocol message types (cocok dengan kaca-macos/src/protocol.rs)
        const val MSG_HELLO = 0x01
        const val MSG_FRAME = 0x02
        const val MSG_CLIPBOARD_TEXT = 0x03
        const val MSG_CLIPBOARD_IMAGE = 0x04
        const val MSG_NOTIFICATION = 0x05
        const val MSG_FILE_META = 0x06
        const val MSG_FILE_CHUNK = 0x07
        const val MSG_SMS = 0x08
        const val MSG_CALL = 0x09
        const val MSG_KEYBOARD = 0x0A
        const val MSG_TOUCH = 0x0B
        const val MSG_BATTERY = 0x0C
        const val MSG_PING = 0x0D
        const val MSG_FIND_PHONE = 0x0E

        const val PROTOCOL_HEADER_LEN = 5 // type(1) + payload_len(4)
        const val MAX_PAYLOAD = 32 * 1024 * 1024

        @Volatile
        @JvmStatic
        var isRunning: Boolean = false
            private set

        @Volatile
        @JvmStatic
        var sessionActive: Boolean = false

        @Volatile
        @JvmStatic
        var currentTarget: String = ""
            private set

        // MediaProjection result — diset oleh MainActivity sebelum start service
        @Volatile
        @JvmStatic
        var pendingResultCode: Int = 0

        @Volatile
        @JvmStatic
        var pendingResultData: Intent? = null

        @Volatile
        @JvmStatic
        var lastError: String = ""

        private const val PREFS = "kaca"
        private const val PREFS_RECENT = "recent"
        private const val PREFS_CONNECTED = "connected"
        private const val PREFS_CONNECTED_HOST = "connected_host"

        @JvmStatic
        fun isConnected(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREFS_CONNECTED, false)

        @JvmStatic
        fun getConnectedHost(ctx: Context): String =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFS_CONNECTED_HOST, "") ?: ""

        @JvmStatic
        fun saveRecentConnection(ctx: Context, ip: String, port: Int) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PREFS_CONNECTED, true)
                .putString(PREFS_CONNECTED_HOST, "$ip:$port")
                .apply()

            val raw = prefs.getStringSet(PREFS_RECENT, emptySet()) ?: emptySet()
            val entries = raw.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 3) {
                    Triple(parts[0], parts[1].toIntOrNull() ?: 27183, parts[2].toLongOrNull() ?: 0L)
                } else null
            }.toMutableList()
            // Hapus entry dengan IP yang sama, lalu tambah yg baru
            entries.removeAll { it.first == ip }
            entries.add(Triple(ip, port, System.currentTimeMillis()))
            // Sort by timestamp descending, keep 10
            val trimmed = entries.sortedByDescending { it.third }.take(10)
                .map { "${it.first},${it.second},${it.third}" }.toSet()
            prefs.edit().putStringSet(PREFS_RECENT, trimmed).apply()
        }

        @JvmStatic
        fun loadRecentConnections(ctx: Context): List<Triple<String, Int, Long>> {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getStringSet(PREFS_RECENT, emptySet()) ?: emptySet()
            return raw.mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size >= 3) {
                    Triple(parts[0], parts[1].toIntOrNull() ?: 27183, parts[2].toLongOrNull() ?: 0L)
                } else null
            }.sortedByDescending { it.third }
        }

        @JvmStatic
        fun clearConnectedState(ctx: Context) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(PREFS_CONNECTED, false)
                .putString(PREFS_CONNECTED_HOST, "")
                .apply()
        }
    }

    private fun setError(msg: String) {
        Log.e(TAG, msg)
        lastError = msg
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notif = NotificationCompat.Builder(this, "kaca_channel")
                .setContentTitle("Kaca error")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setOngoing(false)
                .build()
            nm.notify(NOTIF_ID + 1, notif)
        } catch (_: Exception) {}
    }
}

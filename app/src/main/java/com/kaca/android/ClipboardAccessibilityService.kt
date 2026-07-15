package com.kaca.android

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import kotlin.concurrent.thread

/**
 * Accessibility service to read clipboard from background on Android 12+.
 * User must enable this in Settings > Accessibility > Kaca Clipboard.
 */
class ClipboardAccessibilityService : AccessibilityService() {

    companion object {
        /** Set by accessibility service poll thread, read by MirrorService clipboard poll. */
        @Volatile
        var latestText: String? = null

        /** Whether the accessibility service was ever connected this session. */
        @Volatile
        var isConnected = false
    }

    private var clipboardManager: ClipboardManager? = null
    @Volatile private var running = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        isConnected = true
        running = true

        kacaLog("AccessibilityService connected, SDK=${Build.VERSION.SDK_INT}")

        // Initial read + polling — accessibility service can read clipboard in background
        var last = readClipboard()
        latestText = last
        kacaLog("A11y initial clipboard: ${last?.take(60)}")

        thread(name = "a11y-clipboard") {
            while (running) {
                try {
                    Thread.sleep(2000)
                } catch (_: InterruptedException) { break }
                if (!running) break
                val text = readClipboard()
                if (text != null && text != last) {
                    kacaLog("A11y clipboard changed: ${text.take(60)}")
                    latestText = text
                    last = text
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        running = false
        isConnected = false
        kacaLog("AccessibilityService disconnected")
        return super.onUnbind(intent)
    }

    private fun readClipboard(): String? {
        val cm = clipboardManager ?: return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(this)?.toString()
        return text?.takeIf { it.isNotBlank() }
    }
}

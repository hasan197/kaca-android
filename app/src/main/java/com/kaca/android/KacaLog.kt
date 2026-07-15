package com.kaca.android

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object KacaLog {
    private val buffer = ConcurrentLinkedDeque<String>()
    private val MAX = 200
    private val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    val visible = mutableStateOf(false)

    fun log(msg: String) {
        val line = "${ts.format(Date())} $msg"
        buffer.addLast(line)
        while (buffer.size > MAX) buffer.pollFirst()
    }

    fun snapshot(): String = buffer.joinToString("\n")

    fun clear() = buffer.clear()
}

fun kacaLog(msg: String) = KacaLog.log(msg)

@Composable
fun ShakeDetector(onShake: () -> Unit) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShake = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val g = x * x + y * y + z * z
                if (g > 25f * 25f) {
                    val now = System.currentTimeMillis()
                    if (now - lastShake > 800) {
                        lastShake = now
                        onShake()
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
}

@Composable
fun KacaLogDialog() {
    ShakeDetector { KacaLog.visible.value = !KacaLog.visible.value }
    if (KacaLog.visible.value) {
        AlertDialog(
            onDismissRequest = { KacaLog.visible.value = false },
            title = { Text("Kaca Log") },
            text = {
                val snap = remember { mutableStateOf("") }
                LaunchedEffect(Unit) { snap.value = KacaLog.snapshot() }
                Text(
                    snap.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF00E676)
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { KacaLog.clear() }) { Text("Clear") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { KacaLog.visible.value = false }) { Text("Close") }
                }
            }
        )
    }
}
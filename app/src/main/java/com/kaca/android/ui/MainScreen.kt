package com.kaca.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.delay

val BrandBlue = Color(0xFF007AFF)
val BrandBlueSoft = Color(0xFFEBF5FF)
val BrandBg = Color(0xFFF2F2F7)
val Neutral950 = Color(0xFF0D0D0F)
val Neutral900 = Color(0xFF1C1C1E)
val Neutral800 = Color(0xFF2C2C2E)
val Neutral700 = Color(0xFF48484A)
val Neutral600 = Color(0xFF636366)
val Neutral500 = Color(0xFF8E8E93)
val Neutral400 = Color(0xFFAEAEB2)
val Neutral300 = Color(0xFFC7C7CC)
val Neutral200 = Color(0xFFD1D1D6)
val Neutral100 = Color(0xFFE5E5EA)
val Neutral50 = Color(0xFFF2F2F7)
val White = Color(0xFFFFFFFF)
val White80 = Color(0xCCFFFFFF)
val White90 = Color(0xE6FFFFFF)
val StatusActive = Color(0xFF22C55E)
val StatusActiveBg = Color(0x0D22C55E)
val StatusError = Color(0xFFEF4444)
val BrandRose = Color(0xFFF43F5E)
val BrandRoseSoft = Color(0xFFFFF1F2)
val Emerald600 = Color(0xFF059669)
val Emerald400 = Color(0xFF4ADE80)
val Emerald200 = Color(0xFFA7F3D0)
val Emerald50 = Color(0xFFECFDF5)

data class RecentDevice(val name: String, val ip: String, val label: String)

enum class AppState { Initial, Scanning, Manual, Connecting, Connected }

@Composable
fun MainScreen(
    state: AppState,
    onStateChange: (AppState) -> Unit,
    onScanQr: () -> Unit,
    onManualConnect: (host: String, port: Int, quality: Int) -> Unit,
    onStopMirror: () -> Unit,
    onCancel: () -> Unit,
    onRecentDevice: (String) -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var hostIp by remember { mutableStateOf(
        com.kaca.android.MirrorService.getConnectedHost(ctx).substringBefore(":").ifEmpty { "192.168.1.15" }
    ) }
    var port by remember { mutableStateOf("27183") }
    var quality by remember { mutableStateOf("75") }
    var duration by remember { mutableIntStateOf(0) }
    var fps by remember { mutableIntStateOf(60) }
    var bitrate by remember { mutableStateOf(4.2f) }

    val recentDevices = remember {
        com.kaca.android.MirrorService.loadRecentConnections(ctx).map { (ip, port, ts) ->
            val label = when {
                System.currentTimeMillis() - ts < 60_000 -> "Baru saja"
                System.currentTimeMillis() - ts < 3_600_000 -> "${(System.currentTimeMillis() - ts) / 60_000} mnt lalu"
                else -> "${(System.currentTimeMillis() - ts) / 3_600_000} jam lalu"
            }
            RecentDevice(ip, ip, label)
        }
    }

    LaunchedEffect(state) {
        if (state == AppState.Connected) {
            duration = 0
            while (true) {
                delay(1000)
                duration++
                fps = (56..60).random()
                bitrate = (38..50).random() / 10f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandBg)
    ) {
        BlobBackground()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Header(
                state = state,
                onBack = {
                    onStateChange(AppState.Initial)
                    onCancel()
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (slideInVertically { it / 4 } + fadeIn())
                            .togetherWith(slideOutVertically { -it / 4 } + fadeOut())
                    },
                    label = "state"
                ) { currentState ->
                    when (currentState) {
                        AppState.Initial -> InitialState(
                            onScan = {
                                onStateChange(AppState.Scanning)
                                onScanQr()
                            },
                            onManual = { onStateChange(AppState.Manual) },
                            onRecentDevice = { ip ->
                                hostIp = ip
                                onStateChange(AppState.Manual)
                                onRecentDevice(ip)
                            },
                            recentDevices = recentDevices
                        )
                        AppState.Scanning -> ScanningState(
                            onCancel = { onStateChange(AppState.Initial) }
                        )
                        AppState.Manual -> ManualState(
                            hostIp = hostIp,
                            port = port,
                            quality = quality,
                            onHostChange = { hostIp = it },
                            onPortChange = { port = it },
                            onQualityChange = { quality = it },
                            onConnect = {
                                onManualConnect(hostIp, port.toIntOrNull() ?: 27183, quality.toIntOrNull() ?: 75)
                                onStateChange(AppState.Connecting)
                            },
                            onBack = { onStateChange(AppState.Initial) }
                        )
                        AppState.Connecting -> ConnectingState(
                            hostIp = hostIp,
                            port = port,
                            onCancel = {
                                onStateChange(AppState.Initial)
                                onCancel()
                            }
                        )
                        AppState.Connected -> ConnectedState(
                            hostIp = hostIp,
                            duration = duration,
                            fps = fps,
                            bitrate = bitrate,
                            quality = quality,
                            onStop = {
                                onStopMirror()
                                onStateChange(AppState.Initial)
                            }
                        )
                    }
                }
            }

            Footer(state = state, quality = quality)
        }
    }
}

@Composable
private fun BlobBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val topX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "topX"
    )
    val topY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "topY"
    )
    val topS by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse),
        label = "topS"
    )
    val botX by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -30f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "botX"
    )
    val botY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -40f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "botY"
    )
    val botS by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "botS"
    )

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .size(200.dp)
                .offset(topX.dp, topY.dp)
                .scale(topS)
                .graphicsLayer(alpha = 0.5f)
                .background(
                    Brush.radialGradient(listOf(BrandBlue.copy(alpha = 0.12f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(260.dp)
                .offset(botX.dp, botY.dp)
                .scale(botS)
                .graphicsLayer(alpha = 0.4f)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF6366F1).copy(alpha = 0.1f), Color.Transparent)),
                    CircleShape
                )
        )
    }
}

@Composable
private fun Header(state: AppState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp)) {
            if (state != AppState.Initial && state != AppState.Connected) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(White90)
                ) {
                    Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neutral800)
                }
            }
        }

        Text(
            text = when (state) {
                AppState.Initial -> "Kaca Mirror"
                AppState.Scanning -> "PINDAI QR CODE"
                AppState.Manual -> "KONFIGURASI MANUAL"
                AppState.Connecting -> "MENGHUBUNGKAN"
                AppState.Connected -> "SEDANG MIRRORING"
            },
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = if (state == AppState.Initial) 15.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral900
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(White90)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state == AppState.Connected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(StatusActive, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Neutral300, CircleShape)
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = WifiIcon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Neutral400
            )
        }
    }
}

@Composable
private fun InitialState(
    onScan: () -> Unit,
    onManual: () -> Unit,
    onRecentDevice: (String) -> Unit,
    recentDevices: List<RecentDevice>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        ConnectionVisual()

        Text(
            "Koneksi Layar Instan",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral900,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Pilih metode koneksi di bawah untuk mulai\nmembagikan layar Android Anda ke Mac.",
            fontSize = 12.sp,
            color = Neutral400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
        ) {
            Text("PINDAI QR CODE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onManual,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = White,
                contentColor = Neutral800
            )
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(Neutral600, CircleShape)
                    .padding(4.dp)
            ) {
                Box(Modifier.fillMaxSize().background(White, CircleShape))
            }
            Spacer(Modifier.width(8.dp))
            Text("ISI MANUAL IP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Neutral200.copy(alpha = 0.5f))
        )

        Text(
            "Koneksi Terakhir",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 8.dp)
        )

        recentDevices.forEach { device ->
            RecentDeviceItem(device, onClick = { onRecentDevice(device.ip) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectionVisual() {
    val infiniteTransition = rememberInfiniteTransition(label = "conn")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ring"
    )
    val phoneY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phone"
    )
    val laptopY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "laptop"
    )

    Box(
        modifier = Modifier.size(192.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(192.dp)
                .scale(ringScale)
                .graphicsLayer(alpha = 0.3f)
                .background(BrandBlue.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
        )
        Box(
            Modifier
                .size(156.dp)
                .scale(ringScale * 0.9f)
                .graphicsLayer(alpha = 0.2f)
                .background(BrandBlue.copy(alpha = 0.06f), RoundedCornerShape(100.dp))
        )
        Box(
            Modifier
                .size(120.dp)
                .background(BrandBlue.copy(alpha = 0.04f), RoundedCornerShape(100.dp))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer(translationY = phoneY)
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .background(White, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PhoneIcon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Neutral800
                )
            }

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(BrandBlue, Color(0xFF6366F1)))
                    )
            )

            Spacer(Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer(translationY = laptopY)
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .background(White, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = LaptopIcon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Neutral800
                )
            }
        }
    }
}

@Composable
private fun RecentDeviceItem(device: RecentDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(Neutral50, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = LaptopIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Neutral400
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(device.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Neutral800)
            Text(device.ip, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Neutral400)
        }

        Text(
            device.label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400,
            modifier = Modifier
                .background(Neutral100, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ScanningState(onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scanY by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 200f,
        animationSpec = infiniteRepeatable(tween(2500, easing = LinearEasing), RepeatMode.Reverse),
        label = "scanLine"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Neutral900),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CameraIcon,
                contentDescription = null,
                modifier = Modifier.size(60.dp).graphicsLayer(alpha = 0.12f),
                tint = White
            )

            Canvas(Modifier.fillMaxSize()) {
                val s = 16.dp.toPx()
                val m = 12.dp.toPx()
                drawLine(BrandBlue, Offset(m, m), Offset(m + s, m), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(m, m), Offset(m, m + s), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(size.width - m, m), Offset(size.width - m - s, m), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(size.width - m, m), Offset(size.width - m, m + s), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(m, size.height - m), Offset(m + s, size.height - m), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(m, size.height - m), Offset(m, size.height - m - s), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(size.width - m, size.height - m), Offset(size.width - m - s, size.height - m), strokeWidth = 2f)
                drawLine(BrandBlue, Offset(size.width - m, size.height - m), Offset(size.width - m, size.height - m - s), strokeWidth = 2f)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .graphicsLayer { translationY = scanY }
                    .background(BrandBlue)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Menunggu QR Code...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral900
        )

        Text(
            "Arahkan kamera ke QR Code yang ditampilkan\npada aplikasi Kaca di komputer Mac Anda.",
            fontSize = 12.sp,
            color = Neutral400,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier
                .padding(top = 6.dp)
                .width(240.dp)
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "BATAL",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral600,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(White80)
                .clickable(onClick = onCancel)
                .padding(horizontal = 32.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun ManualState(
    hostIp: String,
    port: String,
    quality: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onQualityChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(White80)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(BrandBlueSoft, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(BrandBlue, RoundedCornerShape(4.dp))
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Isi Host Manual", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Neutral900)
                    Text("Masukkan IP Host dari aplikasi Mac Anda", fontSize = 9.sp, color = Neutral400)
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neutral100))
            Spacer(Modifier.height(20.dp))

            Text(
                "HOST IP MAC",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral400,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            OutlinedTextField(
                value = hostIp,
                onValueChange = onHostChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Contoh: 192.168.1.15", fontSize = 12.sp, color = Neutral300) },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Neutral800
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BrandBlue.copy(alpha = 0.3f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Neutral50,
                    unfocusedContainerColor = Neutral50
                )
            )

            Spacer(Modifier.height(16.dp))

            Row {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "PORT",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Neutral400,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = onPortChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Neutral800
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Neutral50,
                            unfocusedContainerColor = Neutral50
                        )
                    )
                }

                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        "KUALITAS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Neutral400,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = quality,
                        onValueChange = onQualityChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Neutral800
                        ),
                        singleLine = true,
                        suffix = { Text("%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Neutral400) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Neutral50,
                            unfocusedContainerColor = Neutral50
                        )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
            ) {
                Text("MULAI MIRROR SEKARANG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "KEMBALI KE BERANDA",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral600,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(vertical = 16.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ConnectingState(
    hostIp: String,
    port: String,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = BrandBlue,
                strokeWidth = 4.dp
            )
            Icon(
                imageVector = PhoneIcon,
                contentDescription = null,
                modifier = Modifier.size(32.dp).graphicsLayer(alpha = 0.8f),
                tint = BrandBlue
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Mengamankan Koneksi...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral900
        )

        Text(
            "Menghubungkan ke Host IP $hostIp:$port",
            fontSize = 12.sp,
            color = Neutral400,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.height(40.dp))

        Text(
            "BATALKAN KONEKSI",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = BrandRose,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(BrandRoseSoft)
                .clickable(onClick = onCancel)
                .padding(horizontal = 32.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun ConnectedState(
    hostIp: String,
    duration: Int,
    fps: Int,
    bitrate: Float,
    quality: String,
    onStop: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ping")
    val pingScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ping"
    )
    val pingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "pingAlpha"
    )

    val pingScale2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.8f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "ping2"
    )
    val pingAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "pingAlpha2"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        // Active Indicator Wave
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .scale(pingScale)
                    .graphicsLayer(alpha = pingAlpha)
                    .background(Emerald400.copy(alpha = 0.2f), CircleShape)
            )
            Box(
                Modifier
                    .size(88.dp)
                    .scale(pingScale2)
                    .graphicsLayer(alpha = pingAlpha2)
                    .background(Emerald400.copy(alpha = 0.1f), CircleShape)
            )
            Box(
                Modifier
                    .size(80.dp)
                    .background(Emerald50, CircleShape)
                    .border(2.dp, Emerald400.copy(alpha = 0.3f), CircleShape)
                    .shadow(8.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MonitorIcon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = StatusActive
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Status & Title
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Sesi Aktif",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Emerald600,
                modifier = Modifier
                    .background(Emerald50, RoundedCornerShape(100.dp))
                    .border(1.dp, Emerald200, RoundedCornerShape(100.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            )
            Text(
                "Layar Sedang Dimirror",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral950,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                "Host: $hostIp",
                fontSize = 12.sp,
                color = Neutral400,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Stats Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(White)
                .border(1.dp, Neutral200.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            StatItem(
                icon = { Icon(ClockIcon, null, Modifier.size(16.dp), tint = Neutral400) },
                iconMargin = 6.dp,
                label = "DURASI",
                value = String.format("%02d:%02d", duration / 60, duration % 60),
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.width(1.dp).height(48.dp).background(Neutral100))
            StatItem(
                icon = { Icon(GaugeIcon, null, Modifier.size(16.dp), tint = Neutral400) },
                iconMargin = 6.dp,
                label = "FPS",
                value = "${fps} Hz",
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.width(1.dp).height(48.dp).background(Neutral100))
            StatItem(
                icon = { Icon(FlashIcon, null, Modifier.size(16.dp), tint = Neutral400) },
                iconMargin = 6.dp,
                label = "BITRATE",
                value = "%.1f Mb/s".format(bitrate),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.weight(1f))

        // Stop Button
        Button(
            onClick = onStop,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandRose)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
            Spacer(Modifier.width(8.dp))
            Text("STOP MIRRORING", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatItem(
    icon: @Composable () -> Unit,
    iconMargin: Dp = 6.dp,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400,
            modifier = Modifier.padding(top = iconMargin),
            letterSpacing = 0.5.sp
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Neutral800,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun StatItem(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = Neutral800,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Footer(state: AppState, quality: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(White80)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (state == AppState.Connected) StatusActive else Neutral400,
                        CircleShape
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state == AppState.Connected) "Mirroring Aktif" else "Sistem Standby",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Neutral500
            )
        }
        Text(
            if (state == AppState.Connected) "${quality}% Kualitas" else "Enkripsi P2P",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral400
        )
    }
}

@Composable
private fun CircularProgressIndicator(
    modifier: Modifier,
    color: Color,
    strokeWidth: androidx.compose.ui.unit.Dp
) {
    androidx.compose.material3.CircularProgressIndicator(
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth
    )
}


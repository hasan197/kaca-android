package com.kaca.android

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

data class DiscoveredMac(
    val hostname: String,
    val ip: String,
    val port: Int,
)

/// Listener multicast UDP untuk deteksi otomatis Mac di jaringan WiFi.
/// Subscribe via `discoveries` flow. Cancel scope untuk stop.
object MulticastDiscoveryManager {

    private const val MULTICAST_GROUP = "239.255.255.250"
    private const val MULTICAST_PORT = 27184
    private const val TAG = "KacaDiscovery"

    fun discoveries(scope: CoroutineScope): Flow<DiscoveredMac> = callbackFlow {
        scope.launch(Dispatchers.IO) {
            try {
                val group = InetAddress.getByName(MULTICAST_GROUP)
                val sock = MulticastSocket(MULTICAST_PORT)
                sock.reuseAddress = true
                sock.joinGroup(group)

                Log.i(TAG, "listening on $MULTICAST_GROUP:$MULTICAST_PORT")
                val buf = ByteArray(1024)
                while (isActive) {
                    val pkt = DatagramPacket(buf, buf.size)
                    sock.receive(pkt)
                    val json = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                    val mac = parsePayload(json)
                    if (mac != null) {
                        trySend(mac)
                    }
                }
                sock.leaveGroup(group)
                sock.close()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.w(TAG, "discovery error: ${e.message}")
                }
            }
        }
        awaitClose { Log.i(TAG, "discovery stopped") }
    }

    private fun parsePayload(json: String): DiscoveredMac? {
        return try {
            val obj = JSONObject(json)
            val hostname = obj.optString("hostname", "")
            val ip = obj.optString("ip", "")
            val port = obj.optInt("port", 27183)
            if (ip.isBlank()) null else DiscoveredMac(hostname, ip, port)
        } catch (_: Exception) {
            null
        }
    }
}

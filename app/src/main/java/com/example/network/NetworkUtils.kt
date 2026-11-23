package com.example.network

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.example.model.QrConnectionPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URLEncoder
import java.security.SecureRandom

object NetworkUtils {

    private val random = SecureRandom()

    fun generateSessionToken(): String {
        val bytes = ByteArray(10)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    fun getLocalIpAddress(preferredIp: String? = null): String {
        if (!preferredIp.isNullOrBlank()) return preferredIp
        val candidates = mutableListOf<Pair<Int, String>>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                val priority = when {
                    name.startsWith("p2p") -> 4
                    name.startsWith("wlan") -> 3
                    name.startsWith("ap") || name.startsWith("swlan") -> 2
                    name.startsWith("eth") -> 1
                    else -> 0
                }
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        addr.hostAddress?.let { ip -> candidates.add(priority to ip) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return candidates.maxByOrNull { it.first }?.second ?: "127.0.0.1"
    }

    fun buildSessionUrl(
        host: String,
        port: Int,
        token: String,
        deviceName: String,
        mode: String
    ): String {
        val encodedName = URLEncoder.encode(deviceName, "UTF-8")
        return "http://$host:$port/?token=$token&mode=$mode&name=$encodedName"
    }

    fun parseQrPayload(raw: String): QrConnectionPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        // Try URL format: http://ip:port/?token=...&mode=...&name=...
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return try {
                val uri = Uri.parse(trimmed)
                val host = uri.host ?: return null
                val port = if (uri.port > 0) uri.port else 8765
                val token = uri.getQueryParameter("token") ?: return null
                val mode = uri.getQueryParameter("mode") ?: "receive"
                val name = uri.getQueryParameter("name") ?: host
                QrConnectionPayload(host = host, port = port, token = token, name = name, mode = mode)
            } catch (_: Exception) {
                null
            }
        }

        // Try JSON format fallback
        if (trimmed.startsWith("{")) {
            return try {
                val obj = Json.parseToJsonElement(trimmed).jsonObject
                val host = obj["host"]?.jsonPrimitive?.content ?: return null
                val port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 8765
                val token = obj["token"]?.jsonPrimitive?.content ?: return null
                val name = obj["name"]?.jsonPrimitive?.content ?: host
                val mode = obj["mode"]?.jsonPrimitive?.content ?: "receive"
                QrConnectionPayload(host = host, port = port, token = token, name = name, mode = mode)
            } catch (_: Exception) {
                null
            }
        }

        // Try host:port format for manual connections
        val parts = trimmed.split(":")
        if (parts.size == 2) {
            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull() ?: return null
            if (host.isNotEmpty()) {
                return QrConnectionPayload(host = host, port = port, token = "", name = host, mode = "receive")
            }
        }

        return null
    }

    fun generateQrBitmap(content: String, sizePx: Int = 560): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.parseColor("#060910") else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}

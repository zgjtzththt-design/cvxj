package com.example.network

import android.net.Uri
import com.example.model.ActiveTransferTask
import com.example.model.ConnectionMethod
import com.example.model.OfferRequestPayload
import com.example.model.OfferResponsePayload
import com.example.model.OfferedFileMetadata
import com.example.model.PeerDeviceInfo
import com.example.model.PendingIncomingOffer
import com.example.model.SelectedFileItem
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.model.WebPortalStatusDto
import com.example.model.WebSharedFileDto
import com.example.service.FileHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DropLinkHttpServer(
    private val fileHelper: FileHelper,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    interface Listener {
        fun getDeviceInfo(mode: String): PeerDeviceInfo
        fun getCustomSaveTreeUri(): String?
        suspend fun awaitIncomingConsent(offer: PendingIncomingOffer): Boolean
        fun onTaskUpdated(task: ActiveTransferTask)
        suspend fun onTransferFinished(task: ActiveTransferTask, localUri: String?)
        fun onPeerInteraction()
    }

    private var serverScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Volatile
    var sessionToken: String = ""
        private set

    @Volatile
    var sessionMode: String = "receive"
        private set

    @Volatile
    var expiresAtMs: Long = 0L
        private set

    @Volatile
    var sharedFiles: List<SelectedFileItem> = emptyList()

    private val approvedOfferIds = ConcurrentHashMap<String, Boolean>()
    private val activeSockets = ConcurrentHashMap<String, Socket>()

    val listeningPort: Int
        get() = serverSocket?.localPort ?: 0

    val isRunning: Boolean
        get() = serverSocket?.isClosed == false

    fun start(
        preferredPort: Int,
        mode: String,
        token: String,
        ttlMinutes: Int,
        filesToShare: List<SelectedFileItem> = emptyList(),
        listener: Listener
    ): Int {
        stop()
        sessionToken = token
        sessionMode = mode
        expiresAtMs = System.currentTimeMillis() + ttlMinutes * 60_000L
        sharedFiles = filesToShare
        approvedOfferIds.clear()

        val sock = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(preferredPort))
            }
        } catch (_: Exception) {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
        }
        serverSocket = sock
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serverScope = scope
        acceptJob = scope.launch {
            while (isActive && !sock.isClosed) {
                try {
                    val client = sock.accept()
                    launch { handleClient(client, listener) }
                } catch (_: Exception) {
                    if (sock.isClosed) break
                }
            }
        }
        return sock.localPort
    }

    fun stop() {
        approvedOfferIds.clear()
        activeSockets.values.forEach { runCatching { it.close() } }
        activeSockets.clear()
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        serverScope?.cancel()
        serverScope = null
    }

    fun cancelTransfer(transferId: String) {
        activeSockets[transferId]?.let { runCatching { it.close() } }
    }

    private suspend fun handleClient(socket: Socket, listener: Listener) = withContext(Dispatchers.IO) {
        socket.use { sock ->
            sock.soTimeout = 30_000
            val input = BufferedInputStream(sock.getInputStream(), 64 * 1024)
            val output = sock.getOutputStream()

            val requestLine = readLineAscii(input) ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext
            val method = parts[0].uppercase()
            val rawTarget = parts[1]

            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLineAscii(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase()] =
                        line.substring(colon + 1).trim()
                }
            }

            val parsedUri = Uri.parse("http://localhost$rawTarget")
            val path = parsedUri.path ?: "/"
            val queryToken = parsedUri.getQueryParameter("token") ?: headers["x-session-token"] ?: ""

            if (System.currentTimeMillis() > expiresAtMs) {
                writeJsonResponse(output, 410, "Gone", """{"error":"session_expired"}""")
                return@withContext
            }

            // /api/info is allowed without token so NSD/manual peers can inspect device name cleanly,
            // OR requires valid token when strict; here we return non-sensitive device info only.
            if (method == "GET" && path == "/api/info") {
                val info = listener.getDeviceInfo(sessionMode)
                writeJsonResponse(output, 200, "OK", json.encodeToString(PeerDeviceInfo.serializer(), info))
                return@withContext
            }

            // Validate session token for all portal & transfer routes
            if (sessionToken.isNotEmpty() && !HashUtils.constantTimeEquals(queryToken, sessionToken)) {
                writeJsonResponse(output, 401, "Unauthorized", """{"error":"invalid_session_token"}""")
                return@withContext
            }

            listener.onPeerInteraction()

            when {
                method == "GET" && path == "/" -> {
                    val info = listener.getDeviceInfo(sessionMode)
                    val html = WebPortalHtml.render(info.deviceName, sessionMode, sessionToken)
                    writeHtmlResponse(output, html)
                }

                method == "GET" && path == "/api/status" -> {
                    val info = listener.getDeviceInfo(sessionMode)
                    val dto = WebPortalStatusDto(
                        deviceName = info.deviceName,
                        mode = sessionMode,
                        expiresAtMs = expiresAtMs,
                        files = sharedFiles.mapIndexed { index, item ->
                            WebSharedFileDto(
                                index = index,
                                name = item.name,
                                sizeBytes = item.sizeBytes,
                                mimeType = item.mimeType,
                                sha256 = item.sha256
                            )
                        }
                    )
                    writeJsonResponse(output, 200, "OK", json.encodeToString(WebPortalStatusDto.serializer(), dto))
                }

                method == "POST" && path == "/api/offer" -> {
                    val len = headers["content-length"]?.toIntOrNull() ?: 0
                    if (len <= 0 || len > 512 * 1024) {
                        writeJsonResponse(output, 400, "Bad Request", """{"error":"invalid_offer"}""")
                        return@withContext
                    }
                    val bodyBytes = readExactBytes(input, len)
                    val payload = runCatching {
                        json.decodeFromString(OfferRequestPayload.serializer(), bodyBytes.decodeToString())
                    }.getOrNull()
                    if (payload == null) {
                        writeJsonResponse(output, 400, "Bad Request", """{"error":"malformed_json"}""")
                        return@withContext
                    }
                    val remoteIp = (sock.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
                    val offer = PendingIncomingOffer(
                        offerId = payload.offerId,
                        senderDeviceId = payload.senderDeviceId,
                        senderName = payload.senderName,
                        senderAddress = remoteIp,
                        method = ConnectionMethod.LAN_HTTP,
                        files = payload.files
                    )
                    val accepted = listener.awaitIncomingConsent(offer)
                    approvedOfferIds[payload.offerId] = accepted
                    val resp = OfferResponsePayload(
                        accepted = accepted,
                        reason = if (accepted) null else "rejected_by_user"
                    )
                    writeJsonResponse(output, 200, "OK", json.encodeToString(OfferResponsePayload.serializer(), resp))
                }

                method == "GET" && path == "/api/partial" -> {
                    val transferId = HashUtils.sanitizeFileName(parsedUri.getQueryParameter("id"))
                    val partFile = File(fileHelper.getTempIncomingDir(), "$transferId.part")
                    val existing = if (partFile.exists()) partFile.length() else 0L
                    writeJsonResponse(output, 200, "OK", """{"bytes":$existing}""")
                }

                method == "POST" && path == "/api/upload" -> {
                    handleUpload(sock, input, output, headers, listener)
                }

                method == "GET" && path.startsWith("/api/download/") -> {
                    val index = path.removePrefix("/api/download/").toIntOrNull()
                    handleDownload(sock, output, headers, index, listener)
                }

                else -> {
                    writeJsonResponse(output, 404, "Not Found", """{"error":"not_found"}""")
                }
            }
        }
    }

    private suspend fun handleUpload(
        socket: Socket,
        input: BufferedInputStream,
        output: OutputStream,
        headers: Map<String, String>,
        listener: Listener
    ) {
        if (sessionMode != "receive") {
            writeJsonResponse(output, 403, "Forbidden", """{"error":"not_in_receive_mode"}""")
            return
        }

        val rawName = headers["x-file-name"]?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        }
        val fileName = HashUtils.sanitizeFileName(rawName)
        val totalSize = headers["x-file-size"]?.toLongOrNull() ?: headers["content-length"]?.toLongOrNull() ?: 0L
        val contentLength = headers["content-length"]?.toLongOrNull() ?: -1L
        val chunkOffset = headers["x-chunk-offset"]?.toLongOrNull() ?: 0L
        val mimeType = headers["x-file-mime"] ?: "application/octet-stream"
        val expectedSha256 = headers["x-file-sha256"]?.lowercase()
        val offerId = headers["x-offer-id"]
        val transferId = HashUtils.sanitizeFileName(headers["x-transfer-id"] ?: UUID.randomUUID().toString())
        val senderName = headers["x-sender-name"]?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        } ?: "Web Browser"
        val senderDeviceId = headers["x-sender-id"] ?: senderName
        val remoteIp = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "unknown"
        val connMethod = if (offerId.isNullOrBlank()) ConnectionMethod.WEB_BROWSER else ConnectionMethod.LAN_HTTP

        if (contentLength < 0L) {
            writeJsonResponse(output, 411, "Length Required", """{"error":"content_length_required"}""")
            return
        }

        // Require consent if not already pre-approved via /api/offer
        val preApproved = if (!offerId.isNullOrBlank()) approvedOfferIds[offerId] else null
        val isApproved = preApproved ?: listener.awaitIncomingConsent(
            PendingIncomingOffer(
                offerId = offerId ?: transferId,
                senderDeviceId = senderDeviceId,
                senderName = senderName,
                senderAddress = remoteIp,
                method = connMethod,
                files = listOf(
                    OfferedFileMetadata(
                        id = transferId,
                        name = fileName,
                        sizeBytes = totalSize.coerceAtLeast(contentLength),
                        mimeType = mimeType,
                        sha256 = expectedSha256
                    )
                )
            )
        )

        if (!isApproved) {
            writeJsonResponse(output, 403, "Forbidden", """{"error":"rejected_by_receiver"}""")
            return
        }

        activeSockets[transferId] = socket
        val tempDir = fileHelper.getTempIncomingDir()
        val partFile = File(tempDir, "$transferId.part")
        val append = chunkOffset > 0L && partFile.exists() && partFile.length() == chunkOffset
        if (!append && partFile.exists()) {
            partFile.delete()
        }

        val effectiveTotal = totalSize.coerceAtLeast(chunkOffset + contentLength)
        var bytesWritten = if (append) chunkOffset else 0L
        val startMs = System.currentTimeMillis()
        var lastEmitMs = 0L

        var task = ActiveTransferTask(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = effectiveTotal,
            bytesTransferred = bytesWritten,
            speedBps = 0L,
            remainingSeconds = 0L,
            direction = TransferDirection.RECEIVED,
            peerName = senderName,
            peerAddress = remoteIp,
            method = connMethod,
            status = TransferStatus.IN_PROGRESS,
            sha256 = expectedSha256,
            canResume = true,
            startedAtMs = startMs
        )
        listener.onTaskUpdated(task)

        try {
            val buffer = ByteArray(64 * 1024)
            var remainingToRead = contentLength
            FileOutputStream(partFile, append).use { fos ->
                while (remainingToRead > 0L) {
                    val toRead = minOf(buffer.size.toLong(), remainingToRead).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    fos.write(buffer, 0, read)
                    remainingToRead -= read
                    bytesWritten += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmitMs >= 200L) {
                        val elapsedSec = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                        val sessionBytes = bytesWritten - (if (append) chunkOffset else 0L)
                        val speed = (sessionBytes / elapsedSec).toLong()
                        val remBytes = (effectiveTotal - bytesWritten).coerceAtLeast(0L)
                        val eta = if (speed > 0L) remBytes / speed else 0L
                        task = task.copy(
                            bytesTransferred = bytesWritten,
                            speedBps = speed,
                            remainingSeconds = eta
                        )
                        listener.onTaskUpdated(task)
                        lastEmitMs = now
                    }
                }
                fos.flush()
            }

            if (remainingToRead > 0L) {
                task = task.copy(
                    bytesTransferred = bytesWritten,
                    status = TransferStatus.PAUSED,
                    errorMessage = "Connection interrupted at ${HashUtils.formatBytes(bytesWritten)}"
                )
                listener.onTaskUpdated(task)
                listener.onTransferFinished(task, null)
                writeJsonResponse(output, 499, "Client Closed Request", """{"error":"incomplete_stream"}""")
                return
            }

            val actualSha = partFile.inputStream().use { HashUtils.computeSha256(it) }
            val integrityOk = expectedSha256.isNullOrBlank() || expectedSha256 == actualSha

            if (!integrityOk) {
                partFile.delete()
                task = task.copy(
                    status = TransferStatus.FAILED,
                    sha256 = actualSha,
                    integrityVerified = false,
                    errorMessage = "SHA-256 checksum mismatch"
                )
                listener.onTaskUpdated(task)
                listener.onTransferFinished(task, null)
                writeJsonResponse(output, 422, "Unprocessable Entity", """{"error":"checksum_mismatch"}""")
                return
            }

            val savedUri = fileHelper.publishReceivedTempFile(
                tempFile = partFile,
                fileName = fileName,
                mimeType = mimeType,
                customTreeUriString = listener.getCustomSaveTreeUri()
            )
            val elapsedSec = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.1)
            val avgSpeed = (bytesWritten / elapsedSec).toLong()
            task = task.copy(
                bytesTransferred = bytesWritten,
                speedBps = avgSpeed,
                remainingSeconds = 0L,
                status = TransferStatus.COMPLETED,
                sha256 = actualSha,
                integrityVerified = true,
                localUri = savedUri?.toString(),
                canResume = false
            )
            listener.onTaskUpdated(task)
            listener.onTransferFinished(task, savedUri?.toString())
            writeJsonResponse(output, 200, "OK", """{"status":"ok","sha256":"$actualSha"}""")
        } catch (e: Exception) {
            val isCancelled = socket.isClosed
            task = task.copy(
                bytesTransferred = bytesWritten,
                status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.PAUSED,
                errorMessage = e.message ?: "Upload interrupted"
            )
            if (isCancelled) partFile.delete()
            listener.onTaskUpdated(task)
            listener.onTransferFinished(task, null)
        } finally {
            activeSockets.remove(transferId)
        }
    }

    private suspend fun handleDownload(
        socket: Socket,
        output: OutputStream,
        headers: Map<String, String>,
        index: Int?,
        listener: Listener
    ) {
        if (sessionMode != "send" || index == null || index !in sharedFiles.indices) {
            writeJsonResponse(output, 404, "Not Found", """{"error":"file_not_found"}""")
            return
        }
        val item = sharedFiles[index]
        val total = item.sizeBytes
        val rangeHeader = headers["range"]
        var start = 0L
        var end = (total - 1L).coerceAtLeast(0L)
        var isPartial = false

        if (rangeHeader != null && rangeHeader.startsWith("bytes=") && total > 0L) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").split("-")
            start = rangeSpec.getOrNull(0)?.toLongOrNull()?.coerceIn(0L, total - 1) ?: 0L
            end = rangeSpec.getOrNull(1)?.toLongOrNull()?.coerceIn(start, total - 1) ?: (total - 1)
            isPartial = true
        }
        val contentLength = if (total > 0L) (end - start + 1L) else 0L
        val encodedFileName = java.net.URLEncoder.encode(item.name, "UTF-8").replace("+", "%20")

        val sb = StringBuilder()
        if (isPartial) {
            sb.append("HTTP/1.1 206 Partial Content\r\n")
            sb.append("Content-Range: bytes $start-$end/$total\r\n")
        } else {
            sb.append("HTTP/1.1 200 OK\r\n")
        }
        sb.append("Content-Type: ${item.mimeType}\r\n")
        sb.append("Content-Length: $contentLength\r\n")
        sb.append("Accept-Ranges: bytes\r\n")
        sb.append("Content-Disposition: attachment; filename*=UTF-8''$encodedFileName\r\n")
        sb.append("Connection: close\r\n\r\n")
        output.write(sb.toString().toByteArray(Charsets.US_ASCII))

        val transferId = "share-${item.id}-${System.currentTimeMillis()}"
        activeSockets[transferId] = socket
        val remoteIp = (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "Browser"
        val startMs = System.currentTimeMillis()
        var sentBytes = start
        var lastEmitMs = 0L

        var task = ActiveTransferTask(
            id = transferId,
            fileName = item.name,
            fileSizeBytes = total,
            bytesTransferred = sentBytes,
            speedBps = 0L,
            remainingSeconds = 0L,
            direction = TransferDirection.SENT,
            peerName = "Browser / Peer",
            peerAddress = remoteIp,
            method = ConnectionMethod.WEB_BROWSER,
            status = TransferStatus.IN_PROGRESS,
            sha256 = item.sha256,
            startedAtMs = startMs
        )
        listener.onTaskUpdated(task)

        try {
            val stream = fileHelper.openInputStream(item.uri)
            if (stream != null) {
                stream.use { input ->
                    if (start > 0L) skipFully(input, start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = contentLength
                    val digest = MessageDigest.getInstance("SHA-256")
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        if (!isPartial) digest.update(buffer, 0, read)
                        remaining -= read
                        sentBytes += read

                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 200L) {
                            val elapsed = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                            val speed = ((sentBytes - start) / elapsed).toLong()
                            val rem = (total - sentBytes).coerceAtLeast(0L)
                            val eta = if (speed > 0L) rem / speed else 0L
                            task = task.copy(bytesTransferred = sentBytes, speedBps = speed, remainingSeconds = eta)
                            listener.onTaskUpdated(task)
                            lastEmitMs = now
                        }
                    }
                    output.flush()
                    val sha = if (!isPartial) digest.digest().joinToString("") { "%02x".format(it) } else item.sha256
                    task = task.copy(
                        bytesTransferred = sentBytes,
                        status = TransferStatus.COMPLETED,
                        sha256 = sha,
                        integrityVerified = sha != null,
                        remainingSeconds = 0L
                    )
                    listener.onTaskUpdated(task)
                    listener.onTransferFinished(task, item.uri.toString())
                }
            }
        } catch (e: Exception) {
            task = task.copy(
                bytesTransferred = sentBytes,
                status = TransferStatus.CANCELLED,
                errorMessage = e.message
            )
            listener.onTaskUpdated(task)
            listener.onTransferFinished(task, null)
        } finally {
            activeSockets.remove(transferId)
        }
    }

    private fun skipFully(input: InputStream, bytesToSkip: Long) {
        var remaining = bytesToSkip
        val buf = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (read <= 0) break
                remaining -= read
            }
        }
    }

    private fun readLineAscii(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString("UTF-8")
            }
            if (b == '\n'.code) {
                val bytes = baos.toByteArray()
                val len = if (prev == '\r'.code && bytes.isNotEmpty()) bytes.size - 1 else bytes.size
                return String(bytes, 0, len, Charsets.UTF_8)
            }
            baos.write(b)
            prev = b
        }
    }

    private fun readExactBytes(input: BufferedInputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(out, offset, length - offset)
            if (read == -1) break
            offset += read
        }
        return if (offset == length) out else out.copyOf(offset)
    }

    private fun writeHtmlResponse(output: OutputStream, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }

    private fun writeJsonResponse(output: OutputStream, code: Int, reason: String, jsonBody: String) {
        val body = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.US_ASCII))
        output.write(body)
        output.flush()
    }
}

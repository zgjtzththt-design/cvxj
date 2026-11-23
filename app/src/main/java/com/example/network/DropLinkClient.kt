package com.example.network

import com.example.model.ActiveTransferTask
import com.example.model.ConnectionMethod
import com.example.model.OfferRequestPayload
import com.example.model.OfferResponsePayload
import com.example.model.OfferedFileMetadata
import com.example.model.PeerDeviceInfo
import com.example.model.SelectedFileItem
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.model.WebPortalStatusDto
import com.example.service.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DropLinkClient(
    private val fileHelper: FileHelper,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val activeCalls = ConcurrentHashMap<String, Call>()

    fun cancelTransfer(transferId: String) {
        activeCalls[transferId]?.cancel()
    }

    suspend fun fetchPeerInfo(host: String, port: Int): PeerDeviceInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("http://$host:$port/api/info")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                json.decodeFromString(PeerDeviceInfo.serializer(), body)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchSharedFilesStatus(host: String, port: Int, token: String): WebPortalStatusDto? =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/status?token=${URLEncoder.encode(token, "UTF-8")}"
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val body = resp.body?.string() ?: return@withContext null
                    json.decodeFromString(WebPortalStatusDto.serializer(), body)
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun requestOfferApproval(
        host: String,
        port: Int,
        token: String,
        offerId: String,
        localDeviceId: String,
        localDeviceName: String,
        files: List<SelectedFileItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = OfferRequestPayload(
                offerId = offerId,
                senderDeviceId = localDeviceId,
                senderName = localDeviceName,
                files = files.map {
                    OfferedFileMetadata(
                        id = it.id,
                        name = it.name,
                        sizeBytes = it.sizeBytes,
                        mimeType = it.mimeType,
                        sha256 = it.sha256
                    )
                }
            )
            val body = json.encodeToString(OfferRequestPayload.serializer(), payload)
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("http://$host:$port/api/offer?token=${URLEncoder.encode(token, "UTF-8")}")
                .post(body)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val text = resp.body?.string() ?: return@withContext false
                json.decodeFromString(OfferResponsePayload.serializer(), text).accepted
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun queryPartialOffset(host: String, port: Int, token: String, transferId: String): Long =
        withContext(Dispatchers.IO) {
            try {
                val url = "http://$host:$port/api/partial?id=${URLEncoder.encode(transferId, "UTF-8")}&token=${URLEncoder.encode(token, "UTF-8")}"
                val req = Request.Builder().url(url).get().build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext 0L
                    val body = resp.body?.string() ?: return@withContext 0L
                    json.parseToJsonElement(body).jsonObject["bytes"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                }
            } catch (_: Exception) {
                0L
            }
        }

    suspend fun uploadFile(
        transferId: String,
        file: SelectedFileItem,
        sha256: String,
        host: String,
        port: Int,
        token: String,
        offerId: String,
        localDeviceId: String,
        localDeviceName: String,
        peerName: String,
        method: ConnectionMethod,
        resumeOffset: Long = 0L,
        onProgress: (ActiveTransferTask) -> Unit
    ): ActiveTransferTask = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        var lastEmitMs = 0L
        val total = file.sizeBytes

        var currentTask = ActiveTransferTask(
            id = transferId,
            fileName = file.name,
            fileSizeBytes = total,
            bytesTransferred = resumeOffset,
            speedBps = 0L,
            remainingSeconds = 0L,
            direction = TransferDirection.SENT,
            peerName = peerName,
            peerAddress = "$host:$port",
            method = method,
            status = TransferStatus.IN_PROGRESS,
            sha256 = sha256,
            canResume = true,
            startedAtMs = startMs
        )
        onProgress(currentTask)

        val contentLen = (total - resumeOffset).coerceAtLeast(0L)
        val requestBody = object : RequestBody() {
            override fun contentType() = file.mimeType.toMediaType()
            override fun contentLength(): Long = contentLen

            override fun writeTo(sink: BufferedSink) {
                val stream = fileHelper.openInputStream(file.uri)
                    ?: throw IllegalStateException("Cannot open selected file")
                stream.use { input ->
                    if (resumeOffset > 0L) skipFully(input, resumeOffset)
                    val buffer = ByteArray(64 * 1024)
                    var sentFromOffset = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        sentFromOffset += read
                        val totalSent = resumeOffset + sentFromOffset
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 180L || totalSent == total) {
                            val elapsed = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                            val speed = (sentFromOffset / elapsed).toLong()
                            val rem = (total - totalSent).coerceAtLeast(0L)
                            val eta = if (speed > 0L) rem / speed else 0L
                            currentTask = currentTask.copy(
                                bytesTransferred = totalSent,
                                speedBps = speed,
                                remainingSeconds = eta
                            )
                            onProgress(currentTask)
                            lastEmitMs = now
                        }
                    }
                    sink.flush()
                }
            }
        }

        val req = Request.Builder()
            .url("http://$host:$port/api/upload?token=${URLEncoder.encode(token, "UTF-8")}")
            .header("X-File-Name", URLEncoder.encode(file.name, "UTF-8"))
            .header("X-File-Size", total.toString())
            .header("X-File-Mime", file.mimeType)
            .header("X-File-Sha256", sha256)
            .header("X-Chunk-Offset", resumeOffset.toString())
            .header("X-Offer-Id", offerId)
            .header("X-Transfer-Id", transferId)
            .header("X-Sender-Id", localDeviceId)
            .header("X-Sender-Name", URLEncoder.encode(localDeviceName, "UTF-8"))
            .post(requestBody)
            .build()

        val call = httpClient.newCall(req)
        activeCalls[transferId] = call
        return@withContext try {
            call.execute().use { resp ->
                if (resp.isSuccessful) {
                    val elapsed = ((System.currentTimeMillis() - startMs) / 1000.0).coerceAtLeast(0.1)
                    val avgSpeed = ((total - resumeOffset) / elapsed).toLong()
                    currentTask.copy(
                        bytesTransferred = total,
                        speedBps = avgSpeed,
                        remainingSeconds = 0L,
                        status = TransferStatus.COMPLETED,
                        integrityVerified = true,
                        localUri = file.uri.toString(),
                        canResume = false
                    )
                } else {
                    val status = if (resp.code == 403) TransferStatus.CANCELLED else TransferStatus.FAILED
                    currentTask.copy(
                        status = status,
                        errorMessage = "HTTP ${resp.code}: ${resp.message}"
                    )
                }
            }
        } catch (e: Exception) {
            val isCancelled = call.isCanceled()
            currentTask.copy(
                status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.PAUSED,
                canResume = !isCancelled,
                errorMessage = e.message ?: "Connection error"
            )
        } finally {
            activeCalls.remove(transferId)
        }
    }

    suspend fun downloadSharedFile(
        host: String,
        port: Int,
        token: String,
        fileIndex: Int,
        fileName: String,
        totalSize: Long,
        mimeType: String,
        expectedSha256: String?,
        peerName: String,
        customSaveTreeUri: String?,
        onProgress: (ActiveTransferTask) -> Unit
    ): ActiveTransferTask = withContext(Dispatchers.IO) {
        val transferId = "dl-${UUID.randomUUID().toString().take(8)}"
        val partFile = File(fileHelper.getTempIncomingDir(), "$transferId.part")
        val existingOffset = if (partFile.exists()) partFile.length() else 0L
        val startMs = System.currentTimeMillis()
        var lastEmitMs = 0L

        var currentTask = ActiveTransferTask(
            id = transferId,
            fileName = fileName,
            fileSizeBytes = totalSize,
            bytesTransferred = existingOffset,
            speedBps = 0L,
            remainingSeconds = 0L,
            direction = TransferDirection.RECEIVED,
            peerName = peerName,
            peerAddress = "$host:$port",
            method = ConnectionMethod.LAN_HTTP,
            status = TransferStatus.IN_PROGRESS,
            sha256 = expectedSha256,
            canResume = true,
            startedAtMs = startMs
        )
        onProgress(currentTask)

        val url = "http://$host:$port/api/download/$fileIndex?token=${URLEncoder.encode(token, "UTF-8")}"
        val reqBuilder = Request.Builder().url(url).get()
        if (existingOffset > 0L) {
            reqBuilder.header("Range", "bytes=$existingOffset-")
        }
        val call = httpClient.newCall(reqBuilder.build())
        activeCalls[transferId] = call

        return@withContext try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext currentTask.copy(
                        status = TransferStatus.FAILED,
                        errorMessage = "HTTP ${resp.code}"
                    )
                }
                val append = resp.code == 206 && existingOffset > 0L
                val bodyStream = resp.body?.byteStream()
                    ?: return@withContext currentTask.copy(status = TransferStatus.FAILED, errorMessage = "Empty body")
                var bytesWritten = if (append) existingOffset else 0L
                val buffer = ByteArray(64 * 1024)
                bodyStream.use { input ->
                    FileOutputStream(partFile, append).use { fos ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            bytesWritten += read
                            val now = System.currentTimeMillis()
                            if (now - lastEmitMs >= 180L) {
                                val elapsed = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                                val speed = ((bytesWritten - (if (append) existingOffset else 0L)) / elapsed).toLong()
                                val rem = (totalSize - bytesWritten).coerceAtLeast(0L)
                                val eta = if (speed > 0L) rem / speed else 0L
                                currentTask = currentTask.copy(
                                    bytesTransferred = bytesWritten,
                                    speedBps = speed,
                                    remainingSeconds = eta
                                )
                                onProgress(currentTask)
                                lastEmitMs = now
                            }
                        }
                        fos.flush()
                    }
                }
                val actualSha = partFile.inputStream().use { HashUtils.computeSha256(it) }
                if (!expectedSha256.isNullOrBlank() && expectedSha256 != actualSha) {
                    partFile.delete()
                    return@withContext currentTask.copy(
                        status = TransferStatus.FAILED,
                        sha256 = actualSha,
                        integrityVerified = false,
                        errorMessage = "SHA-256 verification failed"
                    )
                }
                val savedUri = fileHelper.publishReceivedTempFile(partFile, fileName, mimeType, customSaveTreeUri)
                currentTask.copy(
                    bytesTransferred = bytesWritten,
                    status = TransferStatus.COMPLETED,
                    sha256 = actualSha,
                    integrityVerified = true,
                    localUri = savedUri?.toString(),
                    remainingSeconds = 0L,
                    canResume = false
                )
            }
        } catch (e: Exception) {
            val isCancelled = call.isCanceled()
            if (isCancelled) partFile.delete()
            currentTask.copy(
                status = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.PAUSED,
                errorMessage = e.message
            )
        } finally {
            activeCalls.remove(transferId)
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
}

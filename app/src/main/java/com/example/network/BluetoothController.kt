package com.example.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.ActiveTransferTask
import com.example.model.BluetoothState
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import com.example.model.OfferedFileMetadata
import com.example.model.PendingIncomingOffer
import com.example.model.SelectedFileItem
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.service.FileHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class BluetoothController(
    private val context: Context,
    private val fileHelper: FileHelper
) {
    companion object {
        val DROPLINK_UUID: UUID = UUID.fromString("8a9c4b20-6f8b-4d7e-9c12-4b6a1e3d9011")
        const val SERVICE_NAME = "DropLinkBT"
    }

    @Serializable
    private data class BtFileHeader(
        val transferId: String,
        val senderDeviceId: String,
        val senderName: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val sha256: String
    )

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val json = Json { ignoreUnknownKeys = true }

    private var serverSocket: BluetoothServerSocket? = null
    private var listenJob: Job? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiverRegistered = false

    private val _state = MutableStateFlow(
        BluetoothState(
            isSupported = adapter != null,
            isEnabled = adapter?.isEnabled == true
        )
    )
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && hasConnectPermission()) {
                        val peer = device.toPeer(paired = false)
                        _state.update { st ->
                            val existing = st.discoveredDevices.filterNot { it.address == peer.address }
                            st.copy(discoveredDevices = existing + peer)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { it.copy(isScanning = false) }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    _state.update { it.copy(isEnabled = adapter?.isEnabled == true) }
                }
            }
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val bt = adapter ?: run {
            _state.update { it.copy(isSupported = false, isEnabled = false) }
            return
        }
        if (!hasConnectPermission()) {
            _state.update { it.copy(statusMessage = "Bluetooth connect permission needed") }
            return
        }
        val paired = runCatching {
            bt.bondedDevices?.map { it.toPeer(paired = true) }.orEmpty()
        }.getOrDefault(emptyList())
        _state.update {
            it.copy(
                isSupported = true,
                isEnabled = bt.isEnabled,
                pairedDevices = paired
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val bt = adapter ?: return
        if (!hasScanPermission() || !hasConnectPermission()) return
        if (!bt.isEnabled) {
            _state.update { it.copy(isEnabled = false, statusMessage = "Bluetooth is turned off") }
            return
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, discoveryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        refreshPairedDevices()
        runCatching {
            if (bt.isDiscovering) bt.cancelDiscovery()
            bt.startDiscovery()
            _state.update { it.copy(isScanning = true, discoveredDevices = emptyList(), statusMessage = null) }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (hasScanPermission()) {
            runCatching { adapter?.cancelDiscovery() }
        }
        _state.update { it.copy(isScanning = false) }
    }

    @SuppressLint("MissingPermission")
    fun startListening(
        customSaveTreeUri: () -> String?,
        awaitConsent: suspend (PendingIncomingOffer) -> Boolean,
        onTaskUpdate: (ActiveTransferTask) -> Unit,
        onTaskFinished: suspend (ActiveTransferTask, String?) -> Unit
    ) {
        val bt = adapter ?: return
        if (!hasConnectPermission() || !bt.isEnabled) return
        stopListening()
        listenJob = scope.launch {
            try {
                val srv = bt.listenUsingRfcommWithServiceRecord(SERVICE_NAME, DROPLINK_UUID)
                serverSocket = srv
                _state.update { it.copy(isListening = true, statusMessage = null) }
                while (isActive) {
                    val sock = try {
                        srv.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch {
                        handleIncomingSocket(sock, customSaveTreeUri, awaitConsent, onTaskUpdate, onTaskFinished)
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isListening = false, statusMessage = e.message) }
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        _state.update { it.copy(isListening = false) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun handleIncomingSocket(
        socket: BluetoothSocket,
        customSaveTreeUri: () -> String?,
        awaitConsent: suspend (PendingIncomingOffer) -> Boolean,
        onTaskUpdate: (ActiveTransferTask) -> Unit,
        onTaskFinished: suspend (ActiveTransferTask, String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        socket.use { sock ->
            val input = DataInputStream(sock.inputStream)
            val output = DataOutputStream(sock.outputStream)
            val headerLen = input.readInt()
            if (headerLen <= 0 || headerLen > 64 * 1024) return@withContext
            val headerBytes = ByteArray(headerLen)
            input.readFully(headerBytes)
            val header = json.decodeFromString(BtFileHeader.serializer(), headerBytes.decodeToString())
            val cleanName = HashUtils.sanitizeFileName(header.fileName)
            val remoteAddr = sock.remoteDevice?.address ?: "Bluetooth"

            val accepted = awaitConsent(
                PendingIncomingOffer(
                    offerId = header.transferId,
                    senderDeviceId = header.senderDeviceId,
                    senderName = header.senderName,
                    senderAddress = remoteAddr,
                    method = ConnectionMethod.BLUETOOTH,
                    files = listOf(
                        OfferedFileMetadata(
                            id = header.transferId,
                            name = cleanName,
                            sizeBytes = header.fileSize,
                            mimeType = header.mimeType,
                            sha256 = header.sha256
                        )
                    )
                )
            )
            output.writeBoolean(accepted)
            output.flush()
            if (!accepted) return@withContext

            val partFile = File(fileHelper.getTempIncomingDir(), "${header.transferId}.part")
            val startMs = System.currentTimeMillis()
            var received = 0L
            var lastEmit = 0L
            var task = ActiveTransferTask(
                id = header.transferId,
                fileName = cleanName,
                fileSizeBytes = header.fileSize,
                bytesTransferred = 0L,
                speedBps = 0L,
                remainingSeconds = 0L,
                direction = TransferDirection.RECEIVED,
                peerName = header.senderName,
                peerAddress = remoteAddr,
                method = ConnectionMethod.BLUETOOTH,
                status = TransferStatus.IN_PROGRESS,
                sha256 = header.sha256,
                startedAtMs = startMs
            )
            onTaskUpdate(task)

            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                FileOutputStream(partFile).use { fos ->
                    var remaining = header.fileSize
                    while (remaining > 0L) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        remaining -= read
                        received += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200L) {
                            val elapsed = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                            val speed = (received / elapsed).toLong()
                            val eta = if (speed > 0L) (header.fileSize - received) / speed else 0L
                            task = task.copy(bytesTransferred = received, speedBps = speed, remainingSeconds = eta)
                            onTaskUpdate(task)
                            lastEmit = now
                        }
                    }
                }
                val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                if (header.sha256.isNotBlank() && header.sha256 != actualSha) {
                    partFile.delete()
                    task = task.copy(status = TransferStatus.FAILED, errorMessage = "SHA-256 mismatch")
                    onTaskUpdate(task)
                    onTaskFinished(task, null)
                    output.writeBoolean(false)
                    return@withContext
                }
                val savedUri = fileHelper.publishReceivedTempFile(partFile, cleanName, header.mimeType, customSaveTreeUri())
                output.writeBoolean(true)
                output.flush()
                task = task.copy(
                    bytesTransferred = received,
                    status = TransferStatus.COMPLETED,
                    integrityVerified = true,
                    localUri = savedUri?.toString()
                )
                onTaskUpdate(task)
                onTaskFinished(task, savedUri?.toString())
            } catch (e: Exception) {
                partFile.delete()
                task = task.copy(status = TransferStatus.FAILED, errorMessage = e.message)
                onTaskUpdate(task)
                onTaskFinished(task, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun sendFileOverBluetooth(
        peerAddress: String,
        peerName: String,
        file: SelectedFileItem,
        sha256: String,
        localDeviceId: String,
        localDeviceName: String,
        onProgress: (ActiveTransferTask) -> Unit
    ): ActiveTransferTask = withContext(Dispatchers.IO) {
        val transferId = "bt-${UUID.randomUUID().toString().take(8)}"
        val startMs = System.currentTimeMillis()
        var task = ActiveTransferTask(
            id = transferId,
            fileName = file.name,
            fileSizeBytes = file.sizeBytes,
            bytesTransferred = 0L,
            speedBps = 0L,
            remainingSeconds = 0L,
            direction = TransferDirection.SENT,
            peerName = peerName,
            peerAddress = peerAddress,
            method = ConnectionMethod.BLUETOOTH,
            status = TransferStatus.IN_PROGRESS,
            sha256 = sha256,
            startedAtMs = startMs
        )
        val bt = adapter ?: return@withContext task.copy(status = TransferStatus.FAILED, errorMessage = "Bluetooth unavailable")
        if (!hasConnectPermission()) return@withContext task.copy(status = TransferStatus.FAILED, errorMessage = "Permission denied")

        return@withContext try {
            if (hasScanPermission() && bt.isDiscovering) bt.cancelDiscovery()
            val device = bt.getRemoteDevice(peerAddress)
            val socket = device.createRfcommSocketToServiceRecord(DROPLINK_UUID)
            socket.use { sock ->
                sock.connect()
                val out = DataOutputStream(sock.outputStream)
                val input = DataInputStream(sock.inputStream)
                val header = BtFileHeader(
                    transferId = transferId,
                    senderDeviceId = localDeviceId,
                    senderName = localDeviceName,
                    fileName = file.name,
                    fileSize = file.sizeBytes,
                    mimeType = file.mimeType,
                    sha256 = sha256
                )
                val headerBytes = json.encodeToString(BtFileHeader.serializer(), header).toByteArray(Charsets.UTF_8)
                out.writeInt(headerBytes.size)
                out.write(headerBytes)
                out.flush()

                val accepted = input.readBoolean()
                if (!accepted) {
                    return@withContext task.copy(status = TransferStatus.CANCELLED, errorMessage = "Rejected by peer")
                }

                onProgress(task)
                val stream = fileHelper.openInputStream(file.uri)
                    ?: return@withContext task.copy(status = TransferStatus.FAILED, errorMessage = "Cannot open file")
                val buffer = ByteArray(16 * 1024)
                var sent = 0L
                var lastEmit = 0L
                stream.use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        sent += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 200L) {
                            val elapsed = ((now - startMs) / 1000.0).coerceAtLeast(0.1)
                            val speed = (sent / elapsed).toLong()
                            val eta = if (speed > 0L) (file.sizeBytes - sent) / speed else 0L
                            task = task.copy(bytesTransferred = sent, speedBps = speed, remainingSeconds = eta)
                            onProgress(task)
                            lastEmit = now
                        }
                    }
                    out.flush()
                }
                val remoteOk = input.readBoolean()
                if (remoteOk) {
                    task.copy(
                        bytesTransferred = sent,
                        status = TransferStatus.COMPLETED,
                        integrityVerified = true,
                        localUri = file.uri.toString()
                    )
                } else {
                    task.copy(status = TransferStatus.FAILED, errorMessage = "Integrity check failed on peer")
                }
            }
        } catch (e: Exception) {
            task.copy(status = TransferStatus.FAILED, errorMessage = e.message ?: "Bluetooth error")
        }
    }

    fun release() {
        stopScan()
        stopListening()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(discoveryReceiver) }
            receiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toPeer(paired: Boolean): DiscoveredPeer {
        val devName = runCatching { name }.getOrNull() ?: address
        return DiscoveredPeer(
            id = "bt-$address",
            name = devName,
            address = address,
            port = 0,
            platform = if (paired) "Bluetooth (Paired)" else "Bluetooth",
            method = ConnectionMethod.BLUETOOTH,
            details = address
        )
    }
}

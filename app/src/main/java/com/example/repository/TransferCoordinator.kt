package com.example.repository

import android.content.Context
import android.net.Uri
import com.example.data.local.DropLinkDao
import com.example.data.local.DropLinkDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.TransferEntity
import com.example.data.local.TrustedDeviceEntity
import com.example.data.local.UserSettings
import com.example.model.ActiveTransferTask
import com.example.model.BluetoothState
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import com.example.model.PeerDeviceInfo
import com.example.model.PendingIncomingOffer
import com.example.model.QrConnectionPayload
import com.example.model.ReceiveSessionState
import com.example.model.ReceivedFileRecord
import com.example.model.SelectedFileItem
import com.example.model.SendShareSessionState
import com.example.model.StorageSummary
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.model.WifiDirectState
import com.example.network.BluetoothController
import com.example.network.DropLinkClient
import com.example.network.DropLinkHttpServer
import com.example.network.HashUtils
import com.example.network.NetworkUtils
import com.example.network.NsdDiscoveryManager
import com.example.network.WifiP2pController
import com.example.service.FileHelper
import com.example.service.TransferForegroundService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TransferCoordinator(private val context: Context) {

    private data class QueuedSendJob(
        val peer: DiscoveredPeer,
        val token: String,
        val files: List<SelectedFileItem>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = DropLinkDatabase.getInstance(context)
    val dao: DropLinkDao = db.dropLinkDao()
    val settingsStore = SettingsDataStore(context)
    val fileHelper = FileHelper(context)
    val httpServer = DropLinkHttpServer(fileHelper)
    val httpClient = DropLinkClient(fileHelper)
    val nsdManager = NsdDiscoveryManager(context)
    val wifiP2pController = WifiP2pController(context)
    val bluetoothController = BluetoothController(context, fileHelper)

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow
        .stateIn(scope, SharingStarted.Eagerly, UserSettings())

    val allTransfers: Flow<List<TransferEntity>> = dao.observeAllTransfers()
    val recentTransfers: Flow<List<TransferEntity>> = dao.observeRecentTransfers(6)
    val trustedDevices: Flow<List<TrustedDeviceEntity>> = dao.observeTrustedDevices()

    private val _activeTasks = MutableStateFlow<List<ActiveTransferTask>>(emptyList())
    val activeTasks: StateFlow<List<ActiveTransferTask>> = _activeTasks.asStateFlow()

    private val _receiveSession = MutableStateFlow(ReceiveSessionState())
    val receiveSession: StateFlow<ReceiveSessionState> = _receiveSession.asStateFlow()

    private val _sendShareSession = MutableStateFlow(SendShareSessionState())
    val sendShareSession: StateFlow<SendShareSessionState> = _sendShareSession.asStateFlow()

    private val _storageSummary = MutableStateFlow(StorageSummary())
    val storageSummary: StateFlow<StorageSummary> = _storageSummary.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<ReceivedFileRecord>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFileRecord>> = _receivedFiles.asStateFlow()

    private val _manualPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val manualPeers: StateFlow<List<DiscoveredPeer>> = _manualPeers.asStateFlow()

    val nsdPeers: StateFlow<List<DiscoveredPeer>> = nsdManager.peers
    val isNsdDiscovering: StateFlow<Boolean> = nsdManager.isDiscovering
    val wifiDirectState: StateFlow<WifiDirectState> = wifiP2pController.state
    val bluetoothState: StateFlow<BluetoothState> = bluetoothController.state

    private val pendingConsents = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val sendQueue = Channel<QueuedSendJob>(Channel.UNLIMITED)
    private var expiryJob: Job? = null

    init {
        scope.launch {
            settingsStore.ensureDeviceId()
            refreshStorageAndFiles()
            startQueueWorker()
        }
        wifiP2pController.initialize()
        bluetoothController.refreshPairedDevices()
    }

    private fun startQueueWorker() {
        scope.launch {
            for (job in sendQueue) {
                executeSendBatch(job)
            }
        }
    }

    suspend fun refreshStorageAndFiles() {
        val customTree = settings.value.customSaveTreeUri
        _storageSummary.value = fileHelper.getStorageSummary(customTree)
        _receivedFiles.value = fileHelper.listReceivedFiles(customTree)
    }

    fun startDiscovery() {
        val id = settings.value.deviceId
        nsdManager.startDiscovery(id)
        if (wifiP2pController.hasRequiredPermission()) {
            wifiP2pController.discoverPeers()
        }
        bluetoothController.refreshPairedDevices()
    }

    fun stopDiscovery() {
        nsdManager.stopDiscovery()
        bluetoothController.stopScan()
    }

    suspend fun addManualPeer(host: String, port: Int): DiscoveredPeer? {
        val info = httpClient.fetchPeerInfo(host, port)
        val peer = DiscoveredPeer(
            id = info?.deviceId ?: "manual-$host:$port",
            name = info?.deviceName ?: host,
            address = host,
            port = port,
            platform = info?.platform ?: "LAN / Web",
            method = ConnectionMethod.LAN_HTTP,
            details = "$host:$port"
        )
        _manualPeers.update { list ->
            list.filterNot { it.address == host && it.port == port } + peer
        }
        return peer
    }

    fun startReceiveSession() {
        scope.launch {
            val currentSettings = settingsStore.settingsFlow.first()
            val deviceId = currentSettings.deviceId.ifBlank { settingsStore.ensureDeviceId() }
            val token = NetworkUtils.generateSessionToken()
            val ttl = currentSettings.sessionTimeoutMinutes.coerceIn(5, 60)
            stopSendShareSession()

            _receiveSession.update { it.copy(isStarting = true, errorMessage = null) }
            try {
                val port = httpServer.start(
                    preferredPort = 8765,
                    mode = "receive",
                    token = token,
                    ttlMinutes = ttl,
                    listener = serverListener
                )
                val preferredIp = wifiP2pController.state.value.groupOwnerAddress
                val ip = NetworkUtils.getLocalIpAddress(preferredIp)
                val url = NetworkUtils.buildSessionUrl(
                    host = ip,
                    port = port,
                    token = token,
                    deviceName = currentSettings.deviceName,
                    mode = "receive"
                )
                if (!currentSettings.hideDeviceWhenIdle) {
                    nsdManager.registerService(deviceId, currentSettings.deviceName, port)
                }
                _receiveSession.value = ReceiveSessionState(
                    isActive = true,
                    sessionToken = token,
                    hostIp = ip,
                    port = port,
                    shareUrl = url,
                    expiresAtMs = httpServer.expiresAtMs,
                    isStarting = false
                )
                startExpiryWatcher(httpServer.expiresAtMs)
            } catch (e: Exception) {
                _receiveSession.update {
                    it.copy(isActive = false, isStarting = false, errorMessage = e.message)
                }
            }
        }
    }

    fun stopReceiveSession() {
        expiryJob?.cancel()
        httpServer.stop()
        nsdManager.unregisterService()
        pendingConsents.values.forEach { it.complete(false) }
        pendingConsents.clear()
        _receiveSession.value = ReceiveSessionState(isActive = false)
    }

    fun startSendShareSession(files: List<SelectedFileItem>) {
        if (files.isEmpty()) return
        scope.launch {
            val currentSettings = settingsStore.settingsFlow.first()
            stopReceiveSession()
            val token = NetworkUtils.generateSessionToken()
            val ttl = currentSettings.sessionTimeoutMinutes.coerceIn(5, 60)

            val enriched = files.map { item ->
                if (item.sha256 != null) item else {
                    val hash = fileHelper.openInputStream(item.uri)?.use { HashUtils.computeSha256(it) }
                    item.copy(sha256 = hash)
                }
            }

            try {
                val port = httpServer.start(
                    preferredPort = 8765,
                    mode = "send",
                    token = token,
                    ttlMinutes = ttl,
                    filesToShare = enriched,
                    listener = serverListener
                )
                val preferredIp = wifiP2pController.state.value.groupOwnerAddress
                val ip = NetworkUtils.getLocalIpAddress(preferredIp)
                val url = NetworkUtils.buildSessionUrl(
                    host = ip,
                    port = port,
                    token = token,
                    deviceName = currentSettings.deviceName,
                    mode = "send"
                )
                _sendShareSession.value = SendShareSessionState(
                    isActive = true,
                    sessionToken = token,
                    hostIp = ip,
                    port = port,
                    shareUrl = url,
                    expiresAtMs = httpServer.expiresAtMs,
                    sharedFiles = enriched
                )
                startExpiryWatcher(httpServer.expiresAtMs)
            } catch (e: Exception) {
                _sendShareSession.update { it.copy(isActive = false, errorMessage = e.message) }
            }
        }
    }

    fun stopSendShareSession() {
        if (httpServer.sessionMode == "send") {
            expiryJob?.cancel()
            httpServer.stop()
        }
        _sendShareSession.value = SendShareSessionState(isActive = false)
    }

    private fun startExpiryWatcher(expiresAtMs: Long) {
        expiryJob?.cancel()
        expiryJob = scope.launch {
            while (isActive) {
                val remaining = expiresAtMs - System.currentTimeMillis()
                if (remaining <= 0L) {
                    if (_receiveSession.value.isActive) stopReceiveSession()
                    if (_sendShareSession.value.isActive) stopSendShareSession()
                    break
                }
                delay(minOf(remaining, 5000L))
            }
        }
    }

    fun enqueueSendToPeer(peer: DiscoveredPeer, files: List<SelectedFileItem>, sessionToken: String = "") {
        if (files.isEmpty()) return
        // Pre-populate queued tasks immediately for instant UI feedback
        val initialTasks = files.map { f ->
            ActiveTransferTask(
                id = "q-${f.id}",
                fileName = f.name,
                fileSizeBytes = f.sizeBytes,
                bytesTransferred = 0L,
                speedBps = 0L,
                remainingSeconds = 0L,
                direction = TransferDirection.SENT,
                peerName = peer.name,
                peerAddress = peer.address,
                method = peer.method,
                status = TransferStatus.QUEUED
            )
        }
        _activeTasks.update { current -> current + initialTasks }
        sendQueue.trySend(QueuedSendJob(peer, sessionToken, files))
    }

    private suspend fun executeSendBatch(job: QueuedSendJob) {
        val currentSettings = settingsStore.settingsFlow.first()
        val deviceId = currentSettings.deviceId.ifBlank { settingsStore.ensureDeviceId() }
        val deviceName = currentSettings.deviceName

        if (currentSettings.notificationsEnabled) {
            runCatching { TransferForegroundService.start(context) }
        }

        if (job.peer.method == ConnectionMethod.BLUETOOTH) {
            for (file in job.files) {
                val sha = file.sha256 ?: fileHelper.openInputStream(file.uri)?.use { HashUtils.computeSha256(it) } ?: ""
                removeQueuedPlaceholder(file.id)
                val result = bluetoothController.sendFileOverBluetooth(
                    peerAddress = job.peer.address,
                    peerName = job.peer.name,
                    file = file,
                    sha256 = sha,
                    localDeviceId = deviceId,
                    localDeviceName = deviceName,
                    onProgress = { upsertActiveTask(it) }
                )
                upsertActiveTask(result)
                recordTransferToDb(result, file.mimeType, result.localUri)
            }
            return
        }

        val offerId = UUID.randomUUID().toString()
        val enrichedFiles = job.files.map { f ->
            val sha = f.sha256 ?: fileHelper.openInputStream(f.uri)?.use { HashUtils.computeSha256(it) }
            f.copy(sha256 = sha)
        }

        // Mark waiting approval
        enrichedFiles.forEach { f ->
            upsertActiveTask(
                ActiveTransferTask(
                    id = "q-${f.id}",
                    fileName = f.name,
                    fileSizeBytes = f.sizeBytes,
                    bytesTransferred = 0L,
                    speedBps = 0L,
                    remainingSeconds = 0L,
                    direction = TransferDirection.SENT,
                    peerName = job.peer.name,
                    peerAddress = "${job.peer.address}:${job.peer.port}",
                    method = job.peer.method,
                    status = TransferStatus.AWAITING_APPROVAL,
                    sha256 = f.sha256
                )
            )
        }

        val approved = httpClient.requestOfferApproval(
            host = job.peer.address,
            port = job.peer.port,
            token = job.token,
            offerId = offerId,
            localDeviceId = deviceId,
            localDeviceName = deviceName,
            files = enrichedFiles
        )

        if (!approved) {
            enrichedFiles.forEach { f ->
                val cancelled = ActiveTransferTask(
                    id = "q-${f.id}",
                    fileName = f.name,
                    fileSizeBytes = f.sizeBytes,
                    bytesTransferred = 0L,
                    speedBps = 0L,
                    remainingSeconds = 0L,
                    direction = TransferDirection.SENT,
                    peerName = job.peer.name,
                    peerAddress = "${job.peer.address}:${job.peer.port}",
                    method = job.peer.method,
                    status = TransferStatus.CANCELLED,
                    errorMessage = "Rejected or unreachable peer"
                )
                upsertActiveTask(cancelled)
                recordTransferToDb(cancelled, f.mimeType, null)
            }
            return
        }

        for (file in enrichedFiles) {
            removeQueuedPlaceholder(file.id)
            val transferId = "tx-${file.id.take(12)}"
            val resumeOffset = httpClient.queryPartialOffset(
                host = job.peer.address,
                port = job.peer.port,
                token = job.token,
                transferId = transferId
            ).coerceIn(0L, file.sizeBytes)

            val result = httpClient.uploadFile(
                transferId = transferId,
                file = file,
                sha256 = file.sha256 ?: "",
                host = job.peer.address,
                port = job.peer.port,
                token = job.token,
                offerId = offerId,
                localDeviceId = deviceId,
                localDeviceName = deviceName,
                peerName = job.peer.name,
                method = job.peer.method,
                resumeOffset = resumeOffset,
                onProgress = { upsertActiveTask(it) }
            )
            upsertActiveTask(result)
            recordTransferToDb(result, file.mimeType, result.localUri)
        }
    }

    suspend fun connectAndDownloadFromQr(payload: QrConnectionPayload): Boolean {
        val status = httpClient.fetchSharedFilesStatus(payload.host, payload.port, payload.token) ?: return false
        val customTree = settings.value.customSaveTreeUri
        if (settings.value.notificationsEnabled) {
            runCatching { TransferForegroundService.start(context) }
        }
        var allOk = true
        for (f in status.files) {
            val result = httpClient.downloadSharedFile(
                host = payload.host,
                port = payload.port,
                token = payload.token,
                fileIndex = f.index,
                fileName = f.name,
                totalSize = f.sizeBytes,
                mimeType = f.mimeType,
                expectedSha256 = f.sha256,
                peerName = status.deviceName,
                customSaveTreeUri = customTree,
                onProgress = { upsertActiveTask(it) }
            )
            upsertActiveTask(result)
            recordTransferToDb(result, f.mimeType, result.localUri)
            if (result.status != TransferStatus.COMPLETED) allOk = false
        }
        refreshStorageAndFiles()
        return allOk
    }

    fun respondToPendingOffer(offerId: String, accept: Boolean, trustSender: Boolean = false) {
        val pending = _receiveSession.value.pendingOffer
        if (accept && trustSender && pending != null) {
            scope.launch {
                dao.insertTrustedDevice(
                    TrustedDeviceEntity(
                        deviceId = pending.senderDeviceId,
                        deviceName = pending.senderName,
                        lastAddress = pending.senderAddress
                    )
                )
            }
        }
        pendingConsents.remove(offerId)?.complete(accept)
        _receiveSession.update { it.copy(pendingOffer = null) }
    }

    fun cancelTransfer(taskId: String) {
        httpClient.cancelTransfer(taskId)
        httpServer.cancelTransfer(taskId)
        _activeTasks.update { list ->
            list.map {
                if (it.id == taskId && (it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.QUEUED)) {
                    it.copy(status = TransferStatus.CANCELLED)
                } else it
            }
        }
    }

    fun cancelAllActiveTransfers() {
        _activeTasks.value
            .filter { it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.QUEUED }
            .forEach { cancelTransfer(it.id) }
    }

    fun clearFinishedTasks() {
        _activeTasks.update { list ->
            list.filter {
                it.status == TransferStatus.IN_PROGRESS ||
                    it.status == TransferStatus.QUEUED ||
                    it.status == TransferStatus.AWAITING_APPROVAL
            }
        }
    }

    fun startBluetoothReceive() {
        bluetoothController.startListening(
            customSaveTreeUri = { settings.value.customSaveTreeUri },
            awaitConsent = { offer -> serverListener.awaitIncomingConsent(offer) },
            onTaskUpdate = { upsertActiveTask(it) },
            onTaskFinished = { task, uri -> serverListener.onTransferFinished(task, uri) }
        )
    }

    private fun removeQueuedPlaceholder(fileId: String) {
        _activeTasks.update { list -> list.filterNot { it.id == "q-$fileId" } }
    }

    private fun upsertActiveTask(task: ActiveTransferTask) {
        _activeTasks.update { list ->
            val idx = list.indexOfFirst { it.id == task.id }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, task) }
            } else {
                listOf(task) + list
            }
        }
        if (task.status == TransferStatus.IN_PROGRESS && settings.value.notificationsEnabled) {
            runCatching { TransferForegroundService.start(context) }
        }
    }

    private suspend fun recordTransferToDb(task: ActiveTransferTask, mimeType: String, localUri: String?) {
        val duration = (System.currentTimeMillis() - task.startedAtMs).coerceAtLeast(1L)
        dao.insertTransfer(
            TransferEntity(
                taskId = task.id,
                fileName = task.fileName,
                fileSizeBytes = task.fileSizeBytes,
                mimeType = mimeType,
                direction = task.direction.name,
                peerName = task.peerName,
                peerAddress = task.peerAddress,
                connectionMethod = task.method.name,
                status = task.status.name,
                sha256 = task.sha256,
                integrityVerified = task.integrityVerified,
                localUri = localUri,
                bytesTransferred = task.bytesTransferred,
                durationMs = duration,
                averageSpeedBps = task.speedBps,
                errorMessage = task.errorMessage
            )
        )
    }

    private val serverListener = object : DropLinkHttpServer.Listener {
        override fun getDeviceInfo(mode: String): PeerDeviceInfo {
            val s = settings.value
            return PeerDeviceInfo(
                deviceId = s.deviceId,
                deviceName = s.deviceName,
                platform = "Android",
                mode = mode,
                requiresApproval = s.requireApproval
            )
        }

        override fun getCustomSaveTreeUri(): String? = settings.value.customSaveTreeUri

        override suspend fun awaitIncomingConsent(offer: PendingIncomingOffer): Boolean {
            val s = settings.value
            if (!s.requireApproval) return true
            if (s.autoAcceptTrusted && dao.isDeviceTrusted(offer.senderDeviceId)) return true

            val deferred = CompletableDeferred<Boolean>()
            pendingConsents[offer.offerId] = deferred
            _receiveSession.update { it.copy(pendingOffer = offer) }

            val result = withTimeoutOrNull(60_000L) { deferred.await() } ?: false
            pendingConsents.remove(offer.offerId)
            _receiveSession.update {
                if (it.pendingOffer?.offerId == offer.offerId) it.copy(pendingOffer = null) else it
            }
            return result
        }

        override fun onTaskUpdated(task: ActiveTransferTask) {
            upsertActiveTask(task)
        }

        override suspend fun onTransferFinished(task: ActiveTransferTask, localUri: String?) {
            val mime = "application/octet-stream"
            recordTransferToDb(task, mime, localUri)
            if (task.status == TransferStatus.COMPLETED) {
                if (task.direction == TransferDirection.RECEIVED) {
                    _receiveSession.update {
                        it.copy(
                            receivedCount = it.receivedCount + 1,
                            receivedBytes = it.receivedBytes + task.bytesTransferred
                        )
                    }
                    refreshStorageAndFiles()
                } else {
                    _sendShareSession.update { it.copy(downloadCount = it.downloadCount + 1) }
                }
            }
        }

        override fun onPeerInteraction() {
            _receiveSession.update {
                if (it.isActive) it.copy(connectedBrowserOrPeers = it.connectedBrowserOrPeers + 1) else it
            }
        }
    }

    fun release() {
        httpServer.stop()
        nsdManager.unregisterService()
        nsdManager.stopDiscovery()
        wifiP2pController.release()
        bluetoothController.release()
    }
}

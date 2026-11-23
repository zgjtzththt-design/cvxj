package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DropLinkApp
import com.example.data.local.TransferEntity
import com.example.data.local.TrustedDeviceEntity
import com.example.data.local.UserSettings
import com.example.i18n.AppLanguage
import com.example.model.ActiveTransferTask
import com.example.model.BluetoothState
import com.example.model.DiscoveredPeer
import com.example.model.QrConnectionPayload
import com.example.model.ReceiveSessionState
import com.example.model.ReceivedFileRecord
import com.example.model.SelectedFileItem
import com.example.model.SendShareSessionState
import com.example.model.StorageSummary
import com.example.model.WifiDirectState
import com.example.network.NetworkUtils
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = (application as DropLinkApp).coordinator

    val settings: StateFlow<UserSettings> = coordinator.settings
    val activeTasks: StateFlow<List<ActiveTransferTask>> = coordinator.activeTasks
    val receiveSession: StateFlow<ReceiveSessionState> = coordinator.receiveSession
    val sendShareSession: StateFlow<SendShareSessionState> = coordinator.sendShareSession
    val storageSummary: StateFlow<StorageSummary> = coordinator.storageSummary
    val receivedFiles: StateFlow<List<ReceivedFileRecord>> = coordinator.receivedFiles
    val isNsdDiscovering: StateFlow<Boolean> = coordinator.isNsdDiscovering
    val wifiDirectState: StateFlow<WifiDirectState> = coordinator.wifiDirectState
    val bluetoothState: StateFlow<BluetoothState> = coordinator.bluetoothState

    val allPeers: StateFlow<List<DiscoveredPeer>> = combine(
        coordinator.nsdPeers,
        coordinator.manualPeers,
        coordinator.wifiDirectState,
        coordinator.bluetoothState
    ) { nsd, manual, wfd, bt ->
        (nsd + manual + wfd.peers + bt.pairedDevices + bt.discoveredDevices)
            .distinctBy { "${it.method}-${it.address}-${it.port}" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<TransferEntity>> = coordinator.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransfers: StateFlow<List<TransferEntity>> = coordinator.recentTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trustedDevices: StateFlow<List<TrustedDeviceEntity>> = coordinator.trustedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFilesForSend = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    val selectedFilesForSend: StateFlow<List<SelectedFileItem>> = _selectedFilesForSend.asStateFlow()

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages = _uiMessages.asSharedFlow()

    fun addPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = coordinator.fileHelper.resolveSelectedFiles(uris)
            if (resolved.isNotEmpty()) {
                _selectedFilesForSend.update { current ->
                    val existingUris = current.map { it.uri.toString() }.toSet()
                    current + resolved.filterNot { it.uri.toString() in existingUris }
                }
            }
        }
    }

    fun addPickedFolderTree(treeUri: Uri) {
        viewModelScope.launch {
            val resolved = coordinator.fileHelper.resolveFolderTree(treeUri)
            if (resolved.isNotEmpty()) {
                _selectedFilesForSend.update { current ->
                    val existingUris = current.map { it.uri.toString() }.toSet()
                    current + resolved.filterNot { it.uri.toString() in existingUris }
                }
            } else {
                _uiMessages.tryEmit("المجلد المحدد فارغ أو تعذر الوصول إليه")
            }
        }
    }

    fun addReceivedFilesToSend(records: List<ReceivedFileRecord>) {
        val items = records.map {
            SelectedFileItem(
                id = it.id,
                uri = it.uri,
                name = it.name,
                sizeBytes = it.sizeBytes,
                mimeType = it.mimeType,
                category = it.category
            )
        }
        _selectedFilesForSend.value = items
    }

    fun removeSelectedFile(fileId: String) {
        _selectedFilesForSend.update { list -> list.filterNot { it.id == fileId } }
    }

    fun clearSelectedFiles() {
        _selectedFilesForSend.value = emptyList()
    }

    fun startDiscovery() = coordinator.startDiscovery()
    fun stopDiscovery() = coordinator.stopDiscovery()

    fun connectManualPeer(input: String, onSuccess: (DiscoveredPeer) -> Unit = {}) {
        val trimmed = input.trim()
        val parsed = NetworkUtils.parseQrPayload(trimmed)
        val host = parsed?.host ?: trimmed.substringBefore(":").trim()
        val port = parsed?.port ?: trimmed.substringAfter(":", "8765").toIntOrNull() ?: 8765
        if (host.isBlank()) return
        viewModelScope.launch {
            val peer = coordinator.addManualPeer(host, port)
            if (peer != null) {
                onSuccess(peer)
            }
        }
    }

    fun sendSelectedFilesToPeer(peer: DiscoveredPeer, token: String = "") {
        val files = _selectedFilesForSend.value
        if (files.isEmpty()) return
        coordinator.enqueueSendToPeer(peer, files, token)
    }

    fun startBrowserShareSession() {
        val files = _selectedFilesForSend.value
        if (files.isEmpty()) return
        coordinator.startSendShareSession(files)
    }

    fun stopBrowserShareSession() = coordinator.stopSendShareSession()

    fun startReceiveSession() = coordinator.startReceiveSession()
    fun stopReceiveSession() = coordinator.stopReceiveSession()

    fun respondToPendingOffer(offerId: String, accept: Boolean, trustSender: Boolean = false) {
        coordinator.respondToPendingOffer(offerId, accept, trustSender)
    }

    fun cancelTransfer(taskId: String) = coordinator.cancelTransfer(taskId)
    fun clearFinishedTasks() = coordinator.clearFinishedTasks()

    fun handleQrPayload(raw: String, onNavigateToSend: () -> Unit, onNavigateToReceiveOrHome: () -> Unit): Boolean {
        val payload = NetworkUtils.parseQrPayload(raw) ?: return false
        if (payload.mode == "send") {
            viewModelScope.launch {
                val ok = coordinator.connectAndDownloadFromQr(payload)
                _uiMessages.tryEmit(if (ok) "تم تنزيل الملفات بنجاح" else "تعذر تنزيل بعض الملفات")
            }
            onNavigateToReceiveOrHome()
            return true
        } else {
            val peer = DiscoveredPeer(
                id = "qr-${payload.host}:${payload.port}",
                name = payload.name,
                address = payload.host,
                port = payload.port,
                platform = "QR Peer",
                method = com.example.model.ConnectionMethod.LAN_HTTP,
                details = "${payload.host}:${payload.port}"
            )
            viewModelScope.launch {
                coordinator.addManualPeer(payload.host, payload.port)
                if (_selectedFilesForSend.value.isNotEmpty()) {
                    coordinator.enqueueSendToPeer(peer, _selectedFilesForSend.value, payload.token)
                    onNavigateToSend()
                } else {
                    _uiMessages.tryEmit("تم حفظ الجهاز: ${payload.name}. اختر ملفات لإرسالها.")
                    onNavigateToReceiveOrHome()
                }
            }
            return true
        }
    }

    fun connectWifiDirectPeer(address: String) = coordinator.wifiP2pController.connectToPeer(address)
    fun createWifiDirectGroup() = coordinator.wifiP2pController.createReceiveGroup()
    fun removeWifiDirectGroup() = coordinator.wifiP2pController.removeGroup()
    fun startBluetoothScan() = coordinator.bluetoothController.startScan()
    fun startBluetoothReceive() = coordinator.startBluetoothReceive()
    fun stopBluetoothReceive() = coordinator.bluetoothController.stopListening()

    fun refreshFilesAndStorage() {
        viewModelScope.launch { coordinator.refreshStorageAndFiles() }
    }

    fun openFile(uriString: String?, mimeType: String) {
        if (uriString.isNullOrBlank()) return
        val ok = coordinator.fileHelper.openFile(Uri.parse(uriString), mimeType)
        if (!ok) _uiMessages.tryEmit("لا يوجد تطبيق متاح لفتح هذا النوع من الملفات")
    }

    fun shareFile(uriString: String?, mimeType: String) {
        if (uriString.isNullOrBlank()) return
        coordinator.fileHelper.shareFile(Uri.parse(uriString), mimeType)
    }

    fun openSaveFolder() {
        val ok = coordinator.fileHelper.openSaveFolder(settings.value.customSaveTreeUri)
        if (!ok) _uiMessages.tryEmit("تعذر فتح مدير الملفات تلقائيًا")
    }

    fun deleteReceivedFile(record: ReceivedFileRecord) {
        viewModelScope.launch {
            coordinator.fileHelper.deleteFile(record.uri)
            coordinator.refreshStorageAndFiles()
        }
    }

    fun observeTransferById(id: Long) = coordinator.dao.observeTransferById(id)

    fun deleteTransferRecord(id: Long) {
        viewModelScope.launch { coordinator.dao.deleteTransferById(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { coordinator.dao.clearAllTransfers() }
    }

    fun revokeTrustedDevice(deviceId: String) {
        viewModelScope.launch { coordinator.dao.removeTrustedDevice(deviceId) }
    }

    // Settings updates
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { coordinator.settingsStore.setThemeMode(mode) } }
    fun setLanguage(lang: AppLanguage) { viewModelScope.launch { coordinator.settingsStore.setLanguage(lang) } }
    fun setDeviceName(name: String) { viewModelScope.launch { coordinator.settingsStore.setDeviceName(name) } }
    fun setRequireApproval(req: Boolean) { viewModelScope.launch { coordinator.settingsStore.setRequireApproval(req) } }
    fun setAutoAcceptTrusted(auto: Boolean) { viewModelScope.launch { coordinator.settingsStore.setAutoAcceptTrusted(auto) } }
    fun setNotificationsEnabled(enabled: Boolean) { viewModelScope.launch { coordinator.settingsStore.setNotificationsEnabled(enabled) } }
    fun setSessionTimeout(minutes: Int) { viewModelScope.launch { coordinator.settingsStore.setSessionTimeoutMinutes(minutes) } }
    fun setHideWhenIdle(hide: Boolean) { viewModelScope.launch { coordinator.settingsStore.setHideDeviceWhenIdle(hide) } }

    fun setCustomSaveFolder(treeUri: Uri?) {
        viewModelScope.launch {
            if (treeUri == null) {
                coordinator.settingsStore.setCustomSaveFolder(null, null)
            } else {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                val label = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Custom Folder"
                coordinator.settingsStore.setCustomSaveFolder(treeUri.toString(), label)
            }
            coordinator.refreshStorageAndFiles()
        }
    }
}

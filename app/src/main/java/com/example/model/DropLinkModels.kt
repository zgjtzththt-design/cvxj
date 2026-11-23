package com.example.model

import android.net.Uri
import kotlinx.serialization.Serializable

enum class FileCategory {
    IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER
}

enum class ConnectionMethod {
    LAN_HTTP,
    WEB_BROWSER,
    WIFI_DIRECT,
    BLUETOOTH,
    INTERNET_FUTURE
}

enum class TransferDirection {
    SENT, RECEIVED
}

enum class TransferStatus {
    QUEUED,
    AWAITING_APPROVAL,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class SelectedFileItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: FileCategory,
    val sha256: String? = null
)

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val platform: String = "Android",
    val method: ConnectionMethod,
    val isTrusted: Boolean = false,
    val isConnected: Boolean = false,
    val details: String = "",
    val lastSeenMs: Long = System.currentTimeMillis()
)

@Serializable
data class OfferedFileMetadata(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String? = null
)

@Serializable
data class PeerDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: String = "Android",
    val version: String = "1.0",
    val mode: String = "receive",
    val requiresApproval: Boolean = true
)

@Serializable
data class OfferRequestPayload(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val files: List<OfferedFileMetadata>
)

@Serializable
data class OfferResponsePayload(
    val accepted: Boolean,
    val reason: String? = null
)

@Serializable
data class WebPortalStatusDto(
    val deviceName: String,
    val mode: String,
    val expiresAtMs: Long,
    val files: List<WebSharedFileDto> = emptyList()
)

@Serializable
data class WebSharedFileDto(
    val index: Int,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val sha256: String? = null
)

data class PendingIncomingOffer(
    val offerId: String,
    val senderDeviceId: String,
    val senderName: String,
    val senderAddress: String,
    val method: ConnectionMethod,
    val files: List<OfferedFileMetadata>,
    val receivedAtMs: Long = System.currentTimeMillis()
) {
    val totalSizeBytes: Long get() = files.sumOf { it.sizeBytes }
}

data class ActiveTransferTask(
    val id: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val bytesTransferred: Long,
    val speedBps: Long,
    val remainingSeconds: Long,
    val direction: TransferDirection,
    val peerName: String,
    val peerAddress: String,
    val method: ConnectionMethod,
    val status: TransferStatus,
    val sha256: String? = null,
    val integrityVerified: Boolean = false,
    val errorMessage: String? = null,
    val localUri: String? = null,
    val canResume: Boolean = false,
    val startedAtMs: Long = System.currentTimeMillis()
) {
    val progress: Float
        get() = if (fileSizeBytes > 0L) {
            (bytesTransferred.toFloat() / fileSizeBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class ReceiveSessionState(
    val isActive: Boolean = false,
    val sessionToken: String = "",
    val hostIp: String = "",
    val port: Int = 8765,
    val shareUrl: String = "",
    val expiresAtMs: Long = 0L,
    val isStarting: Boolean = false,
    val errorMessage: String? = null,
    val receivedCount: Int = 0,
    val receivedBytes: Long = 0L,
    val connectedBrowserOrPeers: Int = 0,
    val pendingOffer: PendingIncomingOffer? = null
)

data class SendShareSessionState(
    val isActive: Boolean = false,
    val sessionToken: String = "",
    val hostIp: String = "",
    val port: Int = 8765,
    val shareUrl: String = "",
    val expiresAtMs: Long = 0L,
    val sharedFiles: List<SelectedFileItem> = emptyList(),
    val downloadCount: Int = 0,
    val errorMessage: String? = null
)

data class StorageSummary(
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val dropLinkBytes: Long = 0L,
    val dropLinkFileCount: Int = 0
)

data class ReceivedFileRecord(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val uri: Uri,
    val lastModifiedMs: Long,
    val category: FileCategory,
    val isReadable: Boolean = true
)

data class QrConnectionPayload(
    val host: String,
    val port: Int,
    val token: String,
    val name: String,
    val mode: String = "receive"
)

data class WifiDirectState(
    val isSupported: Boolean = true,
    val isEnabled: Boolean = false,
    val isDiscovering: Boolean = false,
    val isGroupFormed: Boolean = false,
    val isGroupOwner: Boolean = false,
    val groupOwnerAddress: String? = null,
    val statusMessage: String? = null,
    val peers: List<DiscoveredPeer> = emptyList()
)

data class BluetoothState(
    val isSupported: Boolean = false,
    val isEnabled: Boolean = false,
    val isListening: Boolean = false,
    val isScanning: Boolean = false,
    val pairedDevices: List<DiscoveredPeer> = emptyList(),
    val discoveredDevices: List<DiscoveredPeer> = emptyList(),
    val statusMessage: String? = null
)

package com.example.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import com.example.model.WifiDirectState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class WifiP2pController(private val context: Context) {

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false

    private val _state = MutableStateFlow(
        WifiDirectState(isSupported = manager != null)
    )
    val state: StateFlow<WifiDirectState> = _state.asStateFlow()

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val p2pState = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    val enabled = p2pState == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    _state.update { it.copy(isEnabled = enabled) }
                }

                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeersInternal()
                }

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val ch = channel ?: return
                    manager?.requestConnectionInfo(ch) { info ->
                        if (info != null) {
                            _state.update {
                                it.copy(
                                    isGroupFormed = info.groupFormed,
                                    isGroupOwner = info.isGroupOwner,
                                    groupOwnerAddress = info.groupOwnerAddress?.hostAddress
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun hasRequiredPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun initialize() {
        val mgr = manager ?: run {
            _state.update { it.copy(isSupported = false, statusMessage = "Wi-Fi Direct unsupported") }
            return
        }
        if (channel == null) {
            channel = mgr.initialize(context, Looper.getMainLooper(), null)
        }
        if (!receiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    intentFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                receiverRegistered = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        initialize()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermission()) {
            _state.update { it.copy(statusMessage = "Missing Nearby Wi-Fi permission") }
            return
        }
        _state.update { it.copy(isDiscovering = true, statusMessage = null) }
        mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                requestPeersInternal()
            }

            override fun onFailure(reason: Int) {
                _state.update {
                    it.copy(
                        isDiscovering = false,
                        statusMessage = "Wi-Fi Direct discovery failed (code $reason)"
                    )
                }
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun requestPeersInternal() {
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermission()) return
        mgr.requestPeers(ch) { peerList ->
            val mapped = peerList.deviceList.map { dev -> dev.toDiscoveredPeer() }
            _state.update { it.copy(peers = mapped, isDiscovering = false) }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(deviceAddress: String, port: Int = 8765) {
        initialize()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermission()) return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.update { it.copy(statusMessage = "Connecting to $deviceAddress...") }
            }

            override fun onFailure(reason: Int) {
                _state.update { it.copy(statusMessage = "Connect failed (code $reason)") }
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun createReceiveGroup() {
        initialize()
        val mgr = manager ?: return
        val ch = channel ?: return
        if (!hasRequiredPermission()) return
        mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                _state.update { it.copy(statusMessage = "Wi-Fi Direct Group created") }
            }

            override fun onFailure(reason: Int) {
                _state.update { it.copy(statusMessage = "Group creation failed (code $reason)") }
            }
        })
    }

    fun removeGroup() {
        val mgr = manager ?: return
        val ch = channel ?: return
        mgr.removeGroup(ch, null)
        _state.update { it.copy(isGroupFormed = false, isGroupOwner = false, groupOwnerAddress = null) }
    }

    fun release() {
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    private fun WifiP2pDevice.toDiscoveredPeer(): DiscoveredPeer {
        val statusText = when (status) {
            WifiP2pDevice.CONNECTED -> "Connected"
            WifiP2pDevice.INVITED -> "Invited"
            WifiP2pDevice.AVAILABLE -> "Available"
            else -> "Unavailable"
        }
        return DiscoveredPeer(
            id = "wfd-$deviceAddress",
            name = deviceName.ifBlank { deviceAddress },
            address = deviceAddress,
            port = 8765,
            platform = "Wi-Fi Direct",
            method = ConnectionMethod.WIFI_DIRECT,
            isConnected = status == WifiP2pDevice.CONNECTED,
            details = "$statusText • $deviceAddress"
        )
    }
}

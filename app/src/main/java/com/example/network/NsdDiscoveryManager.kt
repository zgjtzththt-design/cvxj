package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class NsdDiscoveryManager(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_droplink._tcp."
        const val SERVICE_NAME_PREFIX = "DropLink-"
    }

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null
    private var localDeviceId: String = ""

    private val peerMap = ConcurrentHashMap<String, DiscoveredPeer>()

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    fun registerService(deviceId: String, deviceName: String, port: Int) {
        val manager = nsdManager ?: return
        unregisterService()
        localDeviceId = deviceId
        val shortId = deviceId.take(6).ifEmpty { "node" }
        val cleanName = deviceName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME_PREFIX$cleanName-$shortId"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("name", deviceName)
            setAttribute("platform", "Android")
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
                _isRegistered.value = true
                _statusError.value = null
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isRegistered.value = false
                _statusError.value = "NSD registration failed ($errorCode)"
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                _isRegistered.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isRegistered.value = false
            }
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _statusError.value = it.message
        }
    }

    fun unregisterService() {
        val manager = nsdManager ?: return
        registrationListener?.let { listener ->
            runCatching { manager.unregisterService(listener) }
        }
        registrationListener = null
        registeredServiceName = null
        _isRegistered.value = false
    }

    fun startDiscovery(myDeviceId: String) {
        val manager = nsdManager ?: run {
            _statusError.value = "NSD service unavailable"
            return
        }
        stopDiscovery()
        localDeviceId = myDeviceId
        _statusError.value = null

        runCatching {
            multicastLock = wifiManager?.createMulticastLock("droplink_nsd_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isDiscovering.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_droplink")) return
                if (service.serviceName == registeredServiceName) return
                resolvePeer(manager, service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                peerMap.remove(service.serviceName)
                emitPeers()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isDiscovering.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
                _statusError.value = "Discovery failed ($errorCode)"
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                _isDiscovering.value = false
            }
        }

        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            _isDiscovering.value = false
            _statusError.value = it.message
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePeer(manager: NsdManager, service: NsdServiceInfo) {
        runCatching {
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    // Ignore transient NSD resolve collision
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress ?: return
                    val port = serviceInfo.port
                    val attrs = serviceInfo.attributes
                    val peerId = attrs["id"]?.decodeToString() ?: serviceInfo.serviceName
                    if (peerId == localDeviceId) return
                    val displayName = attrs["name"]?.decodeToString()
                        ?: serviceInfo.serviceName.removePrefix(SERVICE_NAME_PREFIX)
                    val platform = attrs["platform"]?.decodeToString() ?: "Device"

                    peerMap[serviceInfo.serviceName] = DiscoveredPeer(
                        id = peerId,
                        name = displayName,
                        address = host,
                        port = port,
                        platform = platform,
                        method = ConnectionMethod.LAN_HTTP,
                        details = "$host:$port",
                        lastSeenMs = System.currentTimeMillis()
                    )
                    emitPeers()
                }
            })
        }
    }

    fun stopDiscovery() {
        val manager = nsdManager
        discoveryListener?.let { listener ->
            if (manager != null) {
                runCatching { manager.stopServiceDiscovery(listener) }
            }
        }
        discoveryListener = null
        _isDiscovering.value = false
        runCatching {
            if (multicastLock?.isHeld == true) multicastLock?.release()
        }
        multicastLock = null
    }

    fun clearPeers() {
        peerMap.clear()
        emitPeers()
    }

    private fun emitPeers() {
        _peers.value = peerMap.values.sortedByDescending { it.lastSeenMs }
    }
}

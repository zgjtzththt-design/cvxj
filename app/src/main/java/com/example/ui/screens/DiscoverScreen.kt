package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.i18n.LocalAppStrings
import com.example.model.BluetoothState
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import com.example.model.WifiDirectState
import com.example.ui.components.DropLinkCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PeerDeviceCard
import com.example.ui.components.PermissionRationaleCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors

@Composable
fun DiscoverScreen(
    peers: List<DiscoveredPeer>,
    isNsdDiscovering: Boolean,
    wifiDirectState: WifiDirectState,
    bluetoothState: BluetoothState,
    onRefreshDiscovery: () -> Unit,
    onConnectManual: (String) -> Unit,
    onSelectPeerToSend: (DiscoveredPeer) -> Unit,
    onConnectWifiDirect: (String) -> Unit,
    onCreateWifiDirectGroup: () -> Unit,
    onStartBluetoothScan: () -> Unit,
    onStartBluetoothListen: () -> Unit,
    onOpenQrScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val context = LocalContext.current

    var selectedMethod by remember { mutableStateOf(ConnectionMethod.LAN_HTTP) }
    var manualAddress by remember { mutableStateOf("") }

    val wifiPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.NEARBY_WIFI_DEVICES
    } else {
        Manifest.permission.ACCESS_FINE_LOCATION
    }
    var hasWifiPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, wifiPerm) == PackageManager.PERMISSION_GRANTED
        )
    }
    val wifiPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasWifiPerm = granted
        if (granted) onRefreshDiscovery()
    }

    val btPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    var hasBtPerms by remember {
        mutableStateOf(
            btPerms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val btPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { map ->
        hasBtPerms = map.values.all { it }
        if (hasBtPerms) onStartBluetoothScan()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = s.discoverTitle,
                    style = MaterialTheme.typography.headlineMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = s.qrQuickScan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.qrQuickScan)
                    }
                    FilledTonalButton(
                        onClick = onRefreshDiscovery,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("discover_refresh_button")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = s.scanNow, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Method Tabs
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf(
                    Triple(ConnectionMethod.LAN_HTTP, s.methodLan, Icons.Rounded.Wifi),
                    Triple(ConnectionMethod.WIFI_DIRECT, s.methodWifiDirect, Icons.Rounded.WifiTethering),
                    Triple(ConnectionMethod.BLUETOOTH, s.methodBluetooth, Icons.Rounded.Bluetooth),
                    Triple(ConnectionMethod.INTERNET_FUTURE, s.methodInternet, Icons.Rounded.Public)
                )
                items(tabs) { (method, label, icon) ->
                    FilterChip(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("discover_tab_${method.name}")
                    )
                }
            }
        }

        // Method-specific controls & permissions
        when (selectedMethod) {
            ConnectionMethod.LAN_HTTP -> {
                item {
                    DropLinkCard {
                        Text(text = s.manualConnectTitle, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = manualAddress,
                                onValueChange = { manualAddress = it },
                                placeholder = { Text(s.enterIpPortHint) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_ip_input")
                            )
                            Button(
                                onClick = {
                                    if (manualAddress.isNotBlank()) {
                                        onConnectManual(manualAddress)
                                        manualAddress = ""
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("manual_connect_button")
                            ) {
                                Text(s.connect)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isNsdDiscovering) "mDNS (_droplink._tcp) • ${s.scanning}" else "mDNS (_droplink._tcp)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.cyan
                        )
                    }
                }
            }

            ConnectionMethod.WIFI_DIRECT -> {
                if (!wifiDirectState.isSupported) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Rounded.WifiTethering,
                            title = s.methodWifiDirect,
                            body = s.hardwareUnsupported
                        )
                    }
                } else if (!hasWifiPerm) {
                    item {
                        PermissionRationaleCard(
                            title = s.permissionRequiredTitle,
                            description = s.permissionWifiDesc,
                            grantLabel = s.grantPermission,
                            onGrant = { wifiPermLauncher.launch(wifiPerm) }
                        )
                    }
                } else {
                    item {
                        DropLinkCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.wifiDirectGroupOwner, style = MaterialTheme.typography.titleSmall)
                                    wifiDirectState.groupOwnerAddress?.let { ip ->
                                        Text(
                                            text = "Group IP: $ip",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                            color = ext.cyan
                                        )
                                    }
                                    wifiDirectState.statusMessage?.let { msg ->
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = onCreateWifiDirectGroup,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(s.startWifiDirectGroup)
                                }
                            }
                        }
                    }
                }
            }

            ConnectionMethod.BLUETOOTH -> {
                if (!bluetoothState.isSupported) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Rounded.Bluetooth,
                            title = s.methodBluetooth,
                            body = s.hardwareUnsupported
                        )
                    }
                } else if (!hasBtPerms) {
                    item {
                        PermissionRationaleCard(
                            title = s.permissionRequiredTitle,
                            description = s.permissionBluetoothDesc,
                            grantLabel = s.grantPermission,
                            onGrant = { btPermLauncher.launch(btPerms) }
                        )
                    }
                } else {
                    item {
                        DropLinkCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onStartBluetoothScan,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(if (bluetoothState.isScanning) s.scanning else s.scanNow)
                                }
                                FilledTonalButton(
                                    onClick = onStartBluetoothListen,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                ) {
                                    Text(if (bluetoothState.isListening) s.bluetoothListening else s.startBluetoothListen)
                                }
                            }
                        }
                    }
                }
            }

            ConnectionMethod.INTERNET_FUTURE -> {
                item {
                    DropLinkCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Public, contentDescription = s.methodInternet, tint = ext.warning)
                            Text(s.methodInternet, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = ext.warningContainer
                            ) {
                                Text(
                                    text = s.futureFeatureBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.warning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = s.internetFutureDesc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            ConnectionMethod.WEB_BROWSER -> Unit
        }

        // Filtered Peers List
        if (selectedMethod != ConnectionMethod.INTERNET_FUTURE) {
            val filteredPeers = peers.filter { it.method == selectedMethod }
            if (filteredPeers.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Rounded.Devices,
                        title = s.noNearbyDevicesTitle,
                        body = s.noNearbyDevicesBody
                    )
                }
            } else {
                items(filteredPeers, key = { "${it.method}-${it.id}-${it.address}" }) { peer ->
                    PeerDeviceCard(
                        peer = peer,
                        actionLabel = if (peer.method == ConnectionMethod.WIFI_DIRECT && !peer.isConnected) s.connect else s.send,
                        onAction = {
                            if (peer.method == ConnectionMethod.WIFI_DIRECT && !peer.isConnected) {
                                onConnectWifiDirect(peer.address)
                            } else {
                                onSelectPeerToSend(peer)
                            }
                        }
                    )
                }
            }
        }
    }
}

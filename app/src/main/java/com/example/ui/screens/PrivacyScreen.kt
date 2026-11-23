package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.TrustedDeviceEntity
import com.example.data.local.UserSettings
import com.example.i18n.LocalAppStrings
import com.example.ui.components.DropLinkCard
import com.example.ui.theme.LocalDropLinkColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    settings: UserSettings,
    trustedDevices: List<TrustedDeviceEntity>,
    onBack: () -> Unit,
    onSessionTimeoutChange: (Int) -> Unit,
    onHideWhenIdleChange: (Boolean) -> Unit,
    onRevokeTrustedDevice: (String) -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.privacyTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DropLinkCard(elevated = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = s.localOnlyTitle, tint = ext.success)
                    Spacer(Modifier.width(10.dp))
                    Text(s.localOnlyTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = s.localOnlyDesc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropLinkCard {
                Text(s.ephemeralSessionsLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { mins ->
                        FilterChip(
                            selected = settings.sessionTimeoutMinutes == mins,
                            onClick = { onSessionTimeoutChange(mins) },
                            label = { Text("$mins ${s.sessionTimeoutMinutes}") }
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                SettingToggleRow(
                    title = s.hideDeviceWhenIdleLabel,
                    subtitle = s.hideDeviceWhenIdleDesc,
                    checked = settings.hideDeviceWhenIdle,
                    onCheckedChange = onHideWhenIdleChange
                )
            }

            DropLinkCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shield, contentDescription = s.trustedDevicesTitle, tint = ext.cyan)
                    Spacer(Modifier.width(8.dp))
                    Text(s.trustedDevicesTitle, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                if (trustedDevices.isEmpty()) {
                    Text(
                        text = s.noTrustedDevices,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        trustedDevices.forEach { dev ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        dev.lastAddress,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onRevokeTrustedDevice(dev.deviceId) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text(s.revokeTrust)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

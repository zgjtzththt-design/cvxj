package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.TransferEntity
import com.example.i18n.LocalAppStrings
import com.example.model.TransferDirection
import com.example.network.HashUtils
import com.example.ui.components.DropLinkCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferDetailScreen(
    transfer: TransferEntity?,
    onBack: () -> Unit,
    onOpenFile: (String?, String) -> Unit,
    onShareFile: (String?, String) -> Unit,
    onDeleteRecord: (Long) -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.transferDetailsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (transfer == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(s.noRecentTransfersTitle, style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            DropLinkCard(elevated = true) {
                Text(text = transfer.fileName, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = HashUtils.formatBytes(transfer.fileSizeBytes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFamily),
                        color = ext.cyan
                    )
                    if (transfer.integrityVerified) {
                        Icon(Icons.Rounded.Verified, contentDescription = s.integrityVerified, tint = ext.success, modifier = Modifier.size(18.dp))
                        Text(s.integrityVerified, style = MaterialTheme.typography.labelSmall, color = ext.success)
                    }
                }
            }

            DropLinkCard {
                DetailRow(s.directionLabel, if (transfer.direction == TransferDirection.SENT.name) s.directionSent else s.directionReceived)
                DetailRow(s.peerDeviceLabel, "${transfer.peerName} (${transfer.peerAddress})")
                DetailRow(s.methodLabel, transfer.connectionMethod)
                DetailRow(s.statusLabel, transfer.status)
                DetailRow(s.speedAvgLabel, HashUtils.formatSpeed(transfer.averageSpeedBps))
                DetailRow(s.durationLabel, "${(transfer.durationMs / 1000.0).coerceAtLeast(0.1).let { String.format(Locale.US, "%.1fs", it) }}")
                DetailRow(s.dateLabel, dateFormatter.format(Date(transfer.timestamp)))
                if (!transfer.sha256.isNullOrBlank()) {
                    DetailRow(s.sha256Label, transfer.sha256, mono = true)
                }
                if (!transfer.errorMessage.isNullOrBlank()) {
                    DetailRow("Error", transfer.errorMessage)
                }
            }

            if (!transfer.localUri.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onOpenFile(transfer.localUri, transfer.mimeType) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = s.openFile, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.openFile)
                    }
                    FilledTonalButton(
                        onClick = { onShareFile(transfer.localUri, transfer.mimeType) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = s.shareFile, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(s.shareFile)
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onDeleteRecord(transfer.id)
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = s.deleteRecord, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.deleteRecord)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily) else MaterialTheme.typography.bodyMedium
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

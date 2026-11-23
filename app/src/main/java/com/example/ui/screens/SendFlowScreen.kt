package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalAppStrings
import com.example.model.ActiveTransferTask
import com.example.model.DiscoveredPeer
import com.example.model.SelectedFileItem
import com.example.model.SendShareSessionState
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.network.HashUtils
import com.example.ui.components.DropLinkCard
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.PeerDeviceCard
import com.example.ui.components.QrCodeDisplay
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendFlowScreen(
    selectedFiles: List<SelectedFileItem>,
    peers: List<DiscoveredPeer>,
    activeTasks: List<ActiveTransferTask>,
    shareSession: SendShareSessionState,
    onBack: () -> Unit,
    onAddMoreFiles: () -> Unit,
    onRemoveFile: (String) -> Unit,
    onClearFiles: () -> Unit,
    onStartBrowserShare: () -> Unit,
    onStopBrowserShare: () -> Unit,
    onSendToPeer: (DiscoveredPeer) -> Unit,
    onCancelTask: (String) -> Unit,
    onClearFinishedTasks: () -> Unit,
    onOpenQrScanner: () -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val clipboard = LocalClipboardManager.current

    var pendingConfirmPeer by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    val sendTasks = activeTasks.filter { it.direction == TransferDirection.SENT }
    val allCompleted = sendTasks.isNotEmpty() && sendTasks.all { it.status == TransferStatus.COMPLETED }
    val totalSize = selectedFiles.sumOf { it.sizeBytes }

    if (pendingConfirmPeer != null) {
        val peer = pendingConfirmPeer!!
        AlertDialog(
            onDismissRequest = { pendingConfirmPeer = null },
            title = { Text(s.confirmTransferTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${s.peerDeviceLabel}: ${peer.name} (${peer.address})")
                    Text("${s.selectedFilesCount}: ${selectedFiles.size} • ${HashUtils.formatBytes(totalSize)}")
                    Text(
                        text = s.confirmTransferDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSendToPeer(peer)
                        pendingConfirmPeer = null
                    },
                    modifier = Modifier.testTag("confirm_send_button")
                ) {
                    Text(s.startTransfer)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmPeer = null }) {
                    Text(s.cancelTransfer)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.sendFilesTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQrScanner, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = s.qrQuickScan)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Completion Banner
            if (allCompleted) {
                item {
                    DropLinkCard(elevated = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = s.transferCompletedTitle,
                                tint = ext.success,
                                modifier = Modifier.size(34.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.transferCompletedTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = s.transferCompletedDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(onClick = {
                                onClearFinishedTasks()
                                onBack()
                            }) {
                                Text(s.done)
                            }
                        }
                    }
                }
            }

            // Active / Recent Send Tasks
            if (sendTasks.isNotEmpty()) {
                item {
                    Text(s.transferProgressTitle, style = MaterialTheme.typography.titleMedium)
                }
                items(sendTasks, key = { it.id }) { task ->
                    TransferProgressCard(
                        task = task,
                        onCancel = { onCancelTask(task.id) }
                    )
                }
            }

            // Selected Files Summary
            item {
                DropLinkCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${s.selectedFilesCount}: ${selectedFiles.size}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${s.totalSize}: ${HashUtils.formatBytes(totalSize)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = ext.cyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onAddMoreFiles,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = s.addMoreFiles, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.addMoreFiles)
                            }
                            if (selectedFiles.isNotEmpty()) {
                                IconButton(
                                    onClick = onClearFiles,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = s.clearSelection)
                                }
                            }
                        }
                    }
                }
            }

            // Selected Files List
            items(selectedFiles, key = { it.id }) { item ->
                DropLinkCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileCategoryBadge(category = item.category)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${HashUtils.formatBytes(item.sizeBytes)} • ${item.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onRemoveFile(item.id) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove")
                        }
                    }
                }
            }

            if (selectedFiles.isNotEmpty()) {
                item {
                    Text(s.chooseDestination, style = MaterialTheme.typography.titleMedium)
                }

                // Browser / QR Share Card
                item {
                    DropLinkCard(elevated = true) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = s.sendViaBrowserOrQr,
                                tint = ext.cyan,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.sendViaBrowserOrQr, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = s.sendViaBrowserOrQrDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        if (!shareSession.isActive) {
                            Button(
                                onClick = onStartBrowserShare,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_browser_share_button")
                            ) {
                                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.sendViaBrowserOrQr)
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = s.shareLinkActive,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = ext.success
                                )
                                QrCodeDisplay(content = shareSession.shareUrl, sizeDp = 190)
                                Text(
                                    text = shareSession.shareUrl,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = ext.cyan
                                )
                                Text(
                                    text = s.openInBrowserHint,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(shareSession.shareUrl))
                                            copiedNotice = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = s.copyLink, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (copiedNotice) s.linkCopied else s.copyLink)
                                    }
                                    OutlinedButton(
                                        onClick = onStopBrowserShare,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(s.stopSharing)
                                    }
                                }
                            }
                        }
                    }
                }

                // Discovered Peers
                if (peers.isNotEmpty()) {
                    item {
                        Text(s.sendToDiscoveredDevice, style = MaterialTheme.typography.titleSmall)
                    }
                    items(peers, key = { "${it.method}-${it.id}-${it.address}" }) { peer ->
                        PeerDeviceCard(
                            peer = peer,
                            actionLabel = s.send,
                            onAction = { pendingConfirmPeer = peer }
                        )
                    }
                }
            }
        }
    }
}

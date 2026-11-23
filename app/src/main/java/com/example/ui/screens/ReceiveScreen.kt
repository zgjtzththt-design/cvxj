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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalAppStrings
import com.example.model.ActiveTransferTask
import com.example.model.ReceiveSessionState
import com.example.model.TransferDirection
import com.example.network.HashUtils
import com.example.ui.components.DropLinkCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.QrCodeDisplay
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    receiveSession: ReceiveSessionState,
    activeTasks: List<ActiveTransferTask>,
    onBack: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onRespondOffer: (offerId: String, accept: Boolean, trust: Boolean) -> Unit,
    onCancelTask: (String) -> Unit,
    onOpenSaveFolder: () -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var remainingSeconds by remember(receiveSession.expiresAtMs) { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        if (!receiveSession.isActive && !receiveSession.isStarting) {
            onStartSession()
        }
    }

    LaunchedEffect(receiveSession.isActive, receiveSession.expiresAtMs) {
        while (receiveSession.isActive) {
            remainingSeconds = ((receiveSession.expiresAtMs - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    val incomingTasks = activeTasks.filter { it.direction == TransferDirection.RECEIVED }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.receiveFilesTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onOpenSaveFolder,
                        modifier = Modifier.testTag("open_save_folder_button")
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = s.openSaveFolder, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.openSaveFolder)
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
            // Pending Incoming Offer Consent Card
            val pending = receiveSession.pendingOffer
            if (pending != null) {
                item {
                    DropLinkCard(elevated = true) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = s.incomingOfferTitle, tint = ext.warning)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(s.incomingOfferTitle, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${s.incomingOfferFrom}: ${pending.senderName} (${pending.senderAddress})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.cyan
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        pending.files.take(5).forEach { f ->
                            Text(
                                text = "• ${f.name} (${HashUtils.formatBytes(f.sizeBytes)})",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (pending.files.size > 5) {
                            Text(
                                text = "+${pending.files.size - 5} more files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onRespondOffer(pending.offerId, true, false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("accept_incoming_button")
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.acceptFiles)
                            }
                            OutlinedButton(
                                onClick = { onRespondOffer(pending.offerId, false, false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("reject_incoming_button")
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(s.rejectFiles)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        TextButton(
                            onClick = { onRespondOffer(pending.offerId, true, true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.trustDeviceAndAccept)
                        }
                    }
                }
            }

            // Session QR & Connection Info Card
            item {
                DropLinkCard(elevated = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (receiveSession.isActive) s.receiveSessionActive else s.receiveSessionInactive,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (receiveSession.isActive) ext.success else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (receiveSession.isActive) {
                                Text(
                                    text = "${s.expiresInLabel}: ${HashUtils.formatRemainingTime(remainingSeconds)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = ext.cyan
                                )
                            }
                        }
                        if (receiveSession.isActive) {
                            OutlinedButton(
                                onClick = onStopSession,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("stop_receive_button")
                            ) {
                                Text(s.stopReceiveSession)
                            }
                        } else {
                            Button(
                                onClick = onStartSession,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_receive_button")
                            ) {
                                Text(s.startReceiveSession)
                            }
                        }
                    }

                    if (receiveSession.isActive && receiveSession.shareUrl.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QrCodeDisplay(content = receiveSession.shareUrl, sizeDp = 210)
                            Text(
                                text = s.scanToConnectBrowser,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = receiveSession.shareUrl,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = ext.cyan,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(receiveSession.shareUrl))
                                        copied = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = s.copyLink, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copied) s.linkCopied else s.copyLink)
                                }
                                FilledTonalButton(
                                    onClick = onOpenSaveFolder,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.FolderOpen, contentDescription = s.openSaveFolder, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.openSaveFolder)
                                }
                            }
                            Text(
                                text = "${s.sessionTokenLabel}: ${receiveSession.sessionToken}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Incoming Tasks / Waiting State
            item {
                Text(
                    text = "${s.receivedInSession} (${receiveSession.receivedCount})",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (incomingTasks.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Rounded.WifiTethering,
                        title = s.waitingForSender,
                        body = s.wifiHotspotTip
                    )
                }
            } else {
                items(incomingTasks, key = { it.id }) { task ->
                    TransferProgressCard(
                        task = task,
                        onCancel = { onCancelTask(task.id) }
                    )
                }
            }
        }
    }
}

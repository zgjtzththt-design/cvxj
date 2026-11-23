package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransferEntity
import com.example.i18n.LocalAppStrings
import com.example.model.ActiveTransferTask
import com.example.model.DiscoveredPeer
import com.example.model.ReceiveSessionState
import com.example.model.SendShareSessionState
import com.example.model.StorageSummary
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.network.HashUtils
import com.example.ui.components.DropLinkBrandHeader
import com.example.ui.components.DropLinkCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.PeerDeviceCard
import com.example.ui.components.TransferProgressCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors

@Composable
fun HomeScreen(
    receiveSession: ReceiveSessionState,
    sendShareSession: SendShareSessionState,
    activeTasks: List<ActiveTransferTask>,
    peers: List<DiscoveredPeer>,
    recentTransfers: List<TransferEntity>,
    storageSummary: StorageSummary,
    isDiscovering: Boolean,
    onOpenSendPicker: () -> Unit,
    onStartReceive: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshDiscovery: () -> Unit,
    onPeerClick: (DiscoveredPeer) -> Unit,
    onCancelTask: (String) -> Unit,
    onOpenTransferDetail: (Long) -> Unit,
    onOpenAllHistory: () -> Unit,
    onOpenAllDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current

    LaunchedEffect(Unit) {
        onRefreshDiscovery()
    }

    val statusText = when {
        receiveSession.isActive -> s.receivingActiveStatus
        sendShareSession.isActive -> s.sharingActiveStatus
        else -> s.localReadyStatus
    }
    val isLive = receiveSession.isActive || sendShareSession.isActive

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DropLinkBrandHeader(
                    statusLabel = statusText,
                    isLive = isLive,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalIconButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = s.qrQuickScan
                        )
                    }
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = s.settings
                        )
                    }
                }
            }
        }

        // Primary Send & Receive Dual Action Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroActionTile(
                    title = s.send,
                    subtitle = s.sendSubtitle,
                    icon = Icons.Rounded.Upload,
                    gradient = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            Color(0xFF1A56DB)
                        )
                    ),
                    contentColor = Color.White,
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    testTag = "home_send_button",
                    onClick = onOpenSendPicker,
                    modifier = Modifier.weight(1f)
                )
                HeroActionTile(
                    title = s.receive,
                    subtitle = s.receiveSubtitle,
                    icon = Icons.Rounded.Download,
                    gradient = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            ext.cyanContainer.copy(alpha = 0.7f)
                        )
                    ),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    borderColor = ext.cyan.copy(alpha = 0.4f),
                    testTag = "home_receive_button",
                    onClick = onStartReceive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Active Transfers Section
        val visibleTasks = activeTasks.take(3)
        if (visibleTasks.isNotEmpty()) {
            item {
                SectionHeader(title = s.activeTransfers)
            }
            items(visibleTasks, key = { it.id }) { task ->
                TransferProgressCard(
                    task = task,
                    onCancel = { onCancelTask(task.id) }
                )
            }
        }

        // Nearby Devices Section
        item {
            SectionHeader(
                title = s.nearbyDevices,
                actionText = if (isDiscovering) s.scanning else s.scanNow,
                actionIcon = Icons.Rounded.Refresh,
                onAction = onRefreshDiscovery,
                secondaryActionText = s.viewAll,
                onSecondaryAction = onOpenAllDevices
            )
        }

        if (peers.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Rounded.Devices,
                    title = s.noNearbyDevicesTitle,
                    body = s.noNearbyDevicesBody,
                    actionLabel = s.scanNow,
                    onAction = onRefreshDiscovery
                )
            }
        } else {
            items(peers.take(4), key = { "${it.method}-${it.id}-${it.address}" }) { peer ->
                PeerDeviceCard(
                    peer = peer,
                    actionLabel = s.send,
                    onAction = { onPeerClick(peer) }
                )
            }
        }

        // Storage Summary Card
        item {
            StorageSummaryCard(storageSummary = storageSummary)
        }

        // Recent Transfers Section
        item {
            SectionHeader(
                title = s.recentTransfers,
                actionText = if (recentTransfers.isNotEmpty()) s.viewAll else null,
                actionIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                onAction = onOpenAllHistory
            )
        }

        if (recentTransfers.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Rounded.History,
                    title = s.noRecentTransfersTitle,
                    body = s.noRecentTransfersBody
                )
            }
        } else {
            items(recentTransfers, key = { it.id }) { entity ->
                RecentTransferRow(
                    entity = entity,
                    onClick = { onOpenTransferDetail(entity.id) }
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun HeroActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: Brush,
    contentColor: Color,
    borderColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(136.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient)
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(contentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = contentColor
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (secondaryActionText != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(secondaryActionText)
                }
            }
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction) {
                    if (actionIcon != null) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(storageSummary: StorageSummary) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val usedFraction = if (storageSummary.totalBytes > 0L) {
        (storageSummary.usedBytes.toFloat() / storageSummary.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    DropLinkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = s.storageSummary,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = s.storageSummary, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${s.availableStorage}: ${HashUtils.formatBytes(storageSummary.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ext.cyanContainer
            ) {
                Text(
                    text = "${storageSummary.dropLinkFileCount} ${s.filesCountLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.cyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { usedFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${s.usedByDropLink}: ${HashUtils.formatBytes(storageSummary.dropLinkBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = ext.cyan
            )
            Text(
                text = HashUtils.formatBytes(storageSummary.totalBytes),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentTransferRow(
    entity: TransferEntity,
    onClick: () -> Unit
) {
    val ext = LocalDropLinkColors.current
    val isSent = entity.direction == TransferDirection.SENT.name
    val isOk = entity.status == TransferStatus.COMPLETED.name

    DropLinkCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOk) ext.successContainer else MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSent) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived,
                    contentDescription = entity.direction,
                    tint = if (isOk) ext.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entity.peerName} • ${HashUtils.formatBytes(entity.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (isOk) ext.successContainer else MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = if (isOk) "SHA-256 ✓" else entity.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOk) ext.success else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

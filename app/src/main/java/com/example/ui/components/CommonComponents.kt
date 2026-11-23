package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.i18n.LocalAppStrings
import com.example.model.ActiveTransferTask
import com.example.model.ConnectionMethod
import com.example.model.DiscoveredPeer
import com.example.model.FileCategory
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.network.HashUtils
import com.example.network.NetworkUtils
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalDropLinkColors
import com.example.ui.theme.SpaceGroteskFamily

@Composable
fun DropLinkCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalDropLinkColors.current
    val bg = if (elevated) ext.cardElevated else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = bg,
        border = BorderStroke(1.dp, ext.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun DropLinkBrandHeader(
    statusLabel: String,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val ext = LocalDropLinkColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_droplink_logo),
            contentDescription = "DropLink",
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ext.cardBorder, RoundedCornerShape(12.dp))
        )
        Column {
            Text(
                text = "DropLink",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isLive) ext.cyan else ext.success)
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLive) ext.cyan else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FileCategoryBadge(
    category: FileCategory,
    modifier: Modifier = Modifier
) {
    val ext = LocalDropLinkColors.current
    val (icon, tint, bg) = when (category) {
        FileCategory.IMAGE -> Triple(Icons.Rounded.Image, ext.cyan, ext.cyanContainer)
        FileCategory.VIDEO -> Triple(Icons.Rounded.VideoFile, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primaryContainer)
        FileCategory.AUDIO -> Triple(Icons.Rounded.AudioFile, ext.warning, ext.warningContainer)
        FileCategory.DOCUMENT -> Triple(Icons.Rounded.Description, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        FileCategory.ARCHIVE -> Triple(Icons.Rounded.FolderZip, ext.warning, ext.warningContainer)
        FileCategory.APK -> Triple(Icons.Rounded.Android, ext.success, ext.successContainer)
        FileCategory.OTHER -> Triple(Icons.Rounded.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category.name,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

fun connectionMethodIcon(method: ConnectionMethod): ImageVector {
    return when (method) {
        ConnectionMethod.LAN_HTTP -> Icons.Rounded.Wifi
        ConnectionMethod.WEB_BROWSER -> Icons.Rounded.Language
        ConnectionMethod.WIFI_DIRECT -> Icons.Rounded.WifiTethering
        ConnectionMethod.BLUETOOTH -> Icons.Rounded.Bluetooth
        ConnectionMethod.INTERNET_FUTURE -> Icons.Rounded.Public
    }
}

@Composable
fun connectionMethodLabel(method: ConnectionMethod): String {
    val s = LocalAppStrings.current
    return when (method) {
        ConnectionMethod.LAN_HTTP -> s.methodLan
        ConnectionMethod.WEB_BROWSER -> "Web Browser"
        ConnectionMethod.WIFI_DIRECT -> s.methodWifiDirect
        ConnectionMethod.BLUETOOTH -> s.methodBluetooth
        ConnectionMethod.INTERNET_FUTURE -> s.methodInternet
    }
}

@Composable
fun TransferProgressCard(
    task: ActiveTransferTask,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    val isRunning = task.status == TransferStatus.IN_PROGRESS ||
        task.status == TransferStatus.QUEUED ||
        task.status == TransferStatus.AWAITING_APPROVAL

    DropLinkCard(modifier = modifier.animateContentSize(), elevated = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (task.status) {
                            TransferStatus.COMPLETED -> ext.successContainer
                            TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (task.status) {
                        TransferStatus.COMPLETED -> Icons.Rounded.CheckCircle
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> Icons.Rounded.ErrorOutline
                        else -> if (task.direction == TransferDirection.SENT) {
                            Icons.AutoMirrored.Rounded.CallMade
                        } else {
                            Icons.AutoMirrored.Rounded.CallReceived
                        }
                    },
                    contentDescription = task.status.name,
                    tint = when (task.status) {
                        TransferStatus.COMPLETED -> ext.success
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.peerName} • ${HashUtils.formatBytes(task.bytesTransferred)} / ${HashUtils.formatBytes(task.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isRunning) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("cancel_transfer_${task.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cancel,
                        contentDescription = s.cancelTransfer,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else if (task.integrityVerified) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = s.integrityVerified,
                    tint = ext.success,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (task.status == TransferStatus.COMPLETED) 1f else task.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = when (task.status) {
                TransferStatus.COMPLETED -> ext.success
                TransferStatus.FAILED, TransferStatus.CANCELLED -> MaterialTheme.colorScheme.error
                else -> ext.cyan
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusLabel = when (task.status) {
                TransferStatus.QUEUED -> s.statusQueued
                TransferStatus.AWAITING_APPROVAL -> s.statusAwaitingApproval
                TransferStatus.IN_PROGRESS -> "${(task.progress * 100).toInt()}%"
                TransferStatus.PAUSED -> s.statusPaused
                TransferStatus.COMPLETED -> s.statusCompleted
                TransferStatus.CANCELLED -> s.statusCancelled
                TransferStatus.FAILED -> s.statusFailed
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (task.status == TransferStatus.IN_PROGRESS) {
                Text(
                    text = "${HashUtils.formatSpeed(task.speedBps)} • ${HashUtils.formatRemainingTime(task.remainingSeconds)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = ext.cyan
                )
            } else if (task.errorMessage != null && task.status != TransferStatus.COMPLETED) {
                Text(
                    text = task.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PeerDeviceCard(
    peer: DiscoveredPeer,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalDropLinkColors.current
    DropLinkCard(modifier = modifier, onClick = onAction) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                ext.cyanContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = connectionMethodIcon(peer.method),
                    contentDescription = peer.method.name,
                    tint = ext.cyan,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${connectionMethodLabel(peer.method)} • ${peer.details.ifBlank { peer.address }}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onAction,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(text = actionLabel, maxLines = 1)
            }
        }
    }
}

@Composable
fun QrCodeDisplay(
    content: String,
    modifier: Modifier = Modifier,
    sizeDp: Int = 210
) {
    val bitmap: Bitmap? = remember(content) {
        NetworkUtils.generateQrBitmap(content, 560)
    }
    if (bitmap != null) {
        Surface(
            modifier = modifier.size(sizeDp.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            border = BorderStroke(2.dp, LocalDropLinkColors.current.cyan.copy(alpha = 0.45f))
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR Code",
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    DropLinkCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = LocalDropLinkColors.current.cyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun PermissionRationaleCard(
    title: String,
    description: String,
    grantLabel: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = LocalDropLinkColors.current
    DropLinkCard(modifier = modifier, elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ext.warningContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = title,
                    tint = ext.warning
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
        ) {
            Text(grantLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerOptionSheet(
    onDismiss: () -> Unit,
    onPickAnyFiles: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFolder: () -> Unit
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = s.chooseFilesSheetTitle,
                style = MaterialTheme.typography.headlineSmall
            )
            PickerOptionRow(
                icon = Icons.Rounded.InsertDriveFile,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                title = s.pickAnyFiles,
                subtitle = s.pickAnyFilesDesc,
                testTag = "pick_any_files_option",
                onClick = {
                    onDismiss()
                    onPickAnyFiles()
                }
            )
            PickerOptionRow(
                icon = Icons.Rounded.Image,
                iconTint = ext.cyan,
                iconBg = ext.cyanContainer,
                title = s.pickMedia,
                subtitle = s.pickMediaDesc,
                testTag = "pick_media_option",
                onClick = {
                    onDismiss()
                    onPickMedia()
                }
            )
            PickerOptionRow(
                icon = Icons.Rounded.FolderZip,
                iconTint = ext.warning,
                iconBg = ext.warningContainer,
                title = s.pickFolder,
                subtitle = s.pickFolderDesc,
                testTag = "pick_folder_option",
                onClick = {
                    onDismiss()
                    onPickFolder()
                }
            )
        }
    }
}

@Composable
private fun PickerOptionRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    DropLinkCard(
        modifier = Modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize(),
        elevated = true,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = iconTint)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

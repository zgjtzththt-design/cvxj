package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TransferEntity
import com.example.i18n.LocalAppStrings
import com.example.model.TransferDirection
import com.example.model.TransferStatus
import com.example.network.HashUtils
import com.example.ui.components.DropLinkCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.theme.LocalDropLinkColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryFilter { ALL, SENT, RECEIVED, COMPLETED, FAILED }

@Composable
fun HistoryScreen(
    transfers: List<TransferEntity>,
    onOpenDetail: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    val ext = LocalDropLinkColors.current
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(s.clearHistory) },
            text = { Text("${s.clearHistory}?") },
            confirmButton = {
                Button(onClick = {
                    onClearHistory()
                    confirmClear = false
                }) { Text(s.clearHistory) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(s.cancelTransfer) }
            }
        )
    }

    val filtered = remember(transfers, search, filter) {
        transfers.filter { item ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.SENT -> item.direction == TransferDirection.SENT.name
                HistoryFilter.RECEIVED -> item.direction == TransferDirection.RECEIVED.name
                HistoryFilter.COMPLETED -> item.status == TransferStatus.COMPLETED.name
                HistoryFilter.FAILED -> item.status != TransferStatus.COMPLETED.name
            }
            val matchesSearch = search.isBlank() ||
                item.fileName.contains(search, ignoreCase = true) ||
                item.peerName.contains(search, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(s.historyTitle, style = MaterialTheme.typography.headlineMedium)
                if (transfers.isNotEmpty()) {
                    IconButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = s.clearHistory)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text(s.searchHistoryHint) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    HistoryFilter.ALL to s.filterAll,
                    HistoryFilter.SENT to s.filterSent,
                    HistoryFilter.RECEIVED to s.filterReceived,
                    HistoryFilter.COMPLETED to s.filterCompleted,
                    HistoryFilter.FAILED to s.filterFailed
                )
                items(options) { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Rounded.History,
                    title = s.noRecentTransfersTitle,
                    body = s.noRecentTransfersBody
                )
            }
        } else {
            items(filtered, key = { it.id }) { entity ->
                val isSent = entity.direction == TransferDirection.SENT.name
                val isOk = entity.status == TransferStatus.COMPLETED.name
                DropLinkCard(onClick = { onOpenDetail(entity.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isOk) ext.successContainer else MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSent) Icons.AutoMirrored.Rounded.CallMade else Icons.AutoMirrored.Rounded.CallReceived,
                                contentDescription = entity.direction,
                                tint = if (isOk) ext.success else MaterialTheme.colorScheme.error
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
                            Text(
                                text = dateFormatter.format(Date(entity.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isOk) ext.successContainer else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (isOk) s.statusCompleted else s.statusFailed,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOk) ext.success else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

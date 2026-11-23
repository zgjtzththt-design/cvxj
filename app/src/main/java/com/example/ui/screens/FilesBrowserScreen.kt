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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.i18n.LocalAppStrings
import com.example.model.FileCategory
import com.example.model.ReceivedFileRecord
import com.example.network.HashUtils
import com.example.ui.components.DropLinkCard
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileCategoryBadge

@Composable
fun FilesBrowserScreen(
    files: List<ReceivedFileRecord>,
    onRefresh: () -> Unit,
    onOpenFile: (ReceivedFileRecord) -> Unit,
    onShareFile: (ReceivedFileRecord) -> Unit,
    onSendFile: (ReceivedFileRecord) -> Unit,
    onDeleteFile: (ReceivedFileRecord) -> Unit,
    onOpenSaveFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val s = LocalAppStrings.current
    var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }

    LaunchedEffect(Unit) {
        onRefresh()
    }

    val filtered = remember(files, selectedCategory) {
        if (selectedCategory == null) files else files.filter { it.category == selectedCategory }
    }

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
                Text(s.filesBrowserTitle, style = MaterialTheme.typography.headlineMedium)
                FilledTonalButton(
                    onClick = onOpenSaveFolder,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = s.openSaveFolder, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(s.openSaveFolder)
                }
            }
        }

        item {
            val categories = listOf(
                null to s.catAll,
                FileCategory.IMAGE to s.catImages,
                FileCategory.VIDEO to s.catVideos,
                FileCategory.AUDIO to s.catAudio,
                FileCategory.DOCUMENT to s.catDocs,
                FileCategory.ARCHIVE to s.catArchives,
                FileCategory.APK to s.catApks
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { (cat, label) ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(label) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Rounded.FolderOpen,
                    title = s.noReceivedFilesTitle,
                    body = s.noReceivedFilesBody
                )
            }
        } else {
            items(filtered, key = { it.id }) { record ->
                DropLinkCard(onClick = { onOpenFile(record) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FileCategoryBadge(category = record.category)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${HashUtils.formatBytes(record.sizeBytes)} • ${record.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onSendFile(record) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = s.sendSelected)
                        }
                        IconButton(
                            onClick = { onShareFile(record) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = s.shareFile)
                        }
                        IconButton(
                            onClick = { onDeleteFile(record) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = s.deleteRecord)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

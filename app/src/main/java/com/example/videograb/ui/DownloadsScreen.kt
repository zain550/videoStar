package com.example.videograb.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.videograb.DownloadItem
import com.example.videograb.FileActions
import com.example.videograb.MainViewModel
import com.example.videograb.R
import com.example.videograb.Status
import com.example.videograb.formatBytes
import com.example.videograb.formatDuration

@Composable
fun DownloadsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val downloadList by vm.downloads.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DownloadItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tab_downloads),
                style = MaterialTheme.typography.headlineMedium,
            )
            if (downloadList.any { !it.isActive }) {
                TextButton(onClick = { vm.clearFinished() }) {
                    Text(stringResource(R.string.clear_finished))
                }
            }
        }

        if (downloadList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.downloads_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(downloadList, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onCancel = { vm.cancel(item.id) },
                        onRetry = { vm.retry(context, item.id) },
                        onOpen = { handle(context, FileActions.open(context, item)) },
                        onShare = { handle(context, FileActions.share(context, item)) },
                        onDelete = {
                            if (item.status == Status.COMPLETED) pendingDelete = item
                            else vm.remove(context, item, deleteFile = false)
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(target.title, maxLines = 3, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(context, target, deleteFile = true)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete_file_too)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.remove(context, target, deleteFile = false)
                    pendingDelete = null
                }) { Text(stringResource(R.string.remove_from_list)) }
            },
        )
    }
}

private fun handle(context: Context, result: FileActions.OpenResult) {
    val msg = when (result) {
        FileActions.OpenResult.OK -> return
        FileActions.OpenResult.MISSING -> R.string.file_missing
        FileActions.OpenResult.NO_APP -> R.string.cannot_open
    }
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(104.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small),
            ) {
                if (item.thumbnail != null) {
                    AsyncImage(
                        model = item.thumbnail,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    item.qualityLabel.ifEmpty { null },
                    formatBytes(item.sizeBytes).ifEmpty { null },
                ).joinToString(" • ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (item.status) {
                    Status.QUEUED -> StatusRow(stringResource(R.string.status_queued), onCancel)

                    Status.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val percent = (item.progress * 100).toInt()
                        val eta = if (item.etaSeconds > 0) {
                            " • " + stringResource(R.string.eta_format, formatDuration(item.etaSeconds))
                        } else ""
                        StatusRow("$percent%$eta", onCancel)
                    }

                    Status.PROCESSING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        StatusRow(stringResource(R.string.status_processing), onCancel)
                    }

                    Status.COMPLETED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.status_completed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.open))
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }

                    Status.FAILED, Status.CANCELED -> {
                        val text = if (item.status == Status.FAILED) {
                            val base = stringResource(R.string.status_failed)
                            if (item.error.isNullOrBlank()) base else "$base: ${item.error}"
                        } else {
                            stringResource(R.string.status_canceled)
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.status == Status.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row {
                            TextButton(onClick = onRetry) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(" " + stringResource(R.string.retry))
                            }
                            TextButton(onClick = onDelete) { Text(stringResource(R.string.remove)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(text: String, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
        }
    }
}

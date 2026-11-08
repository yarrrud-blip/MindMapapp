package com.example.mindmap.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mindmap.model.MindMapSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MapListScreen(
    maps: List<MindMapSummary>,
    onCreateMap: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onRenameMap: (String, String) -> Unit,
    onDeleteMap: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var mapToRename by remember { mutableStateOf<MindMapSummary?>(null) }
    var mapToDelete by remember { mutableStateOf<MindMapSummary?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Мои интеллект-карты",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Создать карту") }
            )
        }
    ) { innerPadding ->
        if (maps.isEmpty()) {
            // Заглушка, если ни одной карты ещё не создано
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "У вас пока нет сохранённых карт",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Нажмите кнопку «Создать карту», чтобы начать визуализировать идеи.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Создать первую карту")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(maps, key = { it.id }) { summary ->
                    var menuExpanded by remember { mutableStateOf(false) }

                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenMap(summary.id) },
                                onLongClick = { menuExpanded = true }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Изменено: ${formatDate(summary.lastModified)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Узлов: ${summary.nodeCount} • Связей: ${summary.connectionCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Меню карты"
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Переименовать") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Edit, contentDescription = null)
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            mapToRename = summary
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            mapToDelete = summary
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог создания новой карты
    if (showCreateDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Новая интеллект-карта") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Название карты") },
                    placeholder = { Text("Например: План проекта") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = newTitle.trim().ifEmpty { "Новая карта" }
                        showCreateDialog = false
                        onCreateMap(finalName)
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог переименования карты
    mapToRename?.let { target ->
        var updatedTitle by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { mapToRename = null },
            title = { Text("Переименовать карту") },
            text = {
                OutlinedTextField(
                    value = updatedTitle,
                    onValueChange = { updatedTitle = it },
                    label = { Text("Новое название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (updatedTitle.isNotBlank()) {
                            onRenameMap(target.id, updatedTitle.trim())
                        }
                        mapToRename = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { mapToRename = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения удаления карты
    mapToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { mapToDelete = null },
            title = { Text("Удалить карту?") },
            text = {
                Text("Вы действительно хотите удалить карту «${target.title}»? Это действие нельзя отменить.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        onDeleteMap(target.id)
                        mapToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { mapToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
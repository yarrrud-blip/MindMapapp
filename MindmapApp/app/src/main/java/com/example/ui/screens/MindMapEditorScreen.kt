package com.example.mindmap.ui.screens

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindmap.model.*
import com.example.mindmap.ui.components.BezierMath
import com.example.mindmap.ui.components.ColorPaletteRow
import com.example.mindmap.ui.components.PresetNodeColors
import com.example.mindmap.ui.components.PresetTextAndLineColors
import com.example.mindmap.viewmodel.MindMapUiState
import com.example.mindmap.viewmodel.MindMapViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MindMapEditorScreen(
    uiState: MindMapUiState,
    viewModel: MindMapViewModel
) {
    val document = uiState.currentDocument ?: return
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Размер видимой области холста в пикселях
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Состояния модальных окон и нижних панелей редактирования
    var nodeForQuickTextEdit by remember { mutableStateOf<MindMapNode?>(null) }
    var nodeForPropertiesSheet by remember { mutableStateOf<MindMapNode?>(null) }
    var nodeToConfirmDelete by remember { mutableStateOf<MindMapNode?>(null) }
    var showConnectionSheet by remember { mutableStateOf(false) }
    var isDraggingControlPoint by remember { mutableStateOf(false) }

    // Автоматическое сохранение при нажатии системной кнопки «Назад»
    BackHandler {
        viewModel.saveAndExitEditor()
    }

    // Показ уведомлений (например, о сохранении или режиме связи)
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearStatusMessage()
        }
    }

    val scale = uiState.canvasScale
    val offset = uiState.canvasOffset

    // Актуальные ссылки для pointerInput-колбэков, чтобы избежать перезапуска жестов
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentDoc by rememberUpdatedState(document)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Масштаб: ${(scale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveAndExitEditor() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад с автосохранением"
                        )
                    }
                },
                actions = {
                    // Кнопка добавления нового узла
                    IconButton(
                        onClick = {
                            val centerScreen = Offset(
                                x = viewportSize.width / 2f,
                                y = viewportSize.height / 2f
                            )
                            val centerWorld = Offset(
                                x = (centerScreen.x - offset.x) / scale,
                                y = (centerScreen.y - offset.y) / scale
                            )
                            viewModel.addNode(centerWorld)
                        }
                    ) {
                        Icon(Icons.Default.AddBox, contentDescription = "Добавить узел")
                    }

                    // Кнопка режима создания связи между двумя узлами
                    FilledTonalIconButton(
                        onClick = { viewModel.toggleConnectingMode() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (uiState.isConnectingMode)
                                MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Соединить узлы",
                            tint = if (uiState.isConnectingMode)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Если выделена связь — кнопка открытия её настроек
                    if (uiState.selectedConnectionId != null) {
                        IconButton(onClick = { showConnectionSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Свойства связи",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Сброс панорамирования и масштаба
                    IconButton(
                        onClick = {
                            val centerOffset = Offset(
                                x = viewportSize.width / 2f - 80f,
                                y = viewportSize.height / 2f - 36f
                            )
                            viewModel.updateCanvasTransform(1f, centerOffset)
                        }
                    ) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Центрировать")
                    }

                    // Ручное сохранение карты
                    IconButton(onClick = { viewModel.saveCurrentMap(showNotification = true) }) {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить карту")
                    }
                }
            )
        }
    ) { innerPadding ->
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        val primaryColor = MaterialTheme.colorScheme.primary
        val labelBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        val labelTextColor = MaterialTheme.colorScheme.onSurfaceVariant

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
                .onSizeChanged { size ->
                    if (viewportSize == IntSize.Zero && size != IntSize.Zero) {
                        // При первом открытии располагаем начало координат (0,0) в центре экрана
                        viewModel.updateCanvasTransform(
                            scale = 1f,
                            offset = Offset(size.width / 2f - 80f, size.height / 2f - 36f)
                        )
                    }
                    viewportSize = size
                }
                // Обработка жестов масштабирования (щипок) и панорамирования холста
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = currentScale
                        val newScale = (oldScale * zoom).coerceIn(0.3f, 3.0f)
                        val effectiveZoom = newScale / oldScale
                        // Масштабирование относительно центра щипка пальцев
                        val newOffset = (currentOffset - centroid) * effectiveZoom + centroid + pan
                        viewModel.updateCanvasTransform(newScale, newOffset)
                    }
                }
                // Обработка тапа по холсту (выбор связи или снятие выделения)
                .pointerInput(Unit) {
                    detectTapGestures { tapScreen ->
                        val worldPoint = Offset(
                            x = (tapScreen.x - currentOffset.x) / currentScale,
                            y = (tapScreen.y - currentOffset.y) / currentScale
                        )
                        val nodeMap = currentDoc.nodes.associateBy { it.id }
                        val hitThresholdWorld = 26f / currentScale

                        // Ищем ближайшую связь, по которой тапнул пользователь
                        var hitConnection: MindMapConnection? = null
                        var minDistance = Float.MAX_VALUE

                        for (conn in currentDoc.connections) {
                            val fromNode = nodeMap[conn.fromNodeId] ?: continue
                            val toNode = nodeMap[conn.toNodeId] ?: continue
                            val p0 = BezierMath.nodeCenter(fromNode)
                            val p2 = BezierMath.nodeCenter(toNode)
                            val mid = Offset((p0.x + p2.x) / 2f, (p0.y + p2.y) / 2f)
                            val p1 = Offset(mid.x + conn.controlOffsetX, mid.y + conn.controlOffsetY)

                            val dist = BezierMath.distanceToQuadraticBezier(worldPoint, p0, p1, p2)
                            if (dist < hitThresholdWorld && dist < minDistance) {
                                minDistance = dist
                                hitConnection = conn
                            }
                        }

                        if (hitConnection != null) {
                            if (uiState.selectedConnectionId == hitConnection.id) {
                                // Повторный тап по уже выделенной связи открывает её свойства
                                showConnectionSheet = true
                            } else {
                                viewModel.selectConnection(hitConnection.id)
                            }
                        } else {
                            viewModel.clearSelection()
                        }
                    }
                }
        ) {
            // 1. СЛОЙ ХОЛСТА: сетка и все линии связей
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Рисуем бесконечную точечную/линейную сетку
                drawInfiniteGrid(scale = scale, offset = offset, gridColor = gridColor)

                val nodeMap = document.nodes.associateBy { it.id }

                document.connections.forEach { conn ->
                    val fromNode = nodeMap[conn.fromNodeId] ?: return@forEach
                    val toNode = nodeMap[conn.toNodeId] ?: return@forEach
                    val isSelected = conn.id == uiState.selectedConnectionId

                    drawMindMapConnection(
                        conn = conn,
                        fromNode = fromNode,
                        toNode = toNode,
                        isSelected = isSelected,
                        isDraggingHandle = isDraggingControlPoint && isSelected,
                        scale = scale,
                        offset = offset,
                        highlightColor = primaryColor,
                        labelBgColor = labelBgColor,
                        labelTextColor = labelTextColor
                    )
                }
            }

            // 2. СЛОЙ УПРАВЛЯЮЩЕЙ ТОЧКИ ВЫДЕЛЕННОЙ СВЯЗИ (Drag Handle для изгиба кривой Безье)
            val selectedConnection = document.connections.find { it.id == uiState.selectedConnectionId }
            if (selectedConnection != null) {
                val fromNode = document.nodes.find { it.id == selectedConnection.fromNodeId }
                val toNode = document.nodes.find { it.id == selectedConnection.toNodeId }
                if (fromNode != null && toNode != null) {
                    val p0 = BezierMath.nodeCenter(fromNode)
                    val p2 = BezierMath.nodeCenter(toNode)
                    val mid = Offset((p0.x + p2.x) / 2f, (p0.y + p2.y) / 2f)
                    // Контрольная точка P1 в мировых координатах
                    val controlWorld = Offset(
                        x = mid.x + selectedConnection.controlOffsetX,
                        y = mid.y + selectedConnection.controlOffsetY
                    )
                    // Переводим позицию контрольной точки в экранные координаты
                    val handleScreen = Offset(
                        x = controlWorld.x * scale + offset.x,
                        y = controlWorld.y * scale + offset.y
                    )
                    val handleSizeDp = 28.dp
                    val handleRadiusPx = with(density) { (handleSizeDp / 2).toPx() }

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = (handleScreen.x - handleRadiusPx).roundToInt(),
                                    y = (handleScreen.y - handleRadiusPx).roundToInt()
                                )
                            }
                            .size(handleSizeDp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape)
                            .pointerInput(selectedConnection.id) {
                                detectDragGestures(
                                    onDragStart = { isDraggingControlPoint = true },
                                    onDragEnd = { isDraggingControlPoint = false },
                                    onDragCancel = { isDraggingControlPoint = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        // Переводим экранный сдвиг пальца в мировые координаты
                                        viewModel.moveConnectionControlPoint(
                                            connectionId = selectedConnection.id,
                                            deltaWorldX = dragAmount.x / currentScale,
                                            deltaWorldY = dragAmount.y / currentScale
                                        )
                                    }
                                )
                            }
                            .pointerInput(selectedConnection.id) {
                                detectTapGestures(
                                    onTap = { showConnectionSheet = true }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = "Потяните для изгиба связи",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // 3. СЛОЙ УЗЛОВ (интерактивные карточки с Drag-and-Drop, двойным и долгим тапом)
            document.nodes.forEach { node ->
                val isSelected = node.id == uiState.selectedNodeId
                NodeCardItem(
                    node = node,
                    isSelected = isSelected,
                    scale = scale,
                    canvasOffset = offset,
                    onTap = { viewModel.onNodeTapped(node.id) },
                    onDoubleTap = {
                        viewModel.onNodeTapped(node.id)
                        nodeForQuickTextEdit = node
                    },
                    onLongPress = {
                        viewModel.onNodeTapped(node.id)
                        nodeForPropertiesSheet = node
                    },
                    onDrag = { deltaScreen ->
                        viewModel.moveNode(
                            nodeId = node.id,
                            deltaWorldX = deltaScreen.x / currentScale,
                            deltaWorldY = deltaScreen.y / currentScale
                        )
                    }
                )
            }

            // Плавающая подсказка внизу экрана при выборе узла или связи
            if (uiState.selectedNodeId != null || uiState.selectedConnectionId != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.selectedNodeId != null) {
                            val selectedNode = document.nodes.find { it.id == uiState.selectedNodeId }
                            Text(
                                text = "Двойной тап — текст • Удержание — стиль",
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(
                                onClick = { selectedNode?.let { nodeForPropertiesSheet = it } }
                            ) {
                                Text("Настроить")
                            }
                        } else if (selectedConnection != null) {
                            Text(
                                text = "Тяните кружок для изгиба линии",
                                style = MaterialTheme.typography.labelMedium
                            )
                            TextButton(onClick = { showConnectionSheet = true }) {
                                Text("Свойства связи")
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Диалог быстрого редактирования текста узла (по двойному тапу) ---
    nodeForQuickTextEdit?.let { targetNode ->
        val latestNode = document.nodes.find { it.id == targetNode.id } ?: targetNode
        var textValue by remember(latestNode) { mutableStateOf(latestNode.text) }

        AlertDialog(
            onDismissRequest = { nodeForQuickTextEdit = null },
            title = { Text("Текст узла") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Содержимое карточки") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateNode(latestNode.copy(text = textValue.ifBlank { "Узел" }))
                        nodeForQuickTextEdit = null
                    }
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeForQuickTextEdit = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // --- Нижняя панель полной настройки узла (по долгому нажатию) ---
    nodeForPropertiesSheet?.let { initialNode ->
        val currentNode = document.nodes.find { it.id == initialNode.id }
        if (currentNode != null) {
            NodePropertiesBottomSheet(
                node = currentNode,
                onDismiss = { nodeForPropertiesSheet = null },
                onUpdateNode = { updated -> viewModel.updateNode(updated) },
                onRequestDelete = {
                    nodeForPropertiesSheet = null
                    nodeToConfirmDelete = currentNode
                }
            )
        }
    }

    // --- Диалог подтверждения удаления узла ---
    nodeToConfirmDelete?.let { node ->
        AlertDialog(
            onDismissRequest = { nodeToConfirmDelete = null },
            title = { Text("Удалить узел?") },
            text = {
                Text("Узел «${node.text}» и все связанные с ним линии будут удалены.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        viewModel.deleteNode(node.id)
                        nodeToConfirmDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { nodeToConfirmDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    // --- Нижняя панель редактирования свойств связи ---
    if (showConnectionSheet && uiState.selectedConnectionId != null) {
        val currentConn = document.connections.find { it.id == uiState.selectedConnectionId }
        if (currentConn != null) {
            ConnectionPropertiesBottomSheet(
                connection = currentConn,
                onDismiss = { showConnectionSheet = false },
                onUpdateConnection = { updated -> viewModel.updateConnection(updated) },
                onResetCurve = { viewModel.resetConnectionCurve(currentConn.id) },
                onDeleteConnection = {
                    showConnectionSheet = false
                    viewModel.deleteConnection(currentConn.id)
                }
            )
        }
    }
}

/**
 * Composable-элемент отдельного узла на холсте.
 */
@Composable
private fun NodeCardItem(
    node: MindMapNode,
    isSelected: Boolean,
    scale: Float,
    canvasOffset: Offset,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDrag: (Offset) -> Unit
) {
    val density = LocalDensity.current

    // Переводим мировые координаты и размеры узла в экранные с учётом текущего зума
    val screenX = node.x * scale + canvasOffset.x
    val screenY = node.y * scale + canvasOffset.y
    val widthDp = with(density) { (node.width * scale).toDp() }
    val heightDp = with(density) { (node.height * scale).toDp() }

    val shape: Shape = when (node.shape) {
        NodeShape.RECTANGLE -> RectangleShape
        NodeShape.ROUNDED_RECT -> RoundedCornerShape((16f * scale).coerceAtLeast(4f).dp)
        NodeShape.OVAL -> CircleShape
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(node.textColor).copy(alpha = 0.25f)
    }
    val borderWidth = if (isSelected) 3.dp else 1.dp
    val shadowElevation = if (isSelected) 10.dp else 3.dp

    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDrag by rememberUpdatedState(onDrag)

    Box(
        modifier = Modifier
            .offset { IntOffset(screenX.roundToInt(), screenY.roundToInt()) }
            .size(width = widthDp, height = heightDp)
            .shadow(elevation = shadowElevation, shape = shape)
            .clip(shape)
            .background(Color(node.backgroundColor))
            .border(width = borderWidth, color = borderColor, shape = shape)
            // Обработка тапа, двойного тапа и долгого нажатия
            .pointerInput(node.id) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { currentOnDoubleTap() },
                    onLongPress = { currentOnLongPress() }
                )
            }
            // Обработка перетаскивания узла (Drag-and-Drop)
            .pointerInput(node.id) {
                detectDragGestures(
                    onDragStart = { currentOnTap() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        currentOnDrag(dragAmount)
                    }
                )
            }
            .padding((8f * scale).coerceAtLeast(4f).dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = node.text,
            color = Color(node.textColor),
            fontSize = (node.fontSize * scale).coerceIn(8f, 48f).sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Отрисовка бесконечной сетки на фоне холста.
 */
private fun DrawScope.drawInfiniteGrid(
    scale: Float,
    offset: Offset,
    gridColor: Color
) {
    val stepWorld = 60f
    val stepScreen = (stepWorld * scale).coerceAtLeast(18f)

    val startX = ((offset.x % stepScreen) + stepScreen) % stepScreen
    val startY = ((offset.y % stepScreen) + stepScreen) % stepScreen

    var x = startX
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += stepScreen
    }

    var y = startY
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += stepScreen
    }
}

/**
 * Отрисовка связи (квадратичной кривой Безье), стрелок, текстовой метки
 * и пунктирных направляющих к контрольной точке при выделении.
 */
private fun DrawScope.drawMindMapConnection(
    conn: MindMapConnection,
    fromNode: MindMapNode,
    toNode: MindMapNode,
    isSelected: Boolean,
    isDraggingHandle: Boolean,
    scale: Float,
    offset: Offset,
    highlightColor: Color,
    labelBgColor: Color,
    labelTextColor: Color
) {
    // 1. Вычисляем опорные точки в мировых координатах
    val p0World = BezierMath.nodeCenter(fromNode)
    val p2World = BezierMath.nodeCenter(toNode)
    val midWorld = Offset((p0World.x + p2World.x) / 2f, (p0World.y + p2World.y) / 2f)
    val p1World = Offset(
        x = midWorld.x + conn.controlOffsetX,
        y = midWorld.y + conn.controlOffsetY
    )

    // 2. Переводим опорные точки в экранные координаты
    val p0 = Offset(p0World.x * scale + offset.x, p0World.y * scale + offset.y)
    val p1 = Offset(p1World.x * scale + offset.x, p1World.y * scale + offset.y)
    val p2 = Offset(p2World.x * scale + offset.x, p2World.y * scale + offset.y)

    val curvePath = Path().apply {
        moveTo(p0.x, p0.y)
        quadraticBezierTo(p1.x, p1.y, p2.x, p2.y)
    }

    val scaledStroke = (conn.strokeWidth * scale).coerceAtLeast(2f)

    // Если связь выделена, рисуем подсветку (полупрозрачный ореол вдоль кривой)
    if (isSelected) {
        drawPath(
            path = curvePath,
            color = highlightColor.copy(alpha = 0.35f),
            style = Stroke(width = scaledStroke + 10f * scale, cap = StrokeCap.Round)
        )

        // Визуальная подсказка: пунктирные линии от центров узлов к контрольной точке P1
        val guideEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        val guideAlpha = if (isDraggingHandle) 0.85f else 0.45f
        drawLine(
            color = highlightColor.copy(alpha = guideAlpha),
            start = p0,
            end = p1,
            strokeWidth = 2f * scale,
            pathEffect = guideEffect
        )
        drawLine(
            color = highlightColor.copy(alpha = guideAlpha),
            start = p2,
            end = p1,
            strokeWidth = 2f * scale,
            pathEffect = guideEffect
        )
    }

    // 3. Настраиваем стиль линии (сплошная, пунктирная, точечная)
    val pathEffect = when (conn.lineStyle) {
        LineStyle.SOLID -> null
        LineStyle.DASHED -> PathEffect.dashPathEffect(
            floatArrayOf(20f * scale, 12f * scale),
            0f
        )
        LineStyle.DOTTED -> PathEffect.dashPathEffect(
            floatArrayOf(4f * scale, 10f * scale),
            0f
        )
    }

    val lineColor = Color(conn.color)

    drawPath(
        path = curvePath,
        color = lineColor,
        style = Stroke(
            width = scaledStroke,
            cap = StrokeCap.Round,
            pathEffect = pathEffect
        )
    )

    // 4. Отрисовка стрелок на границе узлов
    val arrowSize = (14f + conn.strokeWidth * 1.5f) * scale
    if (conn.arrowType == ArrowType.END || conn.arrowType == ArrowType.BOTH) {
        val tEnd = BezierMath.findBoundaryT(toNode, p0World, p1World, p2World, isStartNode = false)
        val tipWorld = BezierMath.evaluateQuadraticBezier(p0World, p1World, p2World, tEnd)
        val prevWorld = BezierMath.evaluateQuadraticBezier(
            p0World, p1World, p2World, (tEnd - 0.04f).coerceAtLeast(0f)
        )
        val tipScreen = Offset(tipWorld.x * scale + offset.x, tipWorld.y * scale + offset.y)
        val angle = atan2(tipWorld.y - prevWorld.y, tipWorld.x - prevWorld.x)
        drawArrowHead(tip = tipScreen, angleRad = angle, size = arrowSize, color = lineColor)
    }

    if (conn.arrowType == ArrowType.START || conn.arrowType == ArrowType.BOTH) {
        val tStart = BezierMath.findBoundaryT(fromNode, p0World, p1World, p2World, isStartNode = true)
        val tipWorld = BezierMath.evaluateQuadraticBezier(p0World, p1World, p2World, tStart)
        val nextWorld = BezierMath.evaluateQuadraticBezier(
            p0World, p1World, p2World, (tStart + 0.04f).coerceAtMost(1f)
        )
        val tipScreen = Offset(tipWorld.x * scale + offset.x, tipWorld.y * scale + offset.y)
        val angle = atan2(tipWorld.y - nextWorld.y, tipWorld.x - nextWorld.x)
        drawArrowHead(tip = tipScreen, angleRad = angle, size = arrowSize, color = lineColor)
    }

    // 5. Отрисовка текстовой метки связи в вершине кривой Безье (t = 0.5)
    if (conn.label.isNotBlank()) {
        val curvePeakWorld = BezierMath.evaluateQuadraticBezier(p0World, p1World, p2World, 0.5f)
        val peakScreen = Offset(
            x = curvePeakWorld.x * scale + offset.x,
            y = curvePeakWorld.y * scale + offset.y - 18f * scale
        )
        drawConnectionLabel(
            text = conn.label,
            position = peakScreen,
            scale = scale,
            bgColor = labelBgColor,
            textColor = labelTextColor
        )
    }
}

private fun DrawScope.drawArrowHead(
    tip: Offset,
    angleRad: Float,
    size: Float,
    color: Color
) {
    val wingAngle = Math.toRadians(28.0).toFloat()
    val x1 = tip.x - size * cos(angleRad - wingAngle)
    val y1 = tip.y - size * sin(angleRad - wingAngle)
    val x2 = tip.x - size * cos(angleRad + wingAngle)
    val y2 = tip.y - size * sin(angleRad + wingAngle)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(x1, y1)
        lineTo(x2, y2)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawConnectionLabel(
    text: String,
    position: Offset,
    scale: Float,
    bgColor: Color,
    textColor: Color
) {
    val textPaint = Paint().apply {
        isAntiAlias = true
        color = textColor.toArgb()
        textSize = (13f * scale).coerceIn(10f, 36f)
        textAlign = Paint.Align.CENTER
    }
    val textWidth = textPaint.measureText(text)
    val fontMetrics = textPaint.fontMetrics
    val textHeight = fontMetrics.descent - fontMetrics.ascent

    val padH = 8f * scale
    val padV = 4f * scale
    val rectTopLeft = Offset(
        x = position.x - textWidth / 2f - padH,
        y = position.y - textHeight / 2f - padV
    )
    val rectSize = androidx.compose.ui.geometry.Size(
        width = textWidth + padH * 2f,
        height = textHeight + padV * 2f
    )

    drawRoundRect(
        color = bgColor,
        topLeft = rectTopLeft,
        size = rectSize,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f * scale, 8f * scale)
    )

    drawContext.canvas.nativeCanvas.drawText(
        text,
        position.x,
        position.y - (fontMetrics.ascent + fontMetrics.descent) / 2f,
        textPaint
    )
}

/**
 * Нижняя шторка (ModalBottomSheet) для настройки внешнего вида и текста узла.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NodePropertiesBottomSheet(
    node: MindMapNode,
    onDismiss: () -> Unit,
    onUpdateNode: (MindMapNode) -> Unit,
    onRequestDelete: () -> Unit
) {
    var localText by remember(node.id) { mutableStateOf(node.text) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Настройка узла",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = localText,
                onValueChange = {
                    localText = it
                    onUpdateNode(node.copy(text = it))
                },
                label = { Text("Текст узла") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Форма узла", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NodeShape.entries.forEach { shapeOption ->
                    FilterChip(
                        selected = node.shape == shapeOption,
                        onClick = { onUpdateNode(node.copy(shape = shapeOption)) },
                        label = { Text(shapeOption.displayName) }
                    )
                }
            }

            Text("Цвет фона", style = MaterialTheme.typography.titleSmall)
            ColorPaletteRow(
                colors = PresetNodeColors,
                selectedColor = node.backgroundColor,
                onColorSelected = { color -> onUpdateNode(node.copy(backgroundColor = color)) }
            )

            Text("Цвет текста", style = MaterialTheme.typography.titleSmall)
            ColorPaletteRow(
                colors = PresetTextAndLineColors,
                selectedColor = node.textColor,
                onColorSelected = { color -> onUpdateNode(node.copy(textColor = color)) }
            )

            Text(
                text = "Размер шрифта: ${node.fontSize.roundToInt()} sp",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = node.fontSize,
                onValueChange = { onUpdateNode(node.copy(fontSize = it)) },
                valueRange = 12f..28f
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRequestDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Удалить узел")
                }

                Button(onClick = onDismiss) {
                    Text("Готово")
                }
            }
        }
    }
}

/**
 * Нижняя шторка (ModalBottomSheet) для настройки параметров связи.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ConnectionPropertiesBottomSheet(
    connection: MindMapConnection,
    onDismiss: () -> Unit,
    onUpdateConnection: (MindMapConnection) -> Unit,
    onResetCurve: () -> Unit,
    onDeleteConnection: () -> Unit
) {
    var localLabel by remember(connection.id) { mutableStateOf(connection.label) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Свойства связи",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = localLabel,
                onValueChange = {
                    localLabel = it
                    onUpdateConnection(connection.copy(label = it))
                },
                label = { Text("Текстовая метка на линии (опционально)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Цвет линии", style = MaterialTheme.typography.titleSmall)
            ColorPaletteRow(
                colors = PresetTextAndLineColors,
                selectedColor = connection.color,
                onColorSelected = { color -> onUpdateConnection(connection.copy(color = color)) }
            )

            Text(
                text = "Толщина линии: ${connection.strokeWidth.roundToInt()} px",
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = connection.strokeWidth,
                onValueChange = { onUpdateConnection(connection.copy(strokeWidth = it)) },
                valueRange = 2f..12f,
                steps = 4
            )

            Text("Стиль линии", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LineStyle.entries.forEach { styleOption ->
                    FilterChip(
                        selected = connection.lineStyle == styleOption,
                        onClick = { onUpdateConnection(connection.copy(lineStyle = styleOption)) },
                        label = { Text(styleOption.displayName) }
                    )
                }
            }

            Text("Стрелки на концах", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ArrowType.entries.forEach { arrowOption ->
                    FilterChip(
                        selected = connection.arrowType == arrowOption,
                        onClick = { onUpdateConnection(connection.copy(arrowType = arrowOption)) },
                        label = { Text(arrowOption.displayName) }
                    )
                }
            }

            // Кнопка выпрямления связи, если контрольная точка была смещена
            if (connection.controlOffsetX != 0f || connection.controlOffsetY != 0f) {
                OutlinedButton(
                    onClick = onResetCurve,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сбросить изгиб (сделать прямой)")
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDeleteConnection,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Удалить связь")
                }

                Button(onClick = onDismiss) {
                    Text("Готово")
                }
            }
        }
    }
}
package com.example.mindmap.viewmodel

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mindmap.data.MindMapRepository
import com.example.mindmap.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние навигации между главным экраном и редактором.
 */
sealed interface ScreenState {
    data object MapList : ScreenState
    data object Editor : ScreenState
}

/**
 * Единое состояние UI приложения.
 */
data class MindMapUiState(
    val screenState: ScreenState = ScreenState.MapList,
    val mapList: List<MindMapSummary> = emptyList(),
    val currentDocument: MindMapDocument? = null,
    val selectedNodeId: String? = null,
    val selectedConnectionId: String? = null,
    val isConnectingMode: Boolean = false, // Если true, тап по второму узлу создаёт связь
    val canvasScale: Float = 1f,
    val canvasOffset: Offset = Offset.Zero,
    val statusMessage: String? = null
)

class MindMapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MindMapRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(MindMapUiState())
    val uiState: StateFlow<MindMapUiState> = _uiState.asStateFlow()

    init {
        refreshMapList()
    }

    /**
     * Обновляет список всех сохранённых карт из файлового хранилища.
     */
    fun refreshMapList() {
        viewModelScope.launch {
            val list = repository.getAllMapSummaries()
            _uiState.update { it.copy(mapList = list) }
        }
    }

    /**
     * Создаёт новую карту с одним корневым узлом в центре и открывает редактор.
     */
    fun createNewMap(title: String) {
        val cleanTitle = title.trim().ifEmpty { "Без названия" }
        val rootNode = MindMapNode(
            text = cleanTitle,
            x = 0f,
            y = 0f,
            backgroundColor = 0xFFBBDEFB,
            textColor = 0xFF0D47A1,
            shape = NodeShape.ROUNDED_RECT,
            fontSize = 18f
        )
        val newDoc = MindMapDocument(
            title = cleanTitle,
            nodes = listOf(rootNode),
            connections = emptyList()
        )
        viewModelScope.launch {
            val savedDoc = repository.saveMap(newDoc)
            val list = repository.getAllMapSummaries()
            _uiState.update {
                it.copy(
                    screenState = ScreenState.Editor,
                    currentDocument = savedDoc,
                    mapList = list,
                    selectedNodeId = rootNode.id,
                    selectedConnectionId = null,
                    isConnectingMode = false,
                    canvasScale = 1f,
                    canvasOffset = Offset.Zero
                )
            }
        }
    }

    /**
     * Открывает выбранную карту из списка в редакторе.
     */
    fun openMap(id: String) {
        viewModelScope.launch {
            val doc = repository.loadMap(id) ?: return@launch
            _uiState.update {
                it.copy(
                    screenState = ScreenState.Editor,
                    currentDocument = doc,
                    selectedNodeId = null,
                    selectedConnectionId = null,
                    isConnectingMode = false,
                    canvasScale = 1f,
                    canvasOffset = Offset.Zero
                )
            }
        }
    }

    /**
     * Переименовывает карту из списка на главном экране.
     */
    fun renameMap(id: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameMap(id, newTitle)
            refreshMapList()
        }
    }

    /**
     * Удаляет карту из файлового хранилища.
     */
    fun deleteMap(id: String) {
        viewModelScope.launch {
            repository.deleteMap(id)
            refreshMapList()
        }
    }

    /**
     * Сохраняет текущую открытую карту вручную.
     */
    fun saveCurrentMap(showNotification: Boolean = true) {
        val doc = _uiState.value.currentDocument ?: return
        viewModelScope.launch {
            val saved = repository.saveMap(doc)
            val list = repository.getAllMapSummaries()
            _uiState.update {
                it.copy(
                    currentDocument = saved,
                    mapList = list,
                    statusMessage = if (showNotification) "Карта «${saved.title}» сохранена" else null
                )
            }
        }
    }

    /**
     * Автоматически сохраняет текущую карту и возвращает пользователя в меню карт.
     */
    fun saveAndExitEditor() {
        val doc = _uiState.value.currentDocument
        viewModelScope.launch {
            if (doc != null) {
                repository.saveMap(doc)
            }
            val list = repository.getAllMapSummaries()
            _uiState.update {
                it.copy(
                    screenState = ScreenState.MapList,
                    currentDocument = null,
                    mapList = list,
                    selectedNodeId = null,
                    selectedConnectionId = null,
                    isConnectingMode = false
                )
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    // --- Управление холстом (панорамирование и зум) ---

    fun updateCanvasTransform(scale: Float, offset: Offset) {
        _uiState.update {
            it.copy(
                canvasScale = scale.coerceIn(0.3f, 3.0f),
                canvasOffset = offset
            )
        }
    }

    fun resetCanvasView() {
        _uiState.update {
            it.copy(canvasScale = 1f, canvasOffset = Offset.Zero)
        }
    }

    // --- Управление узлами ---

    /**
     * Добавляет новый узел рядом с центром текущего видимого экрана.
     */
    fun addNode(viewportCenterWorld: Offset) {
        val doc = _uiState.value.currentDocument ?: return
        // Небольшое смещение, чтобы новые узлы не перекрывали друг друга идеально
        val jitter = (doc.nodes.size % 5) * 24f
        val newNode = MindMapNode(
            text = "Узел ${doc.nodes.size + 1}",
            x = viewportCenterWorld.x - 80f + jitter,
            y = viewportCenterWorld.y - 36f + jitter
        )
        _uiState.update {
            it.copy(
                currentDocument = doc.copy(nodes = doc.nodes + newNode),
                selectedNodeId = newNode.id,
                selectedConnectionId = null,
                isConnectingMode = false
            )
        }
    }

    /**
     * Обрабатывает нажатие по узлу: либо выделяет его, либо создаёт связь, если активен режим соединения.
     */
    fun onNodeTapped(nodeId: String) {
        val state = _uiState.value
        val doc = state.currentDocument ?: return

        if (state.isConnectingMode && state.selectedNodeId != null) {
            val fromId = state.selectedNodeId
            if (fromId != nodeId) {
                // Проверяем, нет ли уже такой же связи между этими двумя узлами
                val exists = doc.connections.any {
                    (it.fromNodeId == fromId && it.toNodeId == nodeId) ||
                        (it.fromNodeId == nodeId && it.toNodeId == fromId)
                }
                if (!exists) {
                    val newConn = MindMapConnection(fromNodeId = fromId, toNodeId = nodeId)
                    _uiState.update {
                        it.copy(
                            currentDocument = doc.copy(connections = doc.connections + newConn),
                            selectedNodeId = null,
                            selectedConnectionId = newConn.id,
                            isConnectingMode = false
                        )
                    }
                    return
                } else {
                    _uiState.update {
                        it.copy(
                            isConnectingMode = false,
                            statusMessage = "Связь между этими узлами уже существует"
                        )
                    }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(
                selectedNodeId = nodeId,
                selectedConnectionId = null,
                isConnectingMode = false
            )
        }
    }

    /**
     * Переключает режим создания связи от текущего выбранного узла.
     */
    fun toggleConnectingMode() {
        val state = _uiState.value
        if (state.selectedNodeId == null) {
            _uiState.update {
                it.copy(statusMessage = "Сначала выберите исходный узел")
            }
            return
        }
        _uiState.update {
            val nextMode = !it.isConnectingMode
            it.copy(
                isConnectingMode = nextMode,
                statusMessage = if (nextMode) "Тапните по второму узлу, чтобы соединить их" else null
            )
        }
    }

    /**
     * Перемещает узел на вектор (dx, dy) в мировых координатах холста.
     */
    fun moveNode(nodeId: String, deltaWorldX: Float, deltaWorldY: Float) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedNodes = doc.nodes.map { node ->
            if (node.id == nodeId) {
                node.copy(x = node.x + deltaWorldX, y = node.y + deltaWorldY)
            } else {
                node
            }
        }
        _uiState.update {
            it.copy(currentDocument = doc.copy(nodes = updatedNodes))
        }
    }

    /**
     * Обновляет свойства узла (текст, цвета, форму, размер шрифта).
     */
    fun updateNode(updatedNode: MindMapNode) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedNodes = doc.nodes.map { if (it.id == updatedNode.id) updatedNode else it }
        _uiState.update {
            it.copy(currentDocument = doc.copy(nodes = updatedNodes))
        }
    }

    /**
     * Удаляет узел и все привязанные к нему связи.
     */
    fun deleteNode(nodeId: String) {
        val doc = _uiState.value.currentDocument ?: return
        val remainingNodes = doc.nodes.filterNot { it.id == nodeId }
        val remainingConnections = doc.connections.filterNot {
            it.fromNodeId == nodeId || it.toNodeId == nodeId
        }
        _uiState.update {
            it.copy(
                currentDocument = doc.copy(
                    nodes = remainingNodes,
                    connections = remainingConnections
                ),
                selectedNodeId = if (it.selectedNodeId == nodeId) null else it.selectedNodeId,
                isConnectingMode = false
            )
        }
    }

    // --- Управление связями ---

    fun selectConnection(connectionId: String?) {
        _uiState.update {
            it.copy(
                selectedConnectionId = connectionId,
                selectedNodeId = null,
                isConnectingMode = false
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedNodeId = null,
                selectedConnectionId = null,
                isConnectingMode = false
            )
        }
    }

    /**
     * Перемещает контрольную точку связи для изменения изгиба кривой Безье.
     */
    fun moveConnectionControlPoint(connectionId: String, deltaWorldX: Float, deltaWorldY: Float) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedConnections = doc.connections.map { conn ->
            if (conn.id == connectionId) {
                conn.copy(
                    controlOffsetX = conn.controlOffsetX + deltaWorldX,
                    controlOffsetY = conn.controlOffsetY + deltaWorldY
                )
            } else {
                conn
            }
        }
        _uiState.update {
            it.copy(currentDocument = doc.copy(connections = updatedConnections))
        }
    }

    /**
     * Сбрасывает изгиб связи (делает линию прямой).
     */
    fun resetConnectionCurve(connectionId: String) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedConnections = doc.connections.map { conn ->
            if (conn.id == connectionId) {
                conn.copy(controlOffsetX = 0f, controlOffsetY = 0f)
            } else {
                conn
            }
        }
        _uiState.update {
            it.copy(currentDocument = doc.copy(connections = updatedConnections))
        }
    }

    /**
     * Обновляет параметры связи (цвет, толщину, стиль, стрелки, текстовую метку).
     */
    fun updateConnection(updatedConnection: MindMapConnection) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedList = doc.connections.map {
            if (it.id == updatedConnection.id) updatedConnection else it
        }
        _uiState.update {
            it.copy(currentDocument = doc.copy(connections = updatedList))
        }
    }

    /**
     * Удаляет выбранную связь.
     */
    fun deleteConnection(connectionId: String) {
        val doc = _uiState.value.currentDocument ?: return
        val updatedList = doc.connections.filterNot { it.id == connectionId }
        _uiState.update {
            it.copy(
                currentDocument = doc.copy(connections = updatedList),
                selectedConnectionId = null
            )
        }
    }
}
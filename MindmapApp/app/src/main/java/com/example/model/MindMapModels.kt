package com.example.mindmap.model

import java.util.UUID

/**
 * Форма узла интеллект-карты.
 */
enum class NodeShape(val displayName: String) {
    RECTANGLE("Прямоугольник"),
    ROUNDED_RECT("Скруглённый"),
    OVAL("Овал / Круг")
}

/**
 * Стиль линии связи между узлами.
 */
enum class LineStyle(val displayName: String) {
    SOLID("Сплошная"),
    DASHED("Пунктирная"),
    DOTTED("Точечная")
}

/**
 * Направление стрелок на концах связи.
 */
enum class ArrowType(val displayName: String) {
    NONE("Без стрелок"),
    END("В конце"),
    START("В начале"),
    BOTH("Двусторонняя")
}

/**
 * Модель узла (карточки) на холсте.
 * Координаты x, y хранятся в мировой системе координат холста.
 * Цвета хранятся в формате ARGB Long для простой сериализации в JSON.
 */
data class MindMapNode(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Новый узел",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 160f,
    val height: Float = 72f,
    val backgroundColor: Long = 0xFFE3F2FD, // Светло-голубой по умолчанию
    val textColor: Long = 0xFF0D47A1,       // Тёмно-синий текст
    val shape: NodeShape = NodeShape.ROUNDED_RECT,
    val fontSize: Float = 16f
)

/**
 * Модель связи между двумя узлами.
 * controlOffsetX и controlOffsetY задают смещение контрольной точки кривой Безье
 * относительно геометрического центра (середины) между двумя соединёнными узлами.
 */
data class MindMapConnection(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val toNodeId: String,
    val color: Long = 0xFF546E7A,
    val strokeWidth: Float = 4f,
    val lineStyle: LineStyle = LineStyle.SOLID,
    val arrowType: ArrowType = ArrowType.END,
    val label: String = "",
    val controlOffsetX: Float = 0f,
    val controlOffsetY: Float = 0f
)

/**
 * Полная модель документа интеллект-карты, сохраняемая в отдельный JSON-файл.
 */
data class MindMapDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastModified: Long = System.currentTimeMillis(),
    val nodes: List<MindMapNode> = emptyList(),
    val connections: List<MindMapConnection> = emptyList()
)

/**
 * Облегчённая модель для отображения элемента в списке на главном экране.
 */
data class MindMapSummary(
    val id: String,
    val title: String,
    val lastModified: Long,
    val nodeCount: Int,
    val connectionCount: Int
)
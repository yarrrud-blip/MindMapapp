package com.example.mindmap.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.mindmap.model.MindMapNode
import kotlin.math.hypot

/**
 * Набор предустановленных цветов для фона узлов, текста и линий.
 */
val PresetNodeColors = listOf(
    0xFFE3F2FDL, // Светло-голубой
    0xFFE8F5E9L, // Светло-зелёный
    0xFFFFFDE7L, // Светло-жёлтый
    0xFFFFF3E0L, // Светло-оранжевый
    0xFFFCE4ECL, // Розовый
    0xFFF3E5F5L, // Лавандовый
    0xFFECEFF1L, // Светло-серый
    0xFF263238L, // Тёмный графит
    0xFF1565C0L, // Насыщенный синий
    0xFF2E7D32L, // Насыщенный зелёный
    0xFFC62828L, // Насыщенный красный
    0xFF6A1B9AL  // Насыщенный фиолетовый
)

val PresetTextAndLineColors = listOf(
    0xFF212121L, // Почти чёрный
    0xFFFFFFFFL, // Белый
    0xFF546E7AL, // Серо-синий
    0xFF0D47A1L, // Тёмно-синий
    0xFF1B5E20L, // Тёмно-зелёный
    0xFFB71C1CL, // Тёмно-красный
    0xFFE65100L, // Тёмно-оранжевый
    0xFF4A148CL, // Тёмно-фиолетовый
    0xFF00838FL, // Бирюзовый
    0xFFD81B60L  // Малиновый
)

/**
 * Горизонтальная палитра выбора цвета с индикатором активного цвета.
 */
@Composable
fun ColorPaletteRow(
    colors: List<Long>,
    selectedColor: Long,
    onColorSelected: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        colors.forEach { colorLong ->
            val composeColor = Color(colorLong)
            val isSelected = selectedColor == colorLong
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(composeColor)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorLong) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    val checkColor = if (composeColor.luminance() > 0.5f) Color.Black else Color.White
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Выбрано",
                        tint = checkColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Геометрические утилиты для расчёта квадратичных кривых Безье и попадания тапа по линии.
 */
object BezierMath {

    /**
     * Возвращает центр узла в мировых координатах.
     */
    fun nodeCenter(node: MindMapNode): Offset =
        Offset(node.x + node.width / 2f, node.y + node.height / 2f)

    /**
     * Вычисляет точку на квадратичной кривой Безье при параметре t в диапазоне [0..1].
     * B(t) = (1-t)^2 * P0 + 2(1-t)t * P1 + t^2 * P2
     */
    fun evaluateQuadraticBezier(p0: Offset, p1: Offset, p2: Offset, t: Float): Offset {
        val oneMinusT = 1f - t
        val x = oneMinusT * oneMinusT * p0.x + 2f * oneMinusT * t * p1.x + t * t * p2.x
        val y = oneMinusT * oneMinusT * p0.y + 2f * oneMinusT * t * p1.y + t * t * p2.y
        return Offset(x, y)
    }

    /**
     * Вычисляет минимальное расстояние от точки point до квадратичной кривой Безье
     * путём аппроксимации кривой отрезками.
     */
    fun distanceToQuadraticBezier(
        point: Offset,
        p0: Offset,
        p1: Offset,
        p2: Offset,
        steps: Int = 24
    ): Float {
        var minDistance = Float.MAX_VALUE
        var prev = p0
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            val curr = evaluateQuadraticBezier(p0, p1, p2, t)
            val dist = distanceToSegment(point, prev, curr)
            if (dist < minDistance) {
                minDistance = dist
            }
            prev = curr
        }
        return minDistance
    }

    private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    /**
     * Находит параметр t, при котором кривая Безье выходит за пределы прямоугольника узла.
     * Это нужно, чтобы стрелки на концах связей рисовались прямо у границы карточки узла,
     * а не скрывались под самой карточкой в её центре.
     */
    fun findBoundaryT(
        node: MindMapNode,
        p0: Offset,
        p1: Offset,
        p2: Offset,
        isStartNode: Boolean
    ): Float {
        val pad = 6f
        val left = node.x - pad
        val right = node.x + node.width + pad
        val top = node.y - pad
        val bottom = node.y + node.height + pad

        val steps = 60
        if (isStartNode) {
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val pt = evaluateQuadraticBezier(p0, p1, p2, t)
                if (pt.x < left || pt.x > right || pt.y < top || pt.y > bottom) {
                    return t
                }
            }
            return 0.2f
        } else {
            for (i in steps downTo 0) {
                val t = i.toFloat() / steps
                val pt = evaluateQuadraticBezier(p0, p1, p2, t)
                if (pt.x < left || pt.x > right || pt.y < top || pt.y > bottom) {
                    return t
                }
            }
            return 0.8f
        }
    }
}
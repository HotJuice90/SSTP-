package kittoku.osc.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp


/**
 * Схема «телефон — дом». Пунктир с крестом, когда туннеля нет, и бегущая
 * подсветка, когда он есть: состояние должно читаться без чтения текста.
 */
@Composable
internal fun ConnectionDiagram(
    isConnected: Boolean,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outline

    val transition = rememberInfiniteTransition(label = "link")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBusy) 900 else 1600),
            // Пока идёт подключение — бежим в одну сторону, как «пытаемся достучаться».
            // Когда туннель поднят — ходим туда-сюда: связь двусторонняя.
            repeatMode = if (isBusy) RepeatMode.Restart else RepeatMode.Reverse,
        ),
        label = "phase",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth(),
    ) {
        DiagramNode(isActive = true) {
            Icon(
                imageVector = Icons.Filled.Smartphone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(140.dp)
                .height(56.dp)
                .drawBehind {
                    val y = size.height / 2
                    val start = Offset(0f, y)
                    val end = Offset(size.width, y)

                    if (isConnected || isBusy) {
                        drawLine(
                            color = activeColor.copy(alpha = 0.35f),
                            start = start,
                            end = end,
                            strokeWidth = 10f,
                            cap = StrokeCap.Round,
                        )

                        if (isBusy) {
                            // Подключение: комета с головой, всегда от телефона к дому.
                            val head = size.width * phase
                            val tail = (head - size.width * 0.35f).coerceAtLeast(0f)

                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, activeColor),
                                    startX = tail,
                                    endX = head,
                                ),
                                start = Offset(tail, y),
                                end = Offset(head, y),
                                strokeWidth = 10f,
                                cap = StrokeCap.Round,
                            )
                        } else {
                            // Туннель поднят: пятно без головы ходит туда-обратно и
                            // одинаково выглядит в обе стороны.
                            val center = size.width * phase
                            val half = size.width * 0.22f
                            val from = (center - half).coerceAtLeast(0f)
                            val to = (center + half).coerceAtMost(size.width)

                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, activeColor, Color.Transparent),
                                    startX = from,
                                    endX = to,
                                ),
                                start = Offset(from, y),
                                end = Offset(to, y),
                                strokeWidth = 10f,
                                cap = StrokeCap.Round,
                            )
                        }
                    } else {
                        drawLine(
                            color = idleColor,
                            start = start,
                            end = end,
                            strokeWidth = 6f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 16f)),
                        )
                    }
                },
        ) {
            if (!isConnected && !isBusy) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.dp, idleColor, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        DiagramNode(isActive = isConnected) {
            Icon(
                imageVector = Icons.Filled.Home,
                contentDescription = null,
                tint = if (isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun DiagramNode(isActive: Boolean, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(
                width = 1.dp,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .padding(14.dp),
    ) {
        content()
    }
}

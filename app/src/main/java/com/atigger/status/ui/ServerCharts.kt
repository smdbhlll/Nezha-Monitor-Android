package com.atigger.status.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atigger.status.data.formatBytes
import java.util.Locale

/**
 * A circular gauge that displays a percentage value with an arc indicator.
 *
 * @param value Progress value from 0-100, or null for placeholder.
 * @param label Label displayed below the gauge.
 * @param color Color of the arc and percentage text.
 * @param modifier Modifier for the composable.
 * @param size Diameter of the gauge circle.
 * @param strokeWidth Width of the arc stroke.
 */
@Composable
fun CircularGauge(
    value: Float?,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp
) {
    val trackColor = color.copy(alpha = 0.15f)
    val textMeasurer = rememberTextMeasurer()
    val placeholderText = "--"
    val valueText = value?.let {
        "%.1f%%".format(Locale.US, it)
    } ?: placeholderText

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val strokePx = strokeWidth.toPx()
                val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
                val topLeft = Offset(strokePx / 2f, strokePx / 2f)

                // Background track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Progress arc
                if (value != null) {
                    val sweepAngle = (value.coerceIn(0f, 100f) / 100f) * 360f
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            // Center text
            val centerTextStyle = TextStyle(
                fontSize = 10.sp,
                color = if (value != null) color else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            val textLayoutResult = textMeasurer.measure(valueText, centerTextStyle)
            Canvas(modifier = Modifier.size(size)) {
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        (this.size.width - textLayoutResult.size.width) / 2f,
                        (this.size.height - textLayoutResult.size.height) / 2f
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A horizontal bar showing used/total ratio with text below.
 *
 * @param used Used bytes, or null for placeholder.
 * @param total Total bytes, or null for placeholder.
 * @param label Label displayed above the bar.
 * @param modifier Modifier for the composable.
 * @param barColor Color of the filled portion.
 */
@Composable
fun UsageBar(
    used: Long?,
    total: Long?,
    label: String,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val fraction = if (used != null && total != null && total > 0) {
        (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val trackColor = barColor.copy(alpha = 0.15f)
    val usedText = used?.let { formatBytes(it) } ?: "--"
    val totalText = total?.let { formatBytes(it) } ?: "--"

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))

        // Bar
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        ) {
            val cornerRadius = size.height / 2f
            // Track
            drawRoundRect(
                color = trackColor,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                size = size
            )
            // Filled portion
            if (fraction > 0f) {
                drawRoundRect(
                    color = barColor,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius),
                    size = Size(size.width * fraction, size.height)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$usedText / $totalText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Shows download and upload speeds with directional arrow symbols.
 *
 * @param downloadSpeed Download speed in bytes/s, or null.
 * @param uploadSpeed Upload speed in bytes/s, or null.
 * @param modifier Modifier for the composable.
 */
@Composable
fun SpeedIndicator(
    downloadSpeed: Long?,
    uploadSpeed: Long?,
    modifier: Modifier = Modifier
) {
    val downloadColor = Color(0xFF1976D2)
    val uploadColor = Color(0xFFE65100)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Download
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "▼",
                color = downloadColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = downloadSpeed?.let { "${formatBytes(it)}/s" } ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = downloadColor
            )
        }

        // Upload
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "▲",
                color = uploadColor,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = uploadSpeed?.let { "${formatBytes(it)}/s" } ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = uploadColor
            )
        }
    }
}

/**
 * A Row of three [CircularGauge]s for CPU, Memory, and Disk usage.
 *
 * @param cpuPercent CPU usage percentage (0-100), or null.
 * @param memoryPercent Memory usage percentage (0-100), or null.
 * @param diskPercent Disk usage percentage (0-100), or null.
 * @param modifier Modifier for the composable.
 */
@Composable
fun ResourceGrid(
    cpuPercent: Float?,
    memoryPercent: Float?,
    diskPercent: Float?,
    modifier: Modifier = Modifier
) {
    val cpuColor = MaterialTheme.colorScheme.primary
    val memoryColor = Color(0xFF6750A4)
    val diskColor = Color(0xFF006B5E)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CircularGauge(
            value = cpuPercent,
            label = "CPU",
            color = cpuColor
        )
        CircularGauge(
            value = memoryPercent,
            label = "Memory",
            color = memoryColor
        )
        CircularGauge(
            value = diskPercent,
            label = "Disk",
            color = diskColor
        )
    }
}

/**
 * Shows total network transfer (in/out) as text.
 *
 * @param netInTransfer Total download bytes, or null.
 * @param netOutTransfer Total upload bytes, or null.
 * @param modifier Modifier for the composable.
 */
@Composable
fun NetworkTrafficRow(
    netInTransfer: Long?,
    netOutTransfer: Long?,
    modifier: Modifier = Modifier
) {
    val downloadColor = Color(0xFF1976D2)
    val uploadColor = Color(0xFFE65100)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column {
            Text(
                text = "Total In",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = netInTransfer?.let { formatBytes(it) } ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = downloadColor
            )
        }
        Column {
            Text(
                text = "Total Out",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = netOutTransfer?.let { formatBytes(it) } ?: "--",
                style = MaterialTheme.typography.bodyMedium,
                color = uploadColor
            )
        }
    }
}

/**
 * Shows TCP, UDP, and process counts as small badge-like chips.
 *
 * @param tcp TCP connection count, or null.
 * @param udp UDP connection count, or null.
 * @param process Process count, or null.
 * @param modifier Modifier for the composable.
 */
@Composable
fun ConnectionRow(
    tcp: Int?,
    udp: Int?,
    process: Int?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ConnectionBadge(label = "TCP", value = tcp)
        ConnectionBadge(label = "UDP", value = udp)
        ConnectionBadge(label = "Proc", value = process)
    }
}

@Composable
private fun ConnectionBadge(label: String, value: Int?) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = value?.toString() ?: "--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}

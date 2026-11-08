package site.zhizhang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 悬浮「＋记一笔」按钮会盖住列表最后一行，给滚动列表留出安全底部 */
val FabSafeBottomPadding = PaddingValues(bottom = 88.dp)

fun money(cents: Long): String = "%,.2f".format(cents / 100.0)
fun formatTime(timestamp: Long): String = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA).format(Date(timestamp))

@Composable
fun SourceIcon(sourceName: String) {
    val (short, background, foreground) = when (sourceName) {
        "微信支付" -> Triple("微", Color(0xFFE5F5E9), Color(0xFF249657))
        "支付宝" -> Triple("支", Color(0xFFE6F2FE), Color(0xFF2185DF))
        "淘宝" -> Triple("淘", Color(0xFFFFF0E5), Color(0xFFEE7828))
        "京东" -> Triple("京", Color(0xFFFEECEB), Color(0xFFD9443E))
        "拼多多" -> Triple("拼", Color(0xFFFEECEB), Color(0xFFD9443E))
        "美团" -> Triple("美", Color(0xFFFFF6D8), Color(0xFF9A7C13))
        "抖音" -> Triple("抖", Color(0xFFECECEF), Color(0xFF202522))
        else -> Triple(sourceName.take(1).ifBlank { "账" }, Mint, Green)
    }
    Box(
        modifier = Modifier.size(42.dp).background(background, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center
    ) { Text(short, color = foreground, fontWeight = FontWeight.Bold) }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(width = 0.7.dp)
    ) { content() }
}

@Composable
fun EntryRow(entry: LedgerEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SourceIcon(entry.sourceName)
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatTime(entry.occurredAt), style = MaterialTheme.typography.bodySmall)
                if (entry.status == EntryStatus.PENDING) Text("待确认", color = Amber, style = MaterialTheme.typography.bodySmall)
                else if (entry.rawNotification.isNotBlank()) Text("自动识别", color = Green, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (entry.type.name == "INCOME") "+" else "-"}¥${money(entry.amountCents)}",
                color = if (entry.type.name == "INCOME") Green else Ink,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(entry.category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun EmptyState(title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Muted, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        action?.invoke()
    }
}

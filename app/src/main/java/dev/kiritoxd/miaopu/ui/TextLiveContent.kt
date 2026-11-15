package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiritoxd.miaopu.data.basketball.TextLiveEntry
import dev.kiritoxd.miaopu.data.basketball.TextLivePage
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Newest entries first, matching how JRs follow a live game. */
internal fun newestFirst(entries: List<TextLiveEntry>): List<TextLiveEntry> =
    entries.asReversed()

internal fun LazyListScope.textLiveFeedItems(live: LoadState<TextLivePage>) {
    when (live) {
        is LoadState.Loading -> item(key = "text-live-loading", contentType = "text-live-notice") {
            TextLiveNotice("正在加载文字直播", loading = true)
        }
        is LoadState.Failed -> item(key = "text-live-failed", contentType = "text-live-notice") {
            TextLiveNotice(live.message)
        }
        is LoadState.Ready -> textLiveEntries(live.value)
    }
}

internal fun LazyListScope.textLiveEntries(page: TextLivePage) {
    item(key = "text-live-header", contentType = "text-live-notice") {
        TextLiveNotice(
            (page.homeTeamName ?: "主队") + " vs " + (page.awayTeamName ?: "客队") + " · 文字实录",
        )
    }
    items(newestFirst(page.entries), key = { it.id }, contentType = { "text-live-row" }) { entry ->
        TextLiveRow(entry)
    }
}

@Composable
private fun TextLiveRow(entry: TextLiveEntry) {
    val colors = MiuixTheme.colorScheme
    val period = isPeriodMarker(entry.text)
    Column(Modifier.fillMaxWidth()) {
        if (period) {
            Box(Modifier.padding(top = 12.dp, bottom = 2.dp)) {
                Text(
                    text = entry.text,
                    modifier = Modifier
                        .background(colors.primary.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = entry.timeLabel,
                modifier = Modifier.width(46.dp),
                fontSize = 12.sp,
                color = colors.onSurfaceVariantSummary,
            )
            Column(Modifier.weight(1f)) {
                if (!entry.teamName.isNullOrBlank()) {
                    Text(
                        text = entry.teamName,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariantSummary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    text = entry.text,
                    fontSize = 14.sp,
                    color = colors.onSurface,
                    lineHeight = 20.sp,
                    fontWeight = if (period) FontWeight.Medium else FontWeight.Normal,
                )
            }
            if (!entry.score.isNullOrBlank() && entry.score.replace("-", "").isNotBlank()) {
                Text(
                    text = entry.score,
                    modifier = Modifier.padding(start = 10.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
internal fun TextLiveNotice(message: String, loading: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (loading) CircularProgressIndicator(modifier = Modifier.size(22.dp))
    }
}

internal fun isPeriodMarker(text: String): Boolean =
    text.startsWith("第") && text.take(6).contains("节") || text.contains("加时")

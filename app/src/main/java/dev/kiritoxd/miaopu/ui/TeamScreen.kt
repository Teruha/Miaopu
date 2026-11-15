package dev.kiritoxd.miaopu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.Schedule
import dev.kiritoxd.miaopu.data.ScheduleDay
import dev.kiritoxd.miaopu.data.basketball.BasketballTeamHead
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TeamScreen(viewModel: MiaopuViewModel, teamId: String, name: String, logoUrl: String?) {
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            SmallTopAppBar(
                title = name.ifBlank { "球队" },
                subtitle = "球队战绩与赛程",
                navigationIcon = {
                    TopBarIconButton(onClick = viewModel::goBack) {
                        Icon(MiaopuIcons.ChevronLeft, contentDescription = "返回比赛详情")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "team-head") { TeamHero(name, logoUrl, viewModel.teamHeadState) }
            when (val state = viewModel.teamScheduleState) {
                LoadState.Loading -> item(key = "schedule-loading") {
                    DetailNotice("正在加载球队赛程", loading = true)
                }
                is LoadState.Failed -> item(key = "schedule-failed") {
                    DetailNotice(state.message, onRetry = if (state.retryable) viewModel::retry else null)
                }
                is LoadState.Ready -> teamScheduleItems(state.value, viewModel::openMatch)
            }
        }
    }
}

@Composable
private fun TeamHero(name: String, logoUrl: String?, headState: LoadState<BasketballTeamHead>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        insideMargin = PaddingValues(16.dp),
        cornerRadius = 20.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center) {
                if (logoUrl != null) {
                    coil3.compose.AsyncImage(logoUrl, null, Modifier.fillMaxSize())
                } else {
                    Text(name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                val head = (headState as? LoadState.Ready)?.value
                Text(
                    text = head?.fullName ?: "",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val state = headState) {
            LoadState.Loading -> Text(
                "正在加载战绩", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            is LoadState.Failed -> Text(
                state.message, fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            is LoadState.Ready -> TeamRecordGrid(state.value)
        }
    }
}

@Composable
private fun TeamRecordGrid(head: BasketballTeamHead) {
    val colors = MiuixTheme.colorScheme
    Column {
        Text(
            text = recordLine(head),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = listOfNotNull(
                head.rank?.let { "排名第 $it" },
                head.homeWins?.let { w -> head.homeLosses?.let { l -> "主场 $w 胜 $l 负" } },
                head.awayWins?.let { w -> head.awayLosses?.let { l -> "客场 $w 胜 $l 负" } },
                head.arena,
            ).joinToString(" · "),
            fontSize = 13.sp,
            color = colors.onSurfaceVariantSummary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun recordLine(head: BasketballTeamHead): String {
    val w = head.wins
    val l = head.losses
    return when {
        w != null && l != null -> "$w 胜 $l 负"
        else -> "暂无战绩"
    }
}

/**
 * The team schedule spans whole seasons. A fan follows the current season
 * only: games from July onward belong to the season that is starting, so
 * anything before the September tip-off of that season is history.
 */
internal fun currentSeasonStartDate(today: String): String {
    val parts = today.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return today
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return today
    val startYear = if (month >= 7) year else year - 1
    return "%04d-09-01".format(startYear)
}

internal fun teamScheduleForNow(days: List<ScheduleDay>, today: String): List<ScheduleDay> {
    val seasonStart = currentSeasonStartDate(today)
    return orderTeamScheduleDays(days.filter { it.date >= seasonStart })
}

/**
 * Reading order follows how a fan catches up: the next games first, then
 * the newest results going back.
 */
internal fun orderTeamScheduleDays(days: List<ScheduleDay>): List<ScheduleDay> {
    val upcoming = days.filter { day ->
        day.matches.isNotEmpty() && day.matches.all { it.statusCode == "NOTSTARTED" }
    }.sortedBy(ScheduleDay::date)
    val played = days.filter { day ->
        day.matches.isNotEmpty() && !day.matches.all { it.statusCode == "NOTSTARTED" }
    }.sortedByDescending(ScheduleDay::date)
    return upcoming + played
}

private fun androidx.compose.foundation.lazy.LazyListScope.teamScheduleItems(
    schedule: Schedule,
    onMatchClick: (MatchSummary) -> Unit,
) {
    orderTeamScheduleDays(schedule.days).forEach { day ->
        item(key = "team-day-${day.date}", contentType = "schedule-day") {
            ScheduleDayBand(day, isFocused = false)
        }
        items(day.matches, key = { "${day.date}:${it.uniqueKey}" }, contentType = { "schedule-match" }) { match ->
            HupuScheduleMatchCard(match = match, onClick = { onMatchClick(match) })
        }
    }
}

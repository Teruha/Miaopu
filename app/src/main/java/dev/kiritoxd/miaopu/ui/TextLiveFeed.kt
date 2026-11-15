package dev.kiritoxd.miaopu.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kiritoxd.miaopu.data.AdapterResult
import dev.kiritoxd.miaopu.data.basketball.BasketballSource
import dev.kiritoxd.miaopu.data.basketball.TextLivePage
import dev.kiritoxd.miaopu.data.basketball.beijingDate
import dev.kiritoxd.miaopu.data.MatchSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Loads NBA play-by-play and refreshes it while the game is in progress.
 * A completed game is fetched once; a failed refresh keeps the last good page.
 */
internal class TextLiveFeed(
    private val scope: CoroutineScope,
    private val source: BasketballSource,
    private val wait: suspend (Long) -> Unit = { delay(it) },
) {
    var state: LoadState<TextLivePage> by mutableStateOf(LoadState.Loading)
        private set
    private var matchId: String? = null
    private var job: Job? = null

    fun bind(match: MatchSummary) {
        if (matchId == match.id) return
        matchId = match.id
        job?.cancel()
        state = LoadState.Loading
        job = scope.launch { run(match.id) }
    }

    fun refresh() {
        val current = matchId ?: return
        job?.cancel()
        job = scope.launch { run(current) }
    }

    fun close() {
        matchId = null
        job?.cancel()
        job = null
        state = LoadState.Loading
    }

    private suspend fun run(matchId: String) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val infoResult = source.matchInfo(matchId)
            currentCoroutineContext().ensureActive()
            val info = infoResult.data ?: return publish(infoResult)
            if (info.leagueType != "NBA") {
                state = LoadState.Failed("这场比赛暂不支持文字直播", retryable = false)
                return
            }
            val homeName = info.homeTeamName ?: return publish(infoResult)
            val awayName = info.awayTeamName ?: return publish(infoResult)
            val gameResult = source.gameNumber(beijingDate(info.beginTimeMillis), homeName, awayName)
            currentCoroutineContext().ensureActive()
            val gameNo = gameResult.data ?: return publish(gameResult)
            currentCoroutineContext().ensureActive()
            val page = source.playByPlay(gameNo).data
            currentCoroutineContext().ensureActive()
            if (page != null) {
                // A shorter page is a server hiccup, not newer content.
                val previous = (state as? LoadState.Ready)?.value
                state = if (previous != null && previous.entries.size > page.entries.size) {
                    LoadState.Ready(previous)
                } else {
                    LoadState.Ready(page)
                }
            }
            if (!info.inProgress) return
            wait(REFRESH_MILLIS)
        }
    }

    private fun publish(result: AdapterResult<*>) {
        if (state !is LoadState.Ready) {
            state = LoadState.Failed(
                result.error?.message ?: "暂时无法读取文字直播",
                result.error?.retryable ?: true,
            )
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 3_000L
    }
}

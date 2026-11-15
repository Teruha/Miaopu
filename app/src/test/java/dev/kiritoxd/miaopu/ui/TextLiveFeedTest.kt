package dev.kiritoxd.miaopu.ui

import dev.kiritoxd.miaopu.data.AdapterResult
import dev.kiritoxd.miaopu.data.AdapterStatus
import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.basketball.BasketballMatchInfo
import dev.kiritoxd.miaopu.data.basketball.BasketballSource
import dev.kiritoxd.miaopu.data.basketball.BasketballTeamHead
import dev.kiritoxd.miaopu.data.basketball.TextLivePage
import dev.kiritoxd.miaopu.data.basketball.TextLiveEntry
import dev.kiritoxd.miaopu.data.Schedule
import dev.kiritoxd.miaopu.ui.LoadState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class TextLiveFeedTest {
    @Test fun completedMatchLoadsSinglePage() = runBlocking {
        val source = FakeSource(statusCalls = 1)
        val feed = feed(source)
        feed.bind(match("m1"))
        assertEquals(1, source.playByPlayCalls)
        val ready = feed.state as LoadState.Ready<TextLivePage>
        assertEquals(listOf("r1"), ready.value.entries.map { it.id })
    }

    @Test fun liveMatchPollsUntilCompleted() = runBlocking {
        val source = FakeSource(statusCalls = 2)
        val feed = feed(source)
        feed.bind(match("m2"))
        assertEquals(2, source.playByPlayCalls)
        assertEquals(2, source.matchInfoCalls)
        assertTrue(source.waitCalls.contains(3000L))
        assertTrue(feed.state is LoadState.Ready<*>)
    }

    @Test fun livePollingKeepsLastPageOnTransientFailure() = runBlocking {
        val source = FakeSource(statusCalls = 2, failSecondPlayByPlay = true)
        val feed = feed(source)
        feed.bind(match("m3"))
        assertTrue(feed.state is LoadState.Ready<*>)
        assertEquals(1, (feed.state as LoadState.Ready<TextLivePage>).value.entries.size)
    }

    @Test fun nonNbaMatchFailsWithoutWebRequests() = runBlocking {
        val source = FakeSource(leagueType = "CBA")
        val feed = feed(source)
        feed.bind(match("m4"))
        val failed = feed.state as LoadState.Failed
        assertFalse(failed.retryable)
        assertEquals(0, source.gameNumberCalls)
        assertEquals(0, source.playByPlayCalls)
    }

    @Test fun missingGameNumberFailsRetryably() = runBlocking {
        val source = FakeSource(gameNumberFound = false)
        val feed = feed(source)
        feed.bind(match("m5"))
        val failed = feed.state as LoadState.Failed
        assertTrue(failed.retryable)
        assertEquals(0, source.playByPlayCalls)
    }

    @Test fun switchingMatchesDiscardsEarlierResults() = runBlocking {
        val firstInfo = CompletableDeferred<AdapterResult<BasketballMatchInfo>>()
        val secondInfo = CompletableDeferred<AdapterResult<BasketballMatchInfo>>()
        val source = FakeSource(matchInfoOverride = { id ->
            withContext(kotlinx.coroutines.NonCancellable) {
                (if (id == "mA") firstInfo else secondInfo).await()
            }
        })
        val feed = feed(source)
        feed.bind(match("mA"))
        feed.bind(match("mB"))
        secondInfo.complete(AdapterResult.success("f", completedInfo("mB")))
        firstInfo.complete(AdapterResult.success("f", completedInfo("mA")))
        assertTrue(feed.state is LoadState.Ready<*>)
        assertEquals(1, source.playByPlayCalls)
        assertEquals(2, source.matchInfoCalls)
    }

    @Test fun refreshReloadsTheWholePipeline() = runBlocking {
        val source = FakeSource(statusCalls = 1)
        val feed = feed(source)
        feed.bind(match("m6"))
        val before = source.playByPlayCalls
        feed.refresh()
        assertEquals(before + 1, source.playByPlayCalls)
        assertTrue(feed.state is LoadState.Ready<*>)
    }

    private fun feed(source: FakeSource) = TextLiveFeed(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        source,
        wait = { source.waitCalls.add(it) },
    )

    private fun match(id: String) = MatchSummary(
        id = id, esport = Esport.NBA, name = "篮球常规赛", introduction = "NBA常规赛",
        status = "已结束", startTimeMillis = 1794700800000L, startTimeLabel = "08:00",
        teams = emptyList(), scoreCountText = null, outBizType = null, outBizNo = null,
        featuredPlayer = null,
    )

    private fun completedInfo(id: String) = BasketballMatchInfo(
        matchId = id, status = "COMPLETED", leagueType = "NBA", gdcId = 1,
        homeTeamId = "h", awayTeamId = "a", homeTeamName = "掘金", awayTeamName = "鹈鹕",
        beginTimeMillis = 1794700800000L, openLive = true,
    )

    private class FakeSource(
        private val statusCalls: Int = 1,
        private val leagueType: String = "NBA",
        private val gameNumberFound: Boolean = true,
        private val failSecondPlayByPlay: Boolean = false,
        private val matchInfoOverride: (suspend (String) -> AdapterResult<BasketballMatchInfo>)? = null,
    ) : BasketballSource {
        var matchInfoCalls = 0
        var gameNumberCalls = 0
        var playByPlayCalls = 0
        val waitCalls = mutableListOf<Long>()

        override suspend fun matchInfo(matchId: String): AdapterResult<BasketballMatchInfo> {
            matchInfoCalls++
            matchInfoOverride?.let { return it(matchId) }
            val info = BasketballMatchInfo(matchId, if (matchInfoCalls < statusCalls) "INPROGRESS" else "COMPLETED",
                leagueType, 20073640L, "h", "a", "掘金", "鹈鹕", 1794700800000L, true)
            return AdapterResult.success("f", info)
        }

        override suspend fun gameNumber(date: String, homeName: String, awayName: String): AdapterResult<Long> {
            gameNumberCalls++
            return if (gameNumberFound) AdapterResult.success("f", 169111L)
            else AdapterResult.failure("f", AdapterStatus.TRANSIENT_FAILURE, "没有找到比赛", true)
        }

        override suspend fun playByPlay(gameNo: Long): AdapterResult<TextLivePage> {
            playByPlayCalls++
            if (failSecondPlayByPlay && playByPlayCalls == 2) {
                return AdapterResult.failure("f", AdapterStatus.TRANSIENT_FAILURE, "失败", true)
            }
            return AdapterResult.success("f", TextLivePage(listOf(TextLiveEntry("r1", "12:00", "掘金", "live", "0-0")), "掘金", "鹈鹕"))
        }

        override suspend fun teamHead(teamId: String): AdapterResult<BasketballTeamHead> =
            AdapterResult.failure("f", AdapterStatus.NOT_CONFIGURED, "unused", false)

        override suspend fun teamSchedule(teamId: String, esport: Esport): AdapterResult<Schedule> =
            AdapterResult.failure("f", AdapterStatus.NOT_CONFIGURED, "unused", false)
    }
}

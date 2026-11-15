package dev.kiritoxd.miaopu.data.basketball

import dev.kiritoxd.miaopu.data.AdapterResult
import dev.kiritoxd.miaopu.data.AdapterStatus
import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.HupuHttpTransport
import dev.kiritoxd.miaopu.data.HupuJsonParser
import dev.kiritoxd.miaopu.data.HupuUrls
import dev.kiritoxd.miaopu.data.Schedule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Read-only public endpoints; no session cookie or captured device credentials. */
internal interface BasketballSource {
    suspend fun matchInfo(matchId: String): AdapterResult<BasketballMatchInfo>
    suspend fun gameNumber(date: String, homeName: String, awayName: String): AdapterResult<Long>
    suspend fun playByPlay(gameNo: Long): AdapterResult<TextLivePage>
    suspend fun teamHead(teamId: String): AdapterResult<BasketballTeamHead>
    suspend fun teamSchedule(teamId: String, esport: Esport): AdapterResult<Schedule>
}

internal class BasketballAdapter : BasketballSource {
    override suspend fun matchInfo(matchId: String): AdapterResult<BasketballMatchInfo> = get(
        "https://games.mobileapi.hupu.com/1/8.2.99/basketballapi/singleMatch?matchId=${encode(matchId)}",
        "hupu.basketball.match",
        BasketballParser::matchInfo,
    )

    override suspend fun gameNumber(date: String, homeName: String, awayName: String): AdapterResult<Long> = html(
        "https://nba.hupu.com/games/$date",
        "hupu.basketball.gameNo",
    ) { page ->
        BasketballParser.gameNumber(page, homeName, awayName)?.gameNo
            ?: error("这一天没有找到这两支球队的比赛")
    }

    override suspend fun playByPlay(gameNo: Long): AdapterResult<TextLivePage> = html(
        "https://nba.hupu.com/games/playbyplay/$gameNo",
        "hupu.basketball.playByPlay",
    ) { page -> BasketballParser.playByPlay(page) }

    override suspend fun teamHead(teamId: String): AdapterResult<BasketballTeamHead> = get(
        "https://games.mobileapi.hupu.com/3/7.5.60/basketballapi/teamPageHeadInfo?teamId=${encode(teamId)}",
        "hupu.basketball.teamHead",
        BasketballParser::teamHead,
    )

    override suspend fun teamSchedule(teamId: String, esport: Esport): AdapterResult<Schedule> = get(
        "${HupuUrls.SCHEDULE_BASE}/1/8.2.10/matchallapi/bff/standard/getScheduleListByTagForH5" +
            "?businessType=team&businessId=${encode(teamId)}&datasource=basketball",
        "hupu.basketball.teamSchedule",
    ) { body -> HupuJsonParser.schedule(body, esport) }

    private suspend fun <T> get(
        url: String,
        source: String,
        parser: (String) -> T,
    ): AdapterResult<T> {
        val response = try {
            HupuHttpTransport.execute(Request.Builder().url(url).header("Accept", "application/json")
                .header("User-Agent", USER_AGENT).build())
        } catch (_: IOException) {
            return AdapterResult.failure(source, AdapterStatus.TRANSIENT_FAILURE, "连接失败，请稍后重试", true)
        }
        return toResult(source, response, parser)
    }

    private suspend fun <T> html(
        url: String,
        source: String,
        parser: (String) -> T,
    ): AdapterResult<T> {
        val response = try {
            HupuHttpTransport.execute(Request.Builder().url(url)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("User-Agent", DESKTOP_USER_AGENT)
                .header("Referer", "https://nba.hupu.com/games").build())
        } catch (_: IOException) {
            return AdapterResult.failure(source, AdapterStatus.TRANSIENT_FAILURE, "连接失败，请稍后重试", true)
        }
        return toResult(source, response, parser)
    }

    private suspend fun <T> toResult(
        source: String,
        response: dev.kiritoxd.miaopu.data.HupuHttpResponse,
        parser: (String) -> T,
    ): AdapterResult<T> = withContext(Dispatchers.Default) {
        when {
            response.status !in 200..299 -> AdapterResult.failure(
                source, AdapterStatus.TRANSIENT_FAILURE, "虎扑服务暂时不可用", true, response.status,
            )
            else -> try {
                AdapterResult.success(source, parser(response.body))
            } catch (error: IllegalStateException) {
                AdapterResult.failure(source, AdapterStatus.INVALID_RESPONSE,
                    error.message ?: "文字直播数据暂时不可用", true)
            } catch (_: org.json.JSONException) {
                AdapterResult.failure(source, AdapterStatus.INVALID_RESPONSE,
                    "数据格式暂时不可用", true)
            } catch (error: CancellationException) {
                throw error
            }
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.toString())

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Miaopu/1.0) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36"
    }
}

/** The schedule list uses Beijing dates; NBA pages publish them as-is. */
internal fun beijingDate(millis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.ofHours(8))
        .format(Instant.ofEpochMilli(millis))

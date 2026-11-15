package dev.kiritoxd.miaopu.data.basketball

import dev.kiritoxd.miaopu.data.HupuUrls
import org.json.JSONObject

internal object BasketballParser {
    fun matchInfo(body: String): BasketballMatchInfo {
        val root = JSONObject(body)
        check(root.optBoolean("success") && root.optInt("status", 200) == 200) { "暂时无法读取比赛信息" }
        val result = root.optJSONObject("result") ?: error("比赛信息暂未提供")
        val matchId = result.text("matchId") ?: error("比赛信息缺少编号")
        return BasketballMatchInfo(
            matchId = matchId,
            status = result.text("matchStatus") ?: error("比赛信息缺少状态"),
            leagueType = result.text("leagueType"),
            gdcId = if (result.isNull("gdcId")) null else result.optLong("gdcId", -1L).takeIf { it >= 0 },
            homeTeamId = result.text("homeTeamId"),
            awayTeamId = result.text("awayTeamId"),
            homeTeamName = result.text("homeTeamName"),
            awayTeamName = result.text("awayTeamName"),
            beginTimeMillis = result.optLong("chinaStartTime", result.optLong("beginTime", 0L) * 1000L),
            openLive = result.optBoolean("openLive"),
        )
    }

    fun teamHead(body: String): BasketballTeamHead {
        val root = JSONObject(body)
        check(root.optBoolean("success") && root.optInt("status", 200) == 200) { "暂时无法读取球队信息" }
        val info = root.optJSONObject("result")?.optJSONObject("info") ?: error("球队信息暂未提供")
        return BasketballTeamHead(
            teamId = info.text("teamId") ?: error("球队信息缺少编号"),
            name = info.text("name").orEmpty(),
            fullName = info.text("full_name"),
            enName = info.text("en_name"),
            arena = info.text("arena"),
            logoUrl = HupuUrls.imageUrl(info.text("logoLink")),
            rank = info.optIntOrNull("rank"),
            wins = info.optIntOrNull("wins"),
            losses = info.optIntOrNull("losses"),
            homeWins = info.optIntOrNull("homeWins"),
            homeLosses = info.optIntOrNull("homeLosses"),
            awayWins = info.optIntOrNull("awayWins"),
            awayLosses = info.optIntOrNull("awayLosses"),
        )
    }

    /**
     * The desktop `nba.hupu.com/games/playbyplay/{gameNo}` page renders one table with
     * `比分（主队-客队）` in its header, then one `<tr id>` row per event.
     */
    fun playByPlay(html: String): TextLivePage {
        val header = Regex("比分（([^（）<>]{1,20})-([^（）<>]{1,20})）").find(html)
            ?: error("文字直播数据格式已变化")
        val rows = ROW_REGEX.findAll(html).mapNotNull { match ->
            val cells = match.value.split("</td>").take(4).mapNotNull { cellText(it) }
            if (cells.size < 3) return@mapNotNull null
            val id = match.groupValues[1]
            TextLiveEntry(
                id = id,
                timeLabel = cells[0],
                teamName = cells.getOrNull(1)?.takeIf(String::isNotBlank),
                text = cells[2],
                score = cells.getOrNull(3),
            )
        }.toList()
        check(rows.isNotEmpty()) { "这场比赛还没有文字直播" }
        return TextLivePage(rows, header.groupValues[1], header.groupValues[2])
    }

    /** Each game block on the day schedule starts at `<div class="team_vs">`. */
    fun gameNumber(html: String, homeName: String, awayName: String): BasketballGameNo? {
        val blocks = html.split(TEAM_VS_DIV).drop(1)
        for (block in blocks) {
            val gameNo = GAME_NO_REGEX.find(block)?.groupValues?.get(1)?.toLongOrNull() ?: continue
            val names = TEAM_LINK_REGEX.findAll(block)
                .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
                .distinct()
                .toList()
            if (names.size < 2) continue
            val blockNames = setOf(names[0], names[1])
            if (homeName in blockNames && awayName in blockNames) {
                return BasketballGameNo(gameNo, names[0], names[1])
            }
        }
        return null
    }

    private val ROW_REGEX = Regex("""<tr id="(\d+)">(?s).*?</tr>""")
    private val TEAM_VS_DIV = Regex("""<div class="team_vs">""")
    private val GAME_NO_REGEX = Regex("""games/playbyplay/(\d+)""")
    private val TEAM_LINK_REGEX = Regex("""teams/[A-Za-z0-9_-]+">([^<>]+)</a>""")

    private fun cellText(cell: String): String? {
        val open = cell.indexOf('>')
        if (open < 0) return null
        val raw = cell.substring(open + 1)
        val value = decodeEntities(raw.replace(Regex("<[^>]*>"), "")).trim()
        return value.takeIf(String::isNotEmpty)
    }

    private val NAMED_ENTITIES = mapOf(
        "quot" to "\"", "amp" to "&", "lt" to "<", "gt" to ">",
        "apos" to "'", "nbsp" to " ", "ensp" to " ", "emsp" to " ",
        "hellip" to "…", "middot" to "·", "mdash" to "—", "ndash" to "–",
    )

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        var result = text
        for ((name, value) in NAMED_ENTITIES) {
            result = result.replace("&$name;", value)
        }
        result = Regex("&#(\\d+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.toChar()?.toString() ?: match.value
        }
        result = Regex("&#x([0-9A-Fa-f]+);").replace(result) { match ->
            match.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }?.toChar()?.toString() ?: match.value
        }
        return result
    }

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf(String::isNotEmpty)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
}

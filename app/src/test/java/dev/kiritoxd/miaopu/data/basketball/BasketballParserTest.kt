package dev.kiritoxd.miaopu.data.basketball

import org.junit.Assert.*
import org.junit.Test

class BasketballParserTest {
    private fun fixture(name: String): String =
        javaClass.getResource("/basketball/$name")!!.readText()

    @Test fun playByPlayExtractsScoreOrderAndRows() {
        val page = BasketballParser.playByPlay(fixture("playbyplay.html"))
        assertEquals("掘金", page.homeTeamName)
        assertEquals("鹈鹕", page.awayTeamName)
        assertEquals(39, page.entries.size)
        val first = page.entries.first()
        assertEquals("1794701432808", first.id)
        assertEquals("12:00", first.timeLabel)
        assertEquals("掘金", first.teamName)
        assertTrue(first.text.contains("换人"))
        assertEquals("0-0", first.score)
        // Rows keep their page order (earliest first).
        assertTrue(page.entries.asSequence().map { it.id.toLong() }.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun playByPlayDecodesHtmlEntities() {
        // Real rows use clock times like 17.0&quot; plus escaped team text.
        val html = """
            <div class="table_list_live playbyplay_td">
            <table><tr class="title bg_a"><td class="left" width="69">时间</td>
            <td width="380">比分（公牛-森林狼）</td></tr></table></div>
            <div class="table_list_live playbyplay_td table_overflow">
            <table>
            <tr id="1"><td class="tdw-1 left" width="69">17.0&quot;</td>
            <td width="69">公牛</td><td width="380">公牛失误（投篮时钟违例）&amp; 回放</td>
            <td width="157" align="center">117-106</td></tr>
            </table></div>
        """.trimIndent()
        val page = BasketballParser.playByPlay(html)
        assertEquals("17.0\"", page.entries.single().timeLabel)
        assertEquals("公牛失误（投篮时钟违例）& 回放", page.entries.single().text)
        assertEquals("公牛", page.homeTeamName)
        assertEquals("森林狼", page.awayTeamName)
    }

    @Test fun playByPlayRejectsForeignPages() {
        val exception = runCatching { BasketballParser.playByPlay("<html><body>其他页面</body></html>") }
            .exceptionOrNull()
        assertNotNull(exception)
    }

    @Test fun gameNumberFindsTheMatchedByTeamNames() {
        val no = BasketballParser.gameNumber(fixture("games-day.html"), "掘金", "鹈鹕")
        assertEquals(169111L, no?.gameNo)
        assertEquals("掘金", no?.homeTeamName)
        assertEquals("鹈鹕", no?.awayTeamName)
    }

    @Test fun gameNumberMatchesEitherTeamSide() {
        val no = BasketballParser.gameNumber(fixture("games-day.html"), "鹈鹕", "掘金")
        assertEquals(169111L, no?.gameNo)
    }

    @Test fun gameNumberReturnsNullForMissingTeams() {
        assertNull(BasketballParser.gameNumber(fixture("games-day.html"), "湖人", "勇士"))
    }

    @Test fun matchInfoReadsSingleMatchResponse() {
        val info = BasketballParser.matchInfo(fixture("single-match.json"))
        assertEquals("1537808046527348736", info.matchId)
        assertEquals("COMPLETED", info.status)
        assertEquals("NBA", info.leagueType)
        assertEquals(20073640L, info.gdcId)
        assertEquals("1901000000501296", info.homeTeamId)
        assertEquals("1901000000501301", info.awayTeamId)
        assertEquals("鹈鹕", info.homeTeamName)
        assertEquals("掘金", info.awayTeamName)
        assertEquals(1794700800000L, info.beginTimeMillis)
        assertTrue(info.openLive)
    }

    @Test fun teamHeadReadsRecordAndRank() {
        val head = BasketballParser.teamHead(fixture("team-head.json"))
        assertEquals("1901000000501296", head.teamId)
        assertEquals("鹈鹕", head.name)
        assertEquals(12, head.rank)
        assertEquals(4, head.wins)
        assertEquals(10, head.losses)
        assertEquals(3, head.homeWins)
        assertEquals(6, head.homeLosses)
        assertEquals(1, head.awayWins)
        assertEquals(4, head.awayLosses)
        assertTrue(head.logoUrl.orEmpty().startsWith("http"))
    }
}

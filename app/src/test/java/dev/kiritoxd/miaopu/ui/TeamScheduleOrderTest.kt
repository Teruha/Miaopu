package dev.kiritoxd.miaopu.ui

import dev.kiritoxd.miaopu.data.Esport
import dev.kiritoxd.miaopu.data.MatchSummary
import dev.kiritoxd.miaopu.data.ScheduleDay
import dev.kiritoxd.miaopu.data.Team
import org.junit.Assert.*
import org.junit.Test

class TeamScheduleOrderTest {
    @Test fun upcomingDaysComeFirstInChronologicalOrder() {
        val ordered = orderTeamScheduleDays(
            listOf(
                day("2026-11-01", match("a", "COMPLETED")),
                day("2026-11-17", match("b", "NOTSTARTED")),
                day("2026-11-15", match("c", "COMPLETED")),
                day("2026-11-19", match("d", "NOTSTARTED")),
            ),
        )
        assertEquals(listOf("2026-11-17", "2026-11-19", "2026-11-15", "2026-11-01"),
            ordered.map { it.date })
    }

    @Test fun completedDaysAreNewestFirst() {
        val ordered = orderTeamScheduleDays(
            listOf(
                day("2026-11-01", match("a", "COMPLETED")),
                day("2026-11-15", match("b", "COMPLETED")),
                day("2026-11-10", match("c", "COMPLETED")),
            ),
        )
        assertEquals(listOf("2026-11-15", "2026-11-10", "2026-11-01"), ordered.map { it.date })
    }

    @Test fun mixedDayCountsAsPlayed() {
        val ordered = orderTeamScheduleDays(
            listOf(
                day("2026-11-20", match("a", "NOTSTARTED"), match("b", "NOTSTARTED")),
                day("2026-11-12", match("c", "COMPLETED"), match("d", "NOTSTARTED")),
            ),
        )
        // A day with any finished match cannot be "upcoming"; it reads as history.
        assertEquals(listOf("2026-11-20", "2026-11-12"), ordered.map { it.date })
        assertTrue(ordered.first().matches.all { it.statusCode == "NOTSTARTED" })
    }

    @Test fun emptyDaysAreDropped() {
        val ordered = orderTeamScheduleDays(
            listOf(day("2026-11-01"), day("2026-11-02", match("a", "COMPLETED"))),
        )
        assertEquals(listOf("2026-11-02"), ordered.map { it.date })
    }

    private fun day(date: String, vararg matches: MatchSummary) = ScheduleDay(
        date = date, label = date, matches = matches.toList(),
    )

    private fun match(id: String, status: String) = MatchSummary(
        id = id, esport = Esport.NBA, name = "NBA常规赛", introduction = "NBA常规赛",
        status = status, startTimeMillis = 0, startTimeLabel = "08:00",
        teams = listOf(Team(id, "主", null, null, false), Team(id + "x", "客", null, null, false)),
        scoreCountText = null, outBizType = null, outBizNo = null, featuredPlayer = null,
        statusCode = status,
    )
}

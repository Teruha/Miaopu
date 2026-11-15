package dev.kiritoxd.miaopu.data.basketball

/** Aggregated match facts from `basketballapi/singleMatch`. */
internal data class BasketballMatchInfo(
    val matchId: String,
    val status: String,
    val leagueType: String?,
    val gdcId: Long?,
    val homeTeamId: String?,
    val awayTeamId: String?,
    val homeTeamName: String?,
    val awayTeamName: String?,
    val beginTimeMillis: Long,
    val openLive: Boolean,
) {
    val inProgress: Boolean get() = status == "INPROGRESS"
}

/** One play-by-play row from the desktop game page. */
internal data class TextLiveEntry(
    val id: String,
    val timeLabel: String,
    val teamName: String?,
    val text: String,
    val score: String?,
)

internal data class TextLivePage(
    val entries: List<TextLiveEntry>,
    val homeTeamName: String?,
    val awayTeamName: String?,
)

/** Team record summary from `basketballapi/teamPageHeadInfo`. */
internal data class BasketballTeamHead(
    val teamId: String,
    val name: String,
    val fullName: String?,
    val enName: String?,
    val arena: String?,
    val logoUrl: String?,
    val rank: Int?,
    val wins: Int?,
    val losses: Int?,
    val homeWins: Int?,
    val homeLosses: Int?,
    val awayWins: Int?,
    val awayLosses: Int?,
)

/** The desktop play-by-play page number located by team names on the day schedule. */
internal data class BasketballGameNo(
    val gameNo: Long,
    val homeTeamName: String?,
    val awayTeamName: String?,
)

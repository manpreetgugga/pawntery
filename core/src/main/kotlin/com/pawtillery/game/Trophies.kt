package com.pawtillery.game

/** Trophies: one-time goals, each paying a treat reward when first earned. */
enum class Trophy(val title: String, val description: String, val reward: Int) {
    FIRST_WIN("First Splat", "Win a game against the computer", 20),
    CHAMPION("Champion Slayer", "Beat the Champion computer", 50),
    SHARPSHOOTER("Sharpshooter", "Win a full match with 7 or more hits", 40),
    SPOTLESS("Squeaky Clean", "Win without getting messy at all", 40),
    HOT_STREAK("Hot Streak", "Win 3 games in a row", 30),
    BOOMERANG("Boomerang Pro", "Land a Boomerang! trick shot", 20),
    BANK_SHOT("Bank Robber", "Land a Bank shot! trick shot", 20),
    TRIPLE("Triple Threat", "Land a Triple! trick shot", 30),
    TRICKSTER("Trickster", "Land 10 trick shots in total", 30),
    STAR_COLLECTOR("Star Collector", "Earn 15 Trick Shot stars", 40),
    PERFECTIONIST("Perfectionist", "Get 3 stars on every Trick Shot", 100),
    FULL_HOUSE("Full House", "Unlock all five pets", 50),
    DAILY_REGULAR("Daily Regular", "Finish 5 daily challenges", 50),
    FAMILY_NIGHT("Family Night", "Play 3 two-player games", 20),
    DEMOLITION("Demolition Crew", "Smash 5 fort pieces in one game", 30),
    WRECKING_BALL("Wrecking Ball", "Smash 50 fort pieces in total", 50),
}

enum class GameKind { VS_COMPUTER, TWO_PLAYERS, TRICK_SHOT, DAILY }

/** What happened in one finished game, from the (first) human player's side. */
class GameSummary(
    val kind: GameKind,
    val won: Boolean,
    val level: ComputerLevel?,
    val throwsPerPlayer: Int,
    val throws: Int,
    val hits: Int,
    val tricks: List<String>,
    val ownMess: Float,
    /** The other side's fort pieces broken on the human player's turns. */
    val smashed: Int = 0,
)

object Trophies {
    /** Which trophies [summary] (with the totals already recorded in [settings]) has just earned. */
    fun earned(settings: Settings, summary: GameSummary): List<Trophy> {
        val vsComputer = summary.kind == GameKind.VS_COMPUTER
        val won = summary.won && vsComputer
        val qualifies = mapOf(
            Trophy.FIRST_WIN to won,
            Trophy.CHAMPION to (won && summary.level == ComputerLevel.CHAMPION),
            Trophy.SHARPSHOOTER to (won && summary.throwsPerPlayer >= MatchConfig.FULL_MATCH && summary.hits >= 7),
            Trophy.SPOTLESS to (won && summary.ownMess < 0.5f),
            Trophy.HOT_STREAK to (settings.winStreak >= 3),
            Trophy.BOOMERANG to ("Boomerang!" in summary.tricks),
            Trophy.BANK_SHOT to ("Bank shot!" in summary.tricks),
            Trophy.TRIPLE to summary.tricks.any { it == "Triple!" || it.startsWith("Combo") },
            Trophy.TRICKSTER to (settings.totalTricks >= 10),
            Trophy.STAR_COLLECTOR to (settings.totalStars >= 15),
            Trophy.PERFECTIONIST to (settings.totalStars >= Challenges.all.size * 3),
            Trophy.FULL_HOUSE to PetKind.entries.all { settings.isUnlocked(it) },
            Trophy.DAILY_REGULAR to (settings.dailiesDone >= 5),
            Trophy.FAMILY_NIGHT to (settings.twoPlayerGames >= 3),
            Trophy.DEMOLITION to (summary.smashed >= 5),
            Trophy.WRECKING_BALL to (settings.totalSmashed >= 50),
        )
        return Trophy.entries.filter { qualifies[it] == true && !settings.hasTrophy(it) }
    }
}

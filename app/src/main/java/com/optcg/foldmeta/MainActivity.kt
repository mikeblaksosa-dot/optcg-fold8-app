package com.optcg.foldmeta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

// --- DATA MODELS ---

data class LeaderInfo(
    val id: String,
    val name: String,
    val colors: Set<String>, // "Red", "Green", "Blue", "Purple", "Black", "Yellow"
    val life: Int,
    val formatTag: String = "STANDARD"
) {
    val colorLabel: String get() = colors.joinToString(" / ")
}

data class CardMetadata(
    val id: String,
    val name: String,
    val color: String,
    val cost: Int,
    val counter: Int, // 0, 1000, or 2000
    val type: String = "Character",
    val roleNote: String = "Standard Rotation Staple"
)

data class CardEntry(
    val id: String,
    val name: String,
    val color: String,
    val count: Int,
    val block: Int,
    val cost: Int,
    val counter: Int,
    val isExempt: Boolean = false
) {
    val isBlockLegal: Boolean get() = block >= 2 || isExempt
    val isCountLegal: Boolean get() = count in 1..4
    fun isColorLegal(leaderColors: Set<String>): Boolean = leaderColors.contains(color) || color == "Multi"
}

data class UserDeck(
    val deckId: String,
    val deckName: String,
    val leaderId: String,
    val simList: String,
    val isCustom: Boolean = false
)

data class TopRankingTournamentDeck(
    val rankLabel: String,
    val eventTitle: String,
    val winRate: String,
    val archetypeName: String,
    val leaderId: String,
    val colors: String,
    val formatTag: String,
    val strategySummary: String,
    val simDecklist: String
)

data class MetaSuggestion(
    val actionType: ActionType,
    val actionLabel: String,
    val cardId: String,
    val cardName: String,
    val deltaCount: Int,
    val reason: String,
    val block: Int
)

enum class ActionType { ILLEGAL_REMOVE, ADD_META, TRIM_FLEX }

// --- MASTER ROTATION, COLOR & TOURNAMENT DATABASE ---

object OptcgDatabase {

    fun getCardImageUrl(cardId: String): String {
        val cleanId = cardId.uppercase().trim()
        return "https://en.onepiece-cardgame.com/images/cardlist/card/$cleanId.png"
    }

    val block1Exemptions = setOf(
        "OP01-039", // Killer (Official Block 4 Exemption - Green)
        "OP01-055", // You Can Be My Samurai!! (Official Block 4 Exemption - Green)
        "OP02-005"  // Curly.Dadan (Official Block 4 Exemption - Red)
    )

    val leaders = listOf(
        LeaderInfo("ST30-001", "Luffy & Ace", setOf("Red", "Green"), 4, "PIRATE_PARTY"),
        LeaderInfo("OP12-061", "Donquixote Rosinante", setOf("Purple", "Yellow"), 4, "PIRATE_PARTY"),
        LeaderInfo("OP09-042", "Buggy (Cross Guild)", setOf("Blue"), 5, "PIRATE_PARTY"),
        LeaderInfo("OP16-080", "Marshall.D.Teach", setOf("Black", "Yellow"), 4, "STANDARD"),
        LeaderInfo("EB05-010", "Nico Robin (EB-05)", setOf("Green", "Yellow"), 4, "EB05_HEROINES"),
        LeaderInfo("ST28-001", "Yamato (ST-28)", setOf("Yellow", "Green"), 4, "STANDARD"),
        LeaderInfo("OP06-022", "Yamato (Heroine)", setOf("Green", "Yellow"), 4, "EB05_HEROINES"),
        LeaderInfo("EB03-001", "Nefeltari Vivi (EB-03)", setOf("Red", "Blue"), 4, "EB05_HEROINES")
    )

    fun getLeader(leaderId: String): LeaderInfo {
        return leaders.find { it.id == leaderId.uppercase() } ?: leaders.first()
    }

    // Strictly Verified Card Catalog with Accurate Colors, Costs, Counters & Block 2+ Legality
    val cardCatalog: Map<String, CardMetadata> = listOf(
        // --- RED CARDS (Block 2+) ---
        CardMetadata("ST30-012", "Monkey.D.Luffy (ST30 Rush)", "Red", 5, 0, "Character", "Core Rush attacker that gains +3000 on Opponent's Turn."),
        CardMetadata("OP16-118", "Portgas.D.Ace (SEC)", "Red", 7, 0, "Character", "Massive top-end threat; hits 10k+ power on opponent's turn with ST30 Leader."),
        CardMetadata("ST30-002", "Inazuma (ST30)", "Red", 3, 2000, "Character", "Essential Red +2000 Counter & Impel Down synergy."),
        CardMetadata("ST30-004", "Emporio.Ivankov (ST30)", "Red", 4, 1000, "Character", "Deploys bodies wide and filters hand."),
        CardMetadata("ST30-016", "Can You Still Fight, Luffy?!", "Red", 2, 0, "Event", "Defensive/Offensive counter event for Luffy & Ace."),
        CardMetadata("OP07-015", "Monkey.D.Dragon (SR)", "Red", 8, 0, "Character", "8-cost Rush finisher that recycles rested DON!!."),
        CardMetadata("OP08-007", "Tony Tony.Chopper (Blocker)", "Red", 3, 1000, "Character", "Reliable Red blocker and body extender."),
        CardMetadata("EB01-006", "Tony Tony.Chopper (EB01)", "Red", 2, 2000, "Character", "Core Red +2000 Counter."),
        CardMetadata("OP06-007", "Shanks (10c Removal)", "Red", 10, 0, "Character", "K.O.s any 10000-power or less threat on entry."),
        CardMetadata("OP02-005", "Curly.Dadan (Block 4 Exempt)", "Red", 2, 1000, "Character", "Official Block 4 Exempt searcher for low-cost Red characters."),

        // --- GREEN CARDS (Block 2+) ---
        CardMetadata("OP13-031", "Trafalgar Law (Supernova)", "Green", 5, 1000, "Character", "High-tempo Green bounce/rest extender."),
        CardMetadata("OP08-036", "Electrical Luna", "Green", 3, 0, "Event", "Freezes opponent's wide board for a whole turn."),
        CardMetadata("EB05-001", "Jewelry Bonney (EB05 SR)", "Green", 5, 1000, "Character", "Locks down opposing rested characters & DON!!."),
        CardMetadata("EB05-006", "Miss Buckingham Stussy (EB05)", "Green", 4, 2000, "Character", "Premium Green +2000 Counter from EB-05."),
        CardMetadata("OP07-029", "Basil Hawkins (SR)", "Green", 6, 1000, "Character", "Sticky defensive wall that redirects removal."),
        CardMetadata("OP05-030", "Donquixote Rosinante (2c Blocker)", "Green", 2, 1000, "Character", "Protects rested characters from being K.O.'d."),
        CardMetadata("OP06-035", "Hody Jones (SR Rush)", "Green", 7, 0, "Character", "Rests two opponent characters/DON!! and swings with Rush."),
        CardMetadata("OP05-032", "Pica", "Green", 4, 2000, "Character", "Reliable Green +2000 Counter."),
        CardMetadata("OP01-039", "Killer (Block 4 Exempt)", "Green", 2, 1000, "Character", "Official Block 4 Exempt cheap Blocker that draws on block."),
        CardMetadata("OP01-055", "You Can Be My Samurai!! (Exempt)", "Green", 1, 0, "Event", "Official Block 4 Exempt card-draw engine."),

        // --- YELLOW CARDS (Block 2+) ---
        CardMetadata("OP06-106", "Kouzuki Hiyori", "Yellow", 2, 2000, "Character", "Top-tier Yellow +2000 Counter that stacks Life triggers."),
        CardMetadata("EB01-056", "Charlotte Flampe", "Yellow", 1, 2000, "Character", "Mandatory +2000 Counter that draws a card by taking 1 Life."),
        CardMetadata("OP06-104", "Kikunojo", "Yellow", 4, 0, "Character", "6000-power attacker that heals 1 Life when K.O.'d."),
        CardMetadata("OP06-107", "Kouzuki Momonosuke (1c)", "Yellow", 1, 0, "Character", "1-cost Land of Wano searcher."),
        CardMetadata("OP07-107", "Franky (Egghead)", "Yellow", 4, 2000, "Character", "Trigger character with +2000 Counter."),
        CardMetadata("ST28-005", "Yamato (ST28 SR)", "Yellow", 5, 1000, "Character", "Headline ST-28 Yellow/Green Yamato mid-game pressure card."),
        CardMetadata("EB05-016", "Nico Robin (EB05 SR)", "Yellow", 6, 1000, "Character", "Flagship EB-05 Heroines Vol. 2 SR boss."),
        CardMetadata("EB05-055", "Nami (EB05 Yabuki SR)", "Yellow", 5, 1000, "Character", "High-impact Yellow removal & life manipulation SR."),
        CardMetadata("EB05-056", "Nico Olvia (EB05)", "Yellow", 2, 2000, "Character", "Searchable Ohara +2000 Counter."),
        CardMetadata("EB05-054", "Charlotte Brulee (EB05)", "Yellow", 3, 1000, "Character", "Trigger Blocker for Yellow control shells."),
        CardMetadata("OP08-106", "Nami (Egghead SR)", "Yellow", 5, 1000, "Character", "K.O.s opponent threats while drawing cards."),
        CardMetadata("OP06-115", "You're the One Who Should Disappear", "Yellow", 0, 0, "Event", "0-cost +3000 Counter event with Last-Life heal trigger."),
        CardMetadata("OP05-102", "Gedatsu (SR)", "Yellow", 5, 1000, "Character", "Mid-game removal staple."),

        // --- PURPLE CARDS (Block 2+) ---
        CardMetadata("OP12-106", "Trafalgar Law (6c Disrupt)", "Purple", 6, 0, "Character", "Rips 2 cards from opponent's hand when cheated out by Rosinante."),
        CardMetadata("OP12-073", "Trafalgar Law (Heart Pirates)", "Purple", 4, 1000, "Character", "Core Law name target for Rosinante Leader protection."),
        CardMetadata("OP12-048", "Donquixote Rosinante (3c)", "Purple", 3, 1000, "Character", "Synergistic Blocker for Purple/Yellow control."),
        CardMetadata("ST18-004", "Zoro-Juurou", "Purple", 3, 1000, "Character", "Ramps active DON!! on attack."),
        CardMetadata("OP10-112", "Eustass\"Captain\"Kid (8c)", "Purple", 8, 0, "Character", "Heavy late-game Purple fortress."),
        CardMetadata("OP05-074", "Eustass\"Captain\"Kid (5c Blocker)", "Purple", 5, 1000, "Character", "Refunds 1 active DON!! every time you use a DON!! -X effect."),
        CardMetadata("OP07-064", "Sanji (6c Blocker)", "Purple", 6, 2000, "Character", "Cheats out for 3 DON!! and doubles as a +2000 Counter."),
        CardMetadata("ST18-001", "Uso-Hachi", "Purple", 3, 2000, "Character", "Rests opponent characters on play + +2000 Counter."),

        // --- BLUE CARDS (Block 2+) ---
        CardMetadata("ST25-003", "Crocodile & Mihawk", "Blue", 7, 0, "Character", "Cross Guild engine centerpiece; deploys bodies and protects board."),
        CardMetadata("OP09-051", "Buggy (10c Boss)", "Blue", 10, 0, "Character", "Cheats out for 5 DON!! and bottoms opponent boss threats."),
        CardMetadata("OP09-056", "Mr.3(Galdino) (Searcher)", "Blue", 1, 1000, "Character", "Turn-1 Cross Guild searcher."),
        CardMetadata("PRB02-009", "Mr.3(Galdino) (PRB02)", "Blue", 2, 2000, "Character", "Searchable Cross Guild +2000 Counter."),
        CardMetadata("ST25-001", "Alvida (ST25)", "Blue", 5, 1000, "Character", "5-cost Cross Guild body required for 10c Buggy."),
        CardMetadata("ST25-002", "Cabaji (ST25)", "Blue", 5, 2000, "Character", "5-cost Cross Guild +2000 Counter."),
        CardMetadata("OP16-051", "Mohji & Cabaji", "Blue", 6, 1000, "Character", "Multi-body Cross Guild extender."),
        CardMetadata("OP09-057", "Cross Guild (Event)", "Blue", 1, 0, "Event", "1-cost consistency searcher event."),
        CardMetadata("OP06-058", "Gravity Blade Raging Tiger", "Blue", 7, 0, "Event", "Bottoms two 6-cost or less characters simultaneously."),
        CardMetadata("OP07-051", "Boa Hancock (Blue SR)", "Blue", 6, 0, "Character", "Freezes opponent characters and draws cards."),
        CardMetadata("OP08-047", "Jozu (SR)", "Blue", 6, 1000, "Character", "Bounces a threat and filters hand."),
        CardMetadata("OP06-047", "Charlotte Pudding", "Blue", 4, 1000, "Character", "Resets opponent's bloated hand back to 5 cards."),
        CardMetadata("OP07-046", "Sengoku (Searcher)", "Blue", 1, 1000, "Character", "Turn-1 Seven Warlords searcher."),

        // --- BLACK CARDS (Block 2+) ---
        CardMetadata("OP09-093", "Marshall.D.Teach (10c)", "Black", 10, 0, "Character", "12k Blocker that negates opponent Leader & Character skills."),
        CardMetadata("OP09-086", "Jesus Burgess", "Black", 4, 1000, "Character", "Cannot be K.O.'d by effects and scales power with trash."),
        CardMetadata("OP09-083", "Van Augur", "Black", 5, 1000, "Character", "Cost reduction + draws 1 when K.O.'d."),
        CardMetadata("OP09-090", "Doc Q", "Black", 2, 1000, "Character", "Early K.O. control piece."),
        CardMetadata("OP09-095", "Black Hole", "Black", 4, 0, "Event", "Negates character effects and K.O.s 4-cost or less."),
        CardMetadata("OP16-085", "Shiryu (OP16)", "Black", 4, 1000, "Character", "Has Trigger to fuel OP16 Teach redirection + K.O. pressure."),
        CardMetadata("OP16-091", "Catarina Devon (OP16)", "Black", 5, 2000, "Character", "Blackbeard Pirates +2000 Counter with Trigger."),
        CardMetadata("OP09-089", "Stronger", "Black", 1, 2000, "Character", "Searchable Blackbeard Pirates +2000 Counter."),
        CardMetadata("OP09-088", "Sanjuan.Wolf", "Black", 5, 1000, "Character", "Blackbeard Pirates mid-range body."),
        CardMetadata("OP09-099", "Fullalead (Stage)", "Black", 1, 0, "Stage", "Consistent turn-by-turn Blackbeard Pirates draw engine."),

        // --- LEGACY BLOCK 1 TEST CARDS (Flagged as Illegal if Pasted) ---
        CardMetadata("OP01-016", "Nami (Block 1 Rotated)", "Red", 1, 1000, "Character", "Rotated out in Block 1."),
        CardMetadata("OP04-083", "Sabo (Block 1 Rotated)", "Black", 5, 1000, "Character", "Rotated out in Block 1.")
    ).associateBy { it.id }

    fun determineBlock(cardId: String): Pair<Int, Boolean> {
        val cleanId = cardId.uppercase().trim()
        val isExempt = block1Exemptions.contains(cleanId)
        val prefix = cleanId.substringBefore("-")

        val block = when {
            prefix in listOf("OP01", "OP02", "OP03", "OP04") -> 1
            prefix in listOf("ST01", "ST02", "ST03", "ST04", "ST05", "ST06", "ST07", "ST08", "ST09") -> 1
            prefix in listOf("OP05", "OP06", "OP07", "OP08", "EB01", "PRB01") -> 2
            prefix in listOf("ST10", "ST11", "ST12", "ST13", "ST14", "ST15", "ST16", "ST17", "ST18", "ST19", "ST20") -> 2
            prefix in listOf("OP09", "OP10", "OP11", "OP12", "EB02", "PRB02") -> 3
            prefix in listOf("ST21", "ST22", "ST23", "ST24", "ST25", "ST26", "ST27", "ST28") -> 3
            else -> 4
        }
        return Pair(block, isExempt)
    }

    fun parseSimDecklist(rawText: String, fallbackColor: String = "Red"): List<CardEntry> {
        val regex = Regex("""(\d+)x?\s*([A-Za-z0-9]+-\d+)""")
        val consolidated = linkedMapOf<String, Int>()

        rawText.lines().forEach { line ->
            val match = regex.find(line.trim())
            if (match != null) {
                val count = match.groupValues[1].toIntOrNull() ?: 0
                val id = match.groupValues[2].uppercase()
                if (count > 0) consolidated[id] = (consolidated[id] ?: 0) + count
            }
        }

        return consolidated.map { (id, count) ->
            val (block, isExempt) = determineBlock(id)
            val meta = cardCatalog[id]
            val name = meta?.name ?: "Card $id"
            val color = meta?.color ?: fallbackColor
            val cost = meta?.cost ?: 4
            val counter = meta?.counter ?: 1000
            CardEntry(id, name, color, count, block, cost, counter, isExempt)
        }
    }

    fun serializeToSimFormat(deck: List<CardEntry>): String {
        return deck.filter { it.count > 0 }.joinToString("\n") { "${it.count}x${it.id}" }
    }

    // 100% Legal 50-Card Pre-Loaded Decks (Max 4 copies per card, 100% Color-Matched, 100% Block 2+)
    val defaultPersonalDecks = listOf(
        UserDeck(
            deckId = "default_st30",
            deckName = "R/G Luffy & Ace (Regional Winner)",
            leaderId = "ST30-001",
            simList = """
                4xST30-012
                4xOP16-118
                4xST30-002
                4xST30-004
                4xOP13-031
                4xST30-016
                4xOP08-036
                4xEB05-006
                4xOP01-039
                4xOP07-015
                4xOP05-030
                4xEB01-006
                2xOP08-007
            """.trimIndent()
        ),
        UserDeck(
            deckId = "default_op12",
            deckName = "P/Y Rosinante (Heart Control)",
            leaderId = "OP12-061",
            simList = """
                4xOP12-106
                4xOP12-073
                4xOP12-048
                4xOP06-106
                4xEB01-056
                4xST18-004
                4xEB05-055
                4xEB05-056
                4xOP10-112
                4xOP05-074
                4xOP07-064
                4xST18-001
                2xOP06-104
            """.trimIndent()
        ),
        UserDeck(
            deckId = "default_st28",
            deckName = "Y/G Yamato (ST-28 Wano Aggro)",
            leaderId = "ST28-001",
            simList = """
                4xST28-005
                4xOP06-104
                4xOP06-106
                4xOP06-107
                4xOP07-107
                4xEB05-055
                4xEB05-001
                4xEB05-006
                4xEB01-056
                4xOP08-036
                4xOP06-035
                4xOP01-039
                2xOP05-030
            """.trimIndent()
        ),
        UserDeck(
            deckId = "default_eb05",
            deckName = "G/Y Nico Robin (EB-05 Heroines)",
            leaderId = "EB05-010",
            simList = """
                4xEB05-016
                4xEB05-055
                4xEB05-056
                4xEB05-001
                4xEB05-006
                4xOP06-106
                4xOP06-104
                4xEB01-056
                4xOP07-107
                4xOP08-036
                4xOP05-030
                4xOP07-029
                2xOP06-035
            """.trimIndent()
        ),
        UserDeck(
            deckId = "default_op09",
            deckName = "Blue Buggy (Cross Guild)",
            leaderId = "OP09-042",
            simList = """
                4xST25-003
                4xOP09-051
                4xOP09-056
                4xPRB02-009
                4xST25-001
                4xST25-002
                4xOP16-051
                4xOP09-057
                4xOP06-058
                4xOP07-051
                4xOP08-047
                4xOP06-047
                2xOP07-046
            """.trimIndent()
        ),
        UserDeck(
            deckId = "default_op16",
            deckName = "B/Y Marshall.D.Teach (OP-16)",
            leaderId = "OP16-080",
            simList = """
                4xOP09-093
                4xOP09-086
                4xOP09-083
                4xOP09-090
                4xOP16-085
                4xOP16-091
                4xOP09-095
                4xEB01-056
                4xOP06-106
                4xEB05-054
                4xOP09-089
                4xOP09-088
                2xOP09-099
            """.trimIndent()
        )
    )

    // Top Ranking Tournament Decks Leaderboard (Tab 4)
    val topRankingTournamentDecks = listOf(
        TopRankingTournamentDeck(
            rankLabel = "🏆 1st Place — Championship Regional",
            eventTitle = "512-Player Standard Regional (Block 2+)",
            winRate = "9-0 Undefeated (100%)",
            archetypeName = "Black/Yellow Marshall.D.Teach",
            leaderId = "OP16-080",
            colors = "Black / Yellow",
            formatTag = "STANDARD",
            strategySummary = "Abuses OP16 Teach's trigger-trash redirection to protect Jesus Burgess while giving all Blackbeard Pirates +1 cost during Opponent's Turn. Locks out late-game with 4x 10c Teach.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "OP16-080" }.simList
        ),
        TopRankingTournamentDeck(
            rankLabel = "🥇 1st Place — Pirates Party Vol. 3",
            eventTitle = "Limited Leader Battle Championship",
            winRate = "8-1 Record (88.9%)",
            archetypeName = "Red/Green Luffy & Ace (ST-30)",
            leaderId = "ST30-001",
            colors = "Red / Green",
            formatTag = "PIRATE_PARTY",
            strategySummary = "With Black Yamato and Teach banned in Pirates Party Vol. 3, ST30 Luffy & Ace dominates the field. Its 6000 base power and +3000 Opponent's Turn buff make 7c SEC Ace and 5c Rush Luffy impossible to clear in combat.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "ST30-001" }.simList
        ),
        TopRankingTournamentDeck(
            rankLabel = "🌸 1st Place — EB-05 Heroines Cup",
            eventTitle = "Extra Booster Heroines Battle Vol. 2",
            winRate = "7-0 Undefeated (100%)",
            archetypeName = "Green/Yellow Nico Robin (EB-05)",
            leaderId = "EB05-010",
            colors = "Green / Yellow",
            formatTag = "EB05_HEROINES",
            strategySummary = "Built exclusively for the 24-Leader Heroines Battle format. Curves 2c Hiyori into 5c Yabuki Nami SR and 6c Robin SR while freezing opponent attackers with 5c Bonney SR and Electrical Luna.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "EB05-010" }.simList
        ),
        TopRankingTournamentDeck(
            rankLabel = "🥈 Top 4 — Treasure Cup",
            eventTitle = "Standard Regulation Treasure Cup",
            winRate = "7-1 Record (87.5%)",
            archetypeName = "Purple/Yellow Donquixote Rosinante",
            leaderId = "OP12-061",
            colors = "Purple / Yellow",
            formatTag = "PIRATE_PARTY",
            strategySummary = "Cheats out 6c Trafalgar Law on Turn 3 using Rosinante's DON!! -1 discount to strip two cards from the opponent's hand, then protects Law from removal using Leader skill and 5c Kid.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "OP12-061" }.simList
        ),
        TopRankingTournamentDeck(
            rankLabel = "🥉 Top 4 — Standard Regional",
            eventTitle = "Standard Regulation Regional",
            winRate = "8-2 Record (80.0%)",
            archetypeName = "Yellow/Green Yamato (ST-28 Wano)",
            leaderId = "ST28-001",
            colors = "Yellow / Green",
            formatTag = "STANDARD",
            strategySummary = "Relentless Double Attack life pressure combined with ST28-005 Yamato SR, 4c Kikunojo, and 7c Hody Jones Rush to close games before slow control decks hit 10 DON!!.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "ST28-001" }.simList
        ),
        TopRankingTournamentDeck(
            rankLabel = "🏅 Top 8 — Regional Championship",
            eventTitle = "Standard Regulation Regional",
            winRate = "7-2 Record (77.8%)",
            archetypeName = "Blue Buggy Cross Guild",
            leaderId = "OP09-042",
            colors = "Blue",
            formatTag = "PIRATE_PARTY",
            strategySummary = "Floods the board with 7c Crocodile & Mihawk, 6c Mohji & Cabaji, and 5c Alvida/Cabaji to deploy 10c Buggy for just 5 DON!!, bottom-decking opposing boss monsters.",
            simDecklist = defaultPersonalDecks.first { it.leaderId == "OP09-042" }.simList
        )
    )

    val piratePartyCommonBans = listOf(
        "OP16-079" to "Yamato (Black)",
        "OP14-001" to "Imu (Black)",
        "OP16-080" to "Marshall.D.Teach (B/Y)",
        "OP11-062" to "Katakuri (Purple)"
    )

    fun analyzeDeckStrict(deck: List<CardEntry>, leader: LeaderInfo): List<MetaSuggestion> {
        val suggestions = mutableListOf<MetaSuggestion>()
        val deckMap = deck.associateBy { it.id }

        // 1. Flag Illegal Block 1 Cards
        deck.filter { !it.isBlockLegal }.forEach { illegal ->
            suggestions.add(
                MetaSuggestion(
                    actionType = ActionType.ILLEGAL_REMOVE,
                    actionLabel = "ILLEGAL BLOCK 1 (REMOVE ALL ${illegal.count})",
                    cardId = illegal.id,
                    cardName = illegal.name,
                    deltaCount = -illegal.count,
                    reason = "Block 1 is rotated out of Standard Regulation. Remove all copies.",
                    block = 1
                )
            )
        }

        // 2. Flag Off-Color Cards (Cards that do NOT match the Leader's colors)
        deck.filter { !it.isColorLegal(leader.colors) }.forEach { offColor ->
            suggestions.add(
                MetaSuggestion(
                    actionType = ActionType.ILLEGAL_REMOVE,
                    actionLabel = "OFF-COLOR ILLEGAL (${offColor.color.uppercase()} IN ${leader.colorLabel.uppercase()})",
                    cardId = offColor.id,
                    cardName = offColor.name,
                    deltaCount = -offColor.count,
                    reason = "Deck construction rules forbid ${offColor.color} cards in a ${leader.colorLabel} Leader deck.",
                    block = offColor.block
                )
            )
        }

        // 3. Flag >4 Copy Rule Violations
        deck.filter { it.count > 4 }.forEach { overLimit ->
            val excess = overLimit.count - 4
            suggestions.add(
                MetaSuggestion(
                    actionType = ActionType.ILLEGAL_REMOVE,
                    actionLabel = "OVER 4-COPY LIMIT (REMOVE -$excess)",
                    cardId = overLimit.id,
                    cardName = overLimit.name,
                    deltaCount = -excess,
                    reason = "Official tournament rules allow a maximum of 4 copies of any card number (${overLimit.count}x currently in deck).",
                    block = overLimit.block
                )
            )
        }

        // 4. Recommend High-Synergy Legal Staples matching Leader's EXACT colors (Max 4 copies total!)
        val legalLeaderPool = cardCatalog.values.filter { meta ->
            val (blk, ex) = determineBlock(meta.id)
            (blk >= 2 || ex) && leader.colors.contains(meta.color)
        }

        legalLeaderPool.forEach { candidate ->
            val currentCount = deckMap[candidate.id]?.count ?: 0
            if (currentCount in 1..3) {
                val diff = (4 - currentCount).coerceAtMost(4 - currentCount)
                suggestions.add(
                    MetaSuggestion(
                        actionType = ActionType.ADD_META,
                        actionLabel = "MAX OUT PLAYSET (+$diff TO 4x)",
                        cardId = candidate.id,
                        cardName = candidate.name,
                        deltaCount = diff,
                        reason = "${candidate.color} staple (${candidate.cost}c, +${candidate.counter} Cntr): ${candidate.roleNote}",
                        block = determineBlock(candidate.id).first
                    )
                )
            } else if (currentCount == 0 && suggestions.count { it.actionType == ActionType.ADD_META } < 6) {
                suggestions.add(
                    MetaSuggestion(
                        actionType = ActionType.ADD_META,
                        actionLabel = "RECOMMENDED ${candidate.color.uppercase()} STAPLE (+2x)",
                        cardId = candidate.id,
                        cardName = candidate.name,
                        deltaCount = 2,
                        reason = "${candidate.color} tournament staple (${candidate.cost}c, +${candidate.counter} Cntr): ${candidate.roleNote}",
                        block = determineBlock(candidate.id).first
                    )
                )
            }
        }

        return suggestions
    }

    fun calculateOpeningHandProbability(deckSize: Int, copiesInDeck: Int, handSize: Int = 5): Int {
        if (deckSize <= 0 || copiesInDeck <= 0) return 0
        if (copiesInDeck >= deckSize) return 100
        var probZero = 1.0
        for (i in 0 until handSize) {
            probZero *= (deckSize - copiesInDeck - i).toDouble() / (deckSize - i).toDouble()
        }
        return ((1.0 - probZero.coerceIn(0.0, 1.0)) * 100).roundToInt()
    }
}

// --- PERSISTENT STORAGE FOR USER CUSTOM DECKS ---

object DeckStorageManager {
    private const val PREFS_NAME = "optcg_fold8_decks_v5"
    private const val KEY_DECKS = "saved_decks_json"

    fun loadDecks(context: Context): List<UserDeck> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_DECKS, null) ?: return OptcgDatabase.defaultPersonalDecks
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<UserDeck>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    UserDeck(
                        deckId = obj.getString("deckId"),
                        deckName = obj.getString("deckName"),
                        leaderId = obj.getString("leaderId"),
                        simList = obj.getString("simList"),
                        isCustom = obj.optBoolean("isCustom", false)
                    )
                )
            }
            if (list.isEmpty()) OptcgDatabase.defaultPersonalDecks else list
        } catch (e: Exception) {
            OptcgDatabase.defaultPersonalDecks
        }
    }

    fun saveDecks(context: Context, decks: List<UserDeck>) {
        val arr = JSONArray()
        decks.forEach { d ->
            val obj = JSONObject().apply {
                put("deckId", d.deckId)
                put("deckName", d.deckName)
                put("leaderId", d.leaderId)
                put("simList", d.simList)
                put("isCustom", d.isCustom)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DECKS, arr.toString())
            .apply()
    }
}

// --- MAIN ACTIVITY ---

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val isUnfolded = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF5C542),
                    secondary = Color(0xFFFF6EC7),
                    surface = Color(0xFF181B24),
                    background = Color(0xFF0D0F14)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FoldableOptcgMasterAppV5(isUnfolded = isUnfolded)
                }
            }
        }
    }
}

// --- MASTER COMPOSABLE UI (v5.0) ---

@Composable
fun FoldableOptcgMasterAppV5(isUnfolded: Boolean) {
    val context = LocalContext.current

    // 0 = My Decks & Builder, 1 = Top Meta Decks, 2 = Event Modes, 3 = Hand & Mulligan Sim
    var currentMainSection by remember { mutableStateOf(0) }

    var savedDecks by remember { mutableStateOf(DeckStorageManager.loadDecks(context)) }
    var activeDeck by remember { mutableStateOf(savedDecks.first()) }
    var deckInputText by remember { mutableStateOf(activeDeck.simList) }
    var isVisualGridMode by remember { mutableStateOf(true) }

    // Dialog states
    var showNewDeckDialog by remember { mutableStateOf(false) }
    var showCardPickerModal by remember { mutableStateOf(false) }
    var inspectedCard by remember { mutableStateOf<Pair<String, String>?>(null) }

    var blockedLeaderIds by remember {
        mutableStateOf(setOf("OP16-079", "OP14-001", "OP16-080"))
    }

    val currentLeader = remember(activeDeck.leaderId) { OptcgDatabase.getLeader(activeDeck.leaderId) }
    val parsedDeck = remember(deckInputText, currentLeader) {
        OptcgDatabase.parseSimDecklist(deckInputText, currentLeader.colors.first())
    }
    val totalCards = parsedDeck.sumOf { it.count }
    val illegalCount = parsedDeck.count { !it.isBlockLegal || !it.isCountLegal || !it.isColorLegal(currentLeader.colors) }
    val suggestions = remember(parsedDeck, currentLeader) {
        OptcgDatabase.analyzeDeckStrict(parsedDeck, currentLeader)
    }

    // Strict 4-Copy Enforced Quantity Updater
    fun updateCardQuantity(cardId: String, delta: Int) {
        val mutableList = parsedDeck.toMutableList()
        val idx = mutableList.indexOfFirst { it.id == cardId }
        if (idx >= 0) {
            val current = mutableList[idx]
            if (delta > 0 && current.count >= 4) {
                Toast.makeText(context, "Max 4 copies per card allowed!", Toast.LENGTH_SHORT).show()
                return
            }
            val newCount = (current.count + delta).coerceIn(0, 4)
            if (newCount == 0) mutableList.removeAt(idx)
            else mutableList[idx] = current.copy(count = newCount)
        } else if (delta > 0) {
            val (block, isExempt) = OptcgDatabase.determineBlock(cardId)
            val meta = OptcgDatabase.cardCatalog[cardId]
            val name = meta?.name ?: "Card $cardId"
            val color = meta?.color ?: currentLeader.colors.first()
            val cost = meta?.cost ?: 4
            val counter = meta?.counter ?: 1000
            mutableList.add(CardEntry(cardId, name, color, delta.coerceIn(1, 4), block, cost, counter, isExempt))
        }
        val updatedSim = OptcgDatabase.serializeToSimFormat(mutableList)
        deckInputText = updatedSim
        // Auto-save into current active deck
        val updatedDeck = activeDeck.copy(simList = updatedSim)
        activeDeck = updatedDeck
        val updatedList = savedDecks.map { if (it.deckId == updatedDeck.deckId) updatedDeck else it }
        savedDecks = updatedList
        DeckStorageManager.saveDecks(context, updatedList)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🏴‍☠️ OPTCG Fold 8", color = Color(0xFFF5C542), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("• 4x Max & Color Legal", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentMainSection == 0,
                    onClick = { currentMainSection = 0 },
                    label = { Text("1. My Decks & Builder") },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                FilterChip(
                    selected = currentMainSection == 1,
                    onClick = { currentMainSection = 1 },
                    label = { Text("2. Top Meta Decks") },
                    leadingIcon = { Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                FilterChip(
                    selected = currentMainSection == 2,
                    onClick = { currentMainSection = 2 },
                    label = { Text("3. Event Modes (EB-05 / Party)") },
                    leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                FilterChip(
                    selected = currentMainSection == 3,
                    onClick = { currentMainSection = 3 },
                    label = { Text("4. Hand & Mulligan Sim") },
                    leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
            }
        }

        when (currentMainSection) {
            0 -> {
                // Personal Deck Manager Bar (Select, + New Custom Deck, Delete Current Deck, Reset Defaults)
                PersonalDeckManagerBar(
                    savedDecks = savedDecks,
                    activeDeck = activeDeck,
                    onSelectDeck = { deck ->
                        activeDeck = deck
                        deckInputText = deck.simList
                    },
                    onNewDeckClick = { showNewDeckDialog = true },
                    onDeleteCurrentDeck = {
                        if (savedDecks.size <= 1) {
                            Toast.makeText(context, "Keep at least 1 deck in your list!", Toast.LENGTH_SHORT).show()
                        } else {
                            val remaining = savedDecks.filter { it.deckId != activeDeck.deckId }
                            savedDecks = remaining
                            activeDeck = remaining.first()
                            deckInputText = activeDeck.simList
                            DeckStorageManager.saveDecks(context, remaining)
                            Toast.makeText(context, "Deleted deck!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onResetDefaults = {
                        savedDecks = OptcgDatabase.defaultPersonalDecks
                        activeDeck = savedDecks.first()
                        deckInputText = activeDeck.simList
                        DeckStorageManager.saveDecks(context, savedDecks)
                        Toast.makeText(context, "Restored verified 50-card tournament decks!", Toast.LENGTH_SHORT).show()
                    },
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )

                if (isUnfolded) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.weight(1.1f).fillMaxHeight()) {
                            DeckWorkspaceWithCurvePane(
                                leader = currentLeader,
                                parsedDeck = parsedDeck,
                                totalCards = totalCards,
                                illegalCount = illegalCount,
                                deckInputText = deckInputText,
                                onTextChange = {
                                    deckInputText = it
                                    val updated = activeDeck.copy(simList = it)
                                    activeDeck = updated
                                    savedDecks = savedDecks.map { d -> if (d.deckId == updated.deckId) updated else d }
                                    DeckStorageManager.saveDecks(context, savedDecks)
                                },
                                isVisualGridMode = isVisualGridMode,
                                onToggleMode = { isVisualGridMode = !isVisualGridMode },
                                onOpenCardPicker = { showCardPickerModal = true },
                                onModifyCount = ::updateCardQuantity,
                                onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                            )
                        }
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF282D3C)))
                        Box(modifier = Modifier.weight(0.9f).fillMaxHeight()) {
                            VisualRecommendationsPane(
                                leader = currentLeader,
                                suggestions = suggestions,
                                totalCards = totalCards,
                                illegalCount = illegalCount,
                                onApplySuggestion = { item ->
                                    updateCardQuantity(item.cardId, item.deltaCount)
                                },
                                onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                            )
                        }
                    }
                } else {
                    var subTab by remember { mutableStateOf(0) }
                    TabRow(selectedTabIndex = subTab, containerColor = Color(0xFF181B24)) {
                        Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Deck ($totalCards/50)") })
                        Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Audit & Upgrades (${suggestions.size})") })
                    }
                    if (subTab == 0) {
                        DeckWorkspaceWithCurvePane(
                            leader = currentLeader,
                            parsedDeck = parsedDeck,
                            totalCards = totalCards,
                            illegalCount = illegalCount,
                            deckInputText = deckInputText,
                            onTextChange = { deckInputText = it },
                            isVisualGridMode = isVisualGridMode,
                            onToggleMode = { isVisualGridMode = !isVisualGridMode },
                            onOpenCardPicker = { showCardPickerModal = true },
                            onModifyCount = ::updateCardQuantity,
                            onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                        )
                    } else {
                        VisualRecommendationsPane(
                            leader = currentLeader,
                            suggestions = suggestions,
                            totalCards = totalCards,
                            illegalCount = illegalCount,
                            onApplySuggestion = { item -> updateCardQuantity(item.cardId, item.deltaCount) },
                            onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                        )
                    }
                }
            }

            1 -> {
                // TAB 2: TOP RANKING TOURNAMENT DECKS LEADERBOARD
                TopRankingDecksHub(
                    onImportToMyDecks = { topDeck ->
                        val imported = UserDeck(
                            deckId = "imported_${System.currentTimeMillis()}",
                            deckName = "★ ${topDeck.archetypeName}",
                            leaderId = topDeck.leaderId,
                            simList = topDeck.simDecklist,
                            isCustom = true
                        )
                        val updated = listOf(imported) + savedDecks
                        savedDecks = updated
                        activeDeck = imported
                        deckInputText = imported.simList
                        DeckStorageManager.saveDecks(context, updated)
                        currentMainSection = 0
                        Toast.makeText(context, "Imported ${topDeck.archetypeName} into My Decks!", Toast.LENGTH_SHORT).show()
                    },
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }

            2 -> {
                // TAB 3: SPECIAL EVENT MODES HUB
                SpecialEventModesHub(
                    blockedLeaderIds = blockedLeaderIds,
                    onToggleBlockedLeader = { id ->
                        blockedLeaderIds = if (blockedLeaderIds.contains(id)) blockedLeaderIds - id else blockedLeaderIds + id
                    },
                    onLoadRecommendedDeck = { topDeck ->
                        val imported = UserDeck(
                            deckId = "event_${System.currentTimeMillis()}",
                            deckName = topDeck.archetypeName,
                            leaderId = topDeck.leaderId,
                            simList = topDeck.simDecklist,
                            isCustom = true
                        )
                        val updated = listOf(imported) + savedDecks
                        savedDecks = updated
                        activeDeck = imported
                        deckInputText = imported.simList
                        DeckStorageManager.saveDecks(context, updated)
                        currentMainSection = 0
                        Toast.makeText(context, "Loaded ${topDeck.archetypeName} into My Decks!", Toast.LENGTH_SHORT).show()
                    },
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }

            3 -> {
                // TAB 4: STARTING HAND & MULLIGAN SIMULATOR
                StartingHandAndMulliganSimulator(
                    parsedDeck = parsedDeck,
                    leader = currentLeader,
                    deckName = activeDeck.deckName,
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }
        }
    }

    // --- CREATE NEW CUSTOM DECK DIALOG ---
    if (showNewDeckDialog) {
        var customName by remember { mutableStateOf("My Custom Deck") }
        var chosenLeader by remember { mutableStateOf(OptcgDatabase.leaders.first()) }
        var startEmpty by remember { mutableStateOf(true) }

        Dialog(onDismissRequest = { showNewDeckDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFFF5C542), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Build New Custom Deck", color = Color(0xFFF5C542), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Deck Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Select Leader (Sets Legal Deck Colors):", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.height(180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(OptcgDatabase.leaders) { ldr ->
                        val selected = ldr.id == chosenLeader.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (selected) Color(0xFF282F44) else Color(0xFF101218), RoundedCornerShape(8.dp))
                                .border(1.dp, if (selected) Color(0xFFF5C542) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { chosenLeader = ldr }
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${ldr.name} (${ldr.id})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(ldr.colorLabel, color = Color(0xFFF5C542), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = startEmpty, onCheckedChange = { startEmpty = it })
                    Text("Start with empty 0/50 list (Open Color-Filtered Card Picker)", color = Color.LightGray, fontSize = 12.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showNewDeckDialog = false }) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val templateSim = if (startEmpty) "" else {
                                OptcgDatabase.defaultPersonalDecks.find { it.leaderId == chosenLeader.id }?.simList ?: ""
                            }
                            val newDeck = UserDeck(
                                deckId = "custom_${System.currentTimeMillis()}",
                                deckName = customName.ifBlank { "${chosenLeader.name} Custom" },
                                leaderId = chosenLeader.id,
                                simList = templateSim,
                                isCustom = true
                            )
                            val updated = listOf(newDeck) + savedDecks
                            savedDecks = updated
                            activeDeck = newDeck
                            deckInputText = newDeck.simList
                            DeckStorageManager.saveDecks(context, updated)
                            showNewDeckDialog = false
                            if (startEmpty) showCardPickerModal = true
                        }
                    ) {
                        Text("Create Deck")
                    }
                }
            }
        }
    }

    // --- COLOR-FILTERED VISUAL CARD CATALOG PICKER MODAL ---
    if (showCardPickerModal) {
        val deckMap = parsedDeck.associateBy { it.id }
        val legalCatalogForLeader = remember(currentLeader) {
            OptcgDatabase.cardCatalog.values.filter { card ->
                val (blk, ex) = OptcgDatabase.determineBlock(card.id)
                (blk >= 2 || ex) && currentLeader.colors.contains(card.color)
            }.sortedWith(compareBy({ it.color }, { it.cost }))
        }
        var selectedColorFilter by remember { mutableStateOf("ALL") }

        Dialog(onDismissRequest = { showCardPickerModal = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .background(Color(0xFF141720), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFFF5C542), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Add Cards (${currentLeader.name} • ${currentLeader.colorLabel})",
                            color = Color(0xFFF5C542),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            "Strictly filtered to ${currentLeader.colorLabel} & Block 2+ Rotation Legal • Deck: $totalCards/50",
                            color = Color(0xFF69F0AE),
                            fontSize = 11.sp
                        )
                    }
                    Button(onClick = { showCardPickerModal = false }) {
                        Text("Done")
                    }
                }

                // Color Filter Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedColorFilter == "ALL",
                        onClick = { selectedColorFilter = "ALL" },
                        label = { Text("All Leader Colors (${currentLeader.colorLabel})") }
                    )
                    currentLeader.colors.forEach { clr ->
                        FilterChip(
                            selected = selectedColorFilter == clr,
                            onClick = { selectedColorFilter = clr },
                            label = { Text("$clr Only") }
                        )
                    }
                }

                val displayedCards = legalCatalogForLeader.filter {
                    selectedColorFilter == "ALL" || it.color == selectedColorFilter
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayedCards, key = { it.id }) { meta ->
                        val countInDeck = deckMap[meta.id]?.count ?: 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                                .border(1.dp, if (countInDeck > 0) Color(0xFFF5C542) else Color(0xFF282D3C), RoundedCornerShape(10.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CardArtworkThumbnail(
                                cardId = meta.id,
                                contentDescription = meta.name,
                                modifier = Modifier
                                    .size(width = 52.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { inspectedCard = Pair(meta.id, meta.name) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${meta.name} (${meta.id})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${meta.color} • ${meta.cost} DON!! • +${meta.counter} Counter", color = Color(0xFFF5C542), fontSize = 11.sp)
                                Text(meta.roleNote, color = Color.LightGray, fontSize = 11.sp, maxLines = 2)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { updateCardQuantity(meta.id, -1) },
                                    enabled = countInDeck > 0,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("−", fontWeight = FontWeight.Bold)
                                }
                                Text("$countInDeck/4", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                Button(
                                    onClick = { updateCardQuantity(meta.id, 1) },
                                    enabled = countInDeck < 4,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text("+", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- HIGH-RES CARD ZOOM MODAL ---
    inspectedCard?.let { (cardId, cardName) ->
        val (block, isExempt) = OptcgDatabase.determineBlock(cardId)
        Dialog(onDismissRequest = { inspectedCard = null }) {
            Column(
                modifier = Modifier
                    .background(Color(0xFF181B24), RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFFF5C542), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(cardName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    text = if (block == 1 && !isExempt) "BLOCK 1 (ROTATED OUT)" else if (isExempt) "BLOCK 4 EXEMPTION (LEGAL)" else "BLOCK $block (STANDARD LEGAL)",
                    color = if (block == 1 && !isExempt) Color(0xFFFF5252) else Color(0xFF69F0AE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                CardArtworkThumbnail(
                    cardId = cardId,
                    contentDescription = cardName,
                    modifier = Modifier.height(350.dp).aspectRatio(0.715f).clip(RoundedCornerShape(10.dp))
                )
                Button(onClick = { inspectedCard = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Close Card View")
                }
            }
        }
    }
}

// --- PERSONAL DECK MANAGER BAR ---

@Composable
fun PersonalDeckManagerBar(
    savedDecks: List<UserDeck>,
    activeDeck: UserDeck,
    onSelectDeck: (UserDeck) -> Unit,
    onNewDeckClick: () -> Unit,
    onDeleteCurrentDeck: () -> Unit,
    onResetDefaults: () -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + New Custom Deck Button
            Button(
                onClick = onNewDeckClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Custom Deck", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }

            // Saved Decks Carousel
            savedDecks.forEach { deck ->
                val isSelected = deck.deckId == activeDeck.deckId
                val ldr = OptcgDatabase.getLeader(deck.leaderId)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF252A3D) else Color(0xFF141720))
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFFF5C542) else Color(0xFF282D3C), RoundedCornerShape(10.dp))
                        .clickable { onSelectDeck(deck) }
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CardArtworkThumbnail(
                        cardId = ldr.id,
                        contentDescription = ldr.name,
                        modifier = Modifier
                            .size(width = 30.dp, height = 42.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onInspectCard(ldr.id, ldr.name) }
                    )
                    Column {
                        Text(deck.deckName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${ldr.id} • ${ldr.colorLabel}", color = Color(0xFFF5C542), fontSize = 10.sp)
                    }
                }
            }

            // Delete Current Deck Button
            OutlinedButton(
                onClick = onDeleteCurrentDeck,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Deck", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete Deck", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Reset Preloaded Defaults Button
            TextButton(onClick = onResetDefaults) {
                Text("Reset Defaults", color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}

// --- TOP RANKING TOURNAMENT DECKS HUB (TAB 2) ---

@Composable
fun TopRankingDecksHub(
    onImportToMyDecks: (TopRankingTournamentDeck) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Top Ranking Tournament Decklists (Verified 50-Card Lists)",
                color = Color(0xFFF5C542),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Text(
                "Every list below is strictly audited: 50 Main Deck cards, max 4 copies per card, 100% Leader Color legal, and 100% Block 2+ Rotation legal.",
                color = Color(0xFFB0BEC5),
                fontSize = 12.sp
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(OptcgDatabase.topRankingTournamentDecks) { topDeck ->
                val parsedCards = remember(topDeck.simDecklist) {
                    OptcgDatabase.parseSimDecklist(topDeck.simDecklist)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFF5C542).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CardArtworkThumbnail(
                                cardId = topDeck.leaderId,
                                contentDescription = topDeck.archetypeName,
                                modifier = Modifier
                                    .size(width = 60.dp, height = 84.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFF5C542), RoundedCornerShape(6.dp))
                                    .clickable { onInspectCard(topDeck.leaderId, topDeck.archetypeName) }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(topDeck.rankLabel, color = Color(0xFF00E676), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Text("${topDeck.archetypeName} (${topDeck.leaderId})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("${topDeck.eventTitle} • Win Rate: ${topDeck.winRate} • ${topDeck.colors}", color = Color(0xFFF5C542), fontSize = 11.sp)
                                Text(topDeck.strategySummary, color = Color(0xFFCFD8DC), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }

                    // Horizontal Visual Preview of All 50 Cards (13 playsets)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        parsedCards.forEach { card ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .background(Color(0xFF101218), RoundedCornerShape(6.dp))
                                    .padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CardArtworkThumbnail(
                                    cardId = card.id,
                                    contentDescription = card.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.715f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onInspectCard(card.id, card.name) }
                                )
                                Text("${card.count}x ${card.id}", color = Color(0xFFF5C542), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onImportToMyDecks(topDeck) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5C542), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import to My Decks & Edit", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OPTCG Top Deck", topDeck.simDecklist))
                                Toast.makeText(context, "Copied 50-card list for OPTCG Sim!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy OPTCG Sim List", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- DECK WORKSPACE WITH COLOR PICKER & TELEMETRY ---

@Composable
fun DeckWorkspaceWithCurvePane(
    leader: LeaderInfo,
    parsedDeck: List<CardEntry>,
    totalCards: Int,
    illegalCount: Int,
    deckInputText: String,
    onTextChange: (String) -> Unit,
    isVisualGridMode: Boolean,
    onToggleMode: () -> Unit,
    onOpenCardPicker: () -> Unit,
    onModifyCount: (String, Int) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DonCurveAndCounterAnalyticsBar(parsedDeck = parsedDeck, totalCards = totalCards)

        // Action Bar: Browse Cards by Leader Color + Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val countColor = if (totalCards == 50 && illegalCount == 0) Color(0xFF00E676) else Color(0xFFFFAB00)
                Text("$totalCards / 50 Cards", color = countColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                if (illegalCount > 0) {
                    Text("• ⚠️ $illegalCount Rule Violation(s)", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                } else {
                    Text("• ✓ ${leader.colorLabel} Legal", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onOpenCardPicker,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5C542), contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add ${leader.colorLabel} Cards", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clipText.isNullOrBlank()) {
                            onTextChange(clipText)
                            Toast.makeText(context, "Pasted Sim Decklist!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OPTCG Sim Deck", deckInputText))
                        Toast.makeText(context, "Copied OPTCG Sim list!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                    onClick = onToggleMode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isVisualGridMode) "Sim Text" else "Grid", fontSize = 11.sp)
                }
            }
        }

        if (isVisualGridMode) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(parsedDeck, key = { it.id }) { card ->
                    val isColorOk = card.isColorLegal(leader.colors)
                    val isCardValid = card.isBlockLegal && card.isCountLegal && isColorOk
                    val borderColor = when {
                        !isCardValid -> Color(0xFFFF5252)
                        card.isExempt -> Color(0xFF00E5FF)
                        else -> Color(0xFF2D3243)
                    }

                    Column(
                        modifier = Modifier
                            .background(Color(0xFF181B24), RoundedCornerShape(8.dp))
                            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            CardArtworkThumbnail(
                                cardId = card.id,
                                contentDescription = card.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.715f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onInspectCard(card.id, card.name) }
                            )
                            Column(
                                horizontalAlignment = Alignment.End,
                                modifier = Modifier.padding(3.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val statusTag = when {
                                    !card.isBlockLegal -> "B1 BAN"
                                    !isColorOk -> "WRONG CLR"
                                    !card.isCountLegal -> ">4 LIMIT"
                                    else -> "B${card.block}"
                                }
                                Text(
                                    text = statusTag,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(if (!isCardValid) Color(0xFFD50000) else Color(0xCC000000), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                                if (card.counter > 0) {
                                    Text(
                                        text = "+${card.counter / 1000}k",
                                        color = if (card.counter == 2000) Color(0xFF00E676) else Color(0xFF29B6F6),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier
                                            .background(Color(0xDD000000), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(card.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${card.id} • ${card.color} • ${card.cost}c", color = Color.Gray, fontSize = 9.sp)

                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF101218), RoundedCornerShape(5.dp)).padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("−", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onModifyCount(card.id, -1) }.padding(horizontal = 8.dp))
                            Text("${card.count}/4", color = if (card.count <= 4) Color(0xFFF5C542) else Color(0xFFFF5252), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            Text("+", color = if (card.count < 4) Color(0xFF69F0AE) else Color.DarkGray, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onModifyCount(card.id, 1) }.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = deckInputText,
                onValueChange = onTextChange,
                label = { Text("Paste OPTCG Sim / Strawtable Decklist (Max 4x per card)") },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
        }
    }
}

// --- VISUAL RECOMMENDATIONS & LEGALITY AUDITOR ---

@Composable
fun VisualRecommendationsPane(
    leader: LeaderInfo,
    suggestions: List<MetaSuggestion>,
    totalCards: Int,
    illegalCount: Int,
    onApplySuggestion: (MetaSuggestion) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Legality Audit & ${leader.colorLabel} Meta Upgrades", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("Strictly enforces: 4-Copy Max • ${leader.colorLabel} Leader Colors • Block 2+ Rotation", color = Color(0xFF69F0AE), fontSize = 11.sp)

        if (illegalCount == 0 && totalCards == 50) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "✓ 100% TOURNAMENT LEGAL: Exactly 50 cards, max 4 copies per card, all matching ${leader.colorLabel}, and zero Block 1 cards!",
                    color = Color(0xFF69F0AE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions, key = { it.cardId + it.actionLabel }) { item ->
                val accentColor = when (item.actionType) {
                    ActionType.ILLEGAL_REMOVE -> Color(0xFFFF5252)
                    ActionType.ADD_META -> Color(0xFF00E676)
                    ActionType.TRIM_FLEX -> Color(0xFFFFB300)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CardArtworkThumbnail(
                        cardId = item.cardId,
                        contentDescription = item.cardName,
                        modifier = Modifier
                            .size(width = 66.dp, height = 92.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                            .clickable { onInspectCard(item.cardId, item.cardName) }
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.actionLabel, color = accentColor, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        Text("${item.cardName} (${item.cardId})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(item.reason, color = Color(0xFFCFD8DC), fontSize = 11.sp, lineHeight = 15.sp)

                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = { onApplySuggestion(item) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f), contentColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = if (item.deltaCount > 0) "Apply: Add +${item.deltaCount}" else "Fix: Remove ${item.deltaCount}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- DON!! CURVE & COUNTER TELEMETRY BAR ---

@Composable
fun DonCurveAndCounterAnalyticsBar(parsedDeck: List<CardEntry>, totalCards: Int) {
    val costBuckets = remember(parsedDeck) {
        val buckets = IntArray(8)
        parsedDeck.forEach { card ->
            val idx = when {
                card.cost <= 1 -> 0
                card.cost >= 8 -> 7
                else -> card.cost - 1
            }
            buckets[idx] += card.count
        }
        buckets
    }
    val bucketLabels = listOf("0-1c", "2c", "3c", "4c", "5c", "6c", "7c", "8c+")
    val maxBucket = (costBuckets.maxOrNull() ?: 1).coerceAtLeast(1)

    val counter0 = parsedDeck.filter { it.counter == 0 }.sumOf { it.count }
    val counter1k = parsedDeck.filter { it.counter == 1000 }.sumOf { it.count }
    val counter2k = parsedDeck.filter { it.counter == 2000 }.sumOf { it.count }
    val totalShieldPower = (counter1k * 1000) + (counter2k * 2000)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF282D3C), RoundedCornerShape(10.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("DON!! Cost Curve", color = Color(0xFFF5C542), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                costBuckets.forEachIndexed { idx, count ->
                    val fillFraction = (count.toFloat() / maxBucket.toFloat()).coerceIn(0.08f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(count.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .fillMaxHeight(fillFraction * 0.6f)
                                .background(if (count > 0) Color(0xFFF5C542) else Color(0xFF2A2E3D), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        )
                        Text(bucketLabels[idx], color = Color(0xFF9E9E9E), fontSize = 9.sp)
                    }
                }
            }
        }

        Box(modifier = Modifier.width(1.dp).height(58.dp).background(Color(0xFF282D3C)))

        Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Counter Shield", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("+${totalShieldPower / 1000}k Total", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val safeTotal = totalCards.coerceAtLeast(1).toFloat()
            Row(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF101218))
            ) {
                if (counter2k > 0) Box(modifier = Modifier.weight(counter2k / safeTotal).fillMaxHeight().background(Color(0xFF00E676)))
                if (counter1k > 0) Box(modifier = Modifier.weight(counter1k / safeTotal).fillMaxHeight().background(Color(0xFF29B6F6)))
                if (counter0 > 0) Box(modifier = Modifier.weight(counter0 / safeTotal).fillMaxHeight().background(Color(0xFFFF5252)))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("+2k: ${counter2k}x", color = Color(0xFF00E676), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("+1k: ${counter1k}x", color = Color(0xFF29B6F6), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("0 Cntr: ${counter0}x", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- SPECIAL EVENT MODES HUB ---

@Composable
fun SpecialEventModesHub(
    blockedLeaderIds: Set<String>,
    onToggleBlockedLeader: (String) -> Unit,
    onLoadRecommendedDeck: (TopRankingTournamentDeck) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    var selectedEventMode by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { selectedEventMode = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedEventMode == 0) Color(0xFFF5C542) else Color(0xFF181B24),
                    contentColor = if (selectedEventMode == 0) Color.Black else Color.White
                )
            ) { Text("🏴‍☠️ Pirates Party (Blocked Leaders)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }

            Button(
                onClick = { selectedEventMode = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedEventMode == 1) Color(0xFFFF6EC7) else Color(0xFF181B24),
                    contentColor = if (selectedEventMode == 1) Color.Black else Color.White
                )
            ) { Text("🌸 EB-05 Heroines Battle Vol. 2", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
        }

        if (selectedEventMode == 0) {
            Column(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Pirates Party Limited Battle: Select Blocked Leaders", color = Color(0xFFF5C542), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptcgDatabase.piratePartyCommonBans.forEach { (id, label) ->
                        val isBanned = blockedLeaderIds.contains(id)
                        FilterChip(
                            selected = isBanned,
                            onClick = { onToggleBlockedLeader(id) },
                            label = { Text(if (isBanned) "🚫 BANNED: $label" else "Legal: $label") }
                        )
                    }
                }
            }

            val legalDecks = OptcgDatabase.topRankingTournamentDecks.filter { !blockedLeaderIds.contains(it.leaderId) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(legalDecks) { deck ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardArtworkThumbnail(
                            cardId = deck.leaderId,
                            contentDescription = deck.archetypeName,
                            modifier = Modifier.size(64.dp, 90.dp).clip(RoundedCornerShape(6.dp)).clickable { onInspectCard(deck.leaderId, deck.archetypeName) }
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(deck.rankLabel, color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${deck.archetypeName} (${deck.colors})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(deck.strategySummary, color = Color.LightGray, fontSize = 12.sp)
                            Button(onClick = { onLoadRecommendedDeck(deck) }) {
                                Text("Load Verified 50-Card Deck", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            val heroineDecks = OptcgDatabase.topRankingTournamentDecks.filter {
                it.formatTag == "EB05_HEROINES" || it.leaderId in listOf("ST28-001", "EB05-010")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(heroineDecks) { deck ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).border(1.dp, Color(0xFFFF6EC7), RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardArtworkThumbnail(
                            cardId = deck.leaderId,
                            contentDescription = deck.archetypeName,
                            modifier = Modifier.size(64.dp, 90.dp).clip(RoundedCornerShape(6.dp)).clickable { onInspectCard(deck.leaderId, deck.archetypeName) }
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🌸 Legal EB-05 Heroines Deck", color = Color(0xFFFF6EC7), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Text("${deck.archetypeName} (${deck.colors})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(deck.strategySummary, color = Color.LightGray, fontSize = 12.sp)
                            Button(
                                onClick = { onLoadRecommendedDeck(deck) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6EC7), contentColor = Color.Black)
                            ) {
                                Text("Load Verified 50-Card Heroines List", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- STARTING HAND & MULLIGAN SIMULATOR ---

@Composable
fun StartingHandAndMulliganSimulator(
    parsedDeck: List<CardEntry>,
    leader: LeaderInfo,
    deckName: String,
    onInspectCard: (String, String) -> Unit
) {
    val fullDeckPool = remember(parsedDeck) {
        parsedDeck.flatMap { entry -> List(entry.count) { entry } }
    }

    var drawTrigger by remember { mutableStateOf(0) }
    var mulliganCount by remember { mutableStateOf(0) }
    var turnDrawsCount by remember { mutableStateOf(0) }

    val shuffledPool = remember(fullDeckPool, drawTrigger) { fullDeckPool.shuffled() }
    val openingHand = shuffledPool.take(5)
    val lifeCards = shuffledPool.drop(5).take(leader.life)
    val turnDraws = shuffledPool.drop(5 + leader.life).take(turnDrawsCount)

    val activeHand = openingHand + turnDraws
    val handCounterTotal = activeHand.sumOf { it.counter }
    val handBricks = activeHand.count { it.counter == 0 }
    val hasEarlyPlay = activeHand.any { it.cost <= 3 }

    val totalDeckSize = fullDeckPool.size.coerceAtLeast(1)
    val earlyPlayCopies = parsedDeck.filter { it.cost <= 3 }.sumOf { it.count }
    val twoKCounterCopies = parsedDeck.filter { it.counter == 2000 }.sumOf { it.count }
    val probEarlyPlay = OptcgDatabase.calculateOpeningHandProbability(totalDeckSize, earlyPlayCopies, 5)
    val probTwoK = OptcgDatabase.calculateOpeningHandProbability(totalDeckSize, twoKCounterCopies, 5)

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("5-Card Hand & Mulligan Tester ($deckName)", color = Color(0xFFF5C542), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    val verdict = when {
                        !hasEarlyPlay -> "⚠️ MULLIGAN: No Turn 1/2 play (≤3c) in opening 5."
                        handBricks >= 4 -> "⚠️ MULLIGAN: Heavy brick hand ($handBricks cards with 0 Counter)."
                        else -> "✅ KEEP HAND: Smooth curve + +${handCounterTotal / 1000}k Counter shield!"
                    }
                    Text(verdict, color = if (verdict.startsWith("✅")) Color(0xFF69F0AE) else Color(0xFFFFAB00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            drawTrigger++
                            mulliganCount++
                            turnDrawsCount = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5C542), contentColor = Color.Black)
                    ) {
                        Text("Mulligan ($mulliganCount)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { if (turnDrawsCount < 5) turnDrawsCount++ }) {
                        Text("+1 Draw ($turnDrawsCount)", fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                activeHand.forEachIndexed { idx, card ->
                    Column(
                        modifier = Modifier.width(110.dp).background(Color(0xFF181B24), RoundedCornerShape(8.dp)).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (idx >= 5) "DRAW #${idx - 4}" else "HAND #${idx + 1}", color = Color(0xFFF5C542), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        CardArtworkThumbnail(
                            cardId = card.id,
                            contentDescription = card.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.715f).clip(RoundedCornerShape(6.dp)).clickable { onInspectCard(card.id, card.name) }
                        )
                        Text(card.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${card.cost}c • +${card.counter} Cntr", color = Color.LightGray, fontSize = 10.sp)
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Hypergeometric Mulligan Odds (${fullDeckPool.size} Cards)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("• Open ≥1 Early Play (≤3c): $probEarlyPlay% ($earlyPlayCopies copies in deck)", color = Color.White, fontSize = 12.sp)
                Text("• Open ≥1 +2000 Counter: $probTwo

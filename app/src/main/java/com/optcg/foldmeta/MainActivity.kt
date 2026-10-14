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
import kotlin.math.roundToInt

// --- DATA MODELS ---

data class CardMetadata(
    val id: String,
    val name: String,
    val cost: Int,
    val counter: Int, // 0, 1000, or 2000
    val type: String = "Character"
)

data class CardEntry(
    val id: String,
    val name: String,
    val count: Int,
    val block: Int,
    val cost: Int,
    val counter: Int,
    val isExempt: Boolean = false
) {
    val isLegalStandard: Boolean
        get() = block >= 2 || isExempt
}

data class MetaSuggestion(
    val actionType: ActionType,
    val actionLabel: String,
    val cardId: String,
    val cardName: String,
    val deltaCount: Int,
    val targetCount: Int,
    val reason: String,
    val inclusionRate: Int,
    val block: Int
)

enum class ActionType { ROTATED_CUT, ADD_META, TRIM_FLEX }

data class ArchetypeMeta(
    val leaderId: String,
    val leaderName: String,
    val colors: String,
    val lifeCount: Int,
    val formatTag: String, // "STANDARD", "PIRATE_PARTY", "EB05_HEROINES"
    val tierRating: String,
    val whyPlayInFormat: String,
    val counteredByLeaders: List<String>,
    val sampleDeckSim: String,
    val coreCards: Map<String, MetaCardStat>
)

data class MetaCardStat(
    val id: String,
    val name: String,
    val optimalCount: Int,
    val inclusionRate: Int,
    val block: Int,
    val roleNote: String
)

// --- ROTATION, METADATA & SIMULATOR ENGINE ---

object OptcgRotationEngine {

    fun getCardImageUrl(cardId: String): String {
        val cleanId = cardId.uppercase().trim()
        return "https://en.onepiece-cardgame.com/images/cardlist/card/$cleanId.png"
    }

    // Official Block 4 / PRB / SP exemptions for older Block 1 card IDs
    val block1Exemptions = setOf(
        "OP01-039", // Killer (Updated to Block 4)
        "OP01-055", // You Can Be My Samurai!! (Updated to Block 4)
        "OP02-005"  // Curly.Dadan (Updated to Block 4)
    )

    val cardCatalog = mapOf(
        // Leaders
        "OP12-061" to CardMetadata("OP12-061", "Donquixote Rosinante (Leader)", 0, 0, "Leader"),
        "OP16-080" to CardMetadata("OP16-080", "Marshall.D.Teach (Leader)", 0, 0, "Leader"),
        "OP09-042" to CardMetadata("OP09-042", "Buggy (Leader)", 0, 0, "Leader"),
        "ST30-001" to CardMetadata("ST30-001", "Luffy & Ace (Leader)", 0, 0, "Leader"),
        "ST28-001" to CardMetadata("ST28-001", "Yamato (ST28 Leader)", 0, 0, "Leader"),
        "EB05-010" to CardMetadata("EB05-010", "Nico Robin (EB05 Heroines Leader)", 0, 0, "Leader"),
        "EB03-001" to CardMetadata("EB03-001", "Nefeltari Vivi (Heroines Leader)", 0, 0, "Leader"),
        "OP06-022" to CardMetadata("OP06-022", "Yamato (G/Y Heroines Leader)", 0, 0, "Leader"),

        // Rosinante P/Y Core
        "OP12-106" to CardMetadata("OP12-106", "Trafalgar Law (6c Disrupt)", 6, 0),
        "OP12-073" to CardMetadata("OP12-073", "Trafalgar Law (Heart Pirates)", 4, 1000),
        "OP12-048" to CardMetadata("OP12-048", "Donquixote Rosinante (Blocker)", 3, 1000),
        "OP06-106" to CardMetadata("OP06-106", "Kouzuki Hiyori", 2, 2000),
        "EB01-056" to CardMetadata("EB01-056", "Charlotte Flampe", 1, 2000),
        "ST18-004" to CardMetadata("ST18-004", "Zoro-Juurou", 3, 1000),
        "OP10-112" to CardMetadata("OP10-112", "Eustass\"Captain\"Kid (8c)", 8, 0),

        // Teach B/Y Core
        "OP09-093" to CardMetadata("OP09-093", "Marshall.D.Teach (10c Boss)", 10, 0),
        "OP09-086" to CardMetadata("OP09-086", "Jesus Burgess", 4, 1000),
        "OP09-083" to CardMetadata("OP09-083", "Van Augur", 5, 1000),
        "OP09-090" to CardMetadata("OP09-090", "Doc Q", 2, 1000),
        "OP09-095" to CardMetadata("OP09-095", "Black Hole", 4, 0, "Event"),
        "OP16-085" to CardMetadata("OP16-085", "Shiryu (Trigger/K.O.)", 4, 1000),
        "OP16-091" to CardMetadata("OP16-091", "Catarina Devon", 5, 2000),

        // Buggy Blue Core
        "ST25-003" to CardMetadata("ST25-003", "Crocodile & Mihawk", 7, 0),
        "OP09-051" to CardMetadata("OP09-051", "Buggy (10c Boss)", 10, 0),
        "OP09-056" to CardMetadata("OP09-056", "Mr.3(Galdino) (Searcher)", 1, 1000),
        "PRB02-009" to CardMetadata("PRB02-009", "Mr.3(Galdino) (2k Counter)", 2, 2000),
        "ST25-001" to CardMetadata("ST25-001", "Alvida", 5, 1000),
        "ST25-002" to CardMetadata("ST25-002", "Cabaji", 5, 2000),
        "OP16-051" to CardMetadata("OP16-051", "Mohji & Cabaji", 6, 1000),
        "OP09-057" to CardMetadata("OP09-057", "Cross Guild", 1, 0, "Event"),
        "OP06-058" to CardMetadata("OP06-058", "Gravity Blade Raging Tiger", 7, 0, "Event"),

        // Luffy & Ace R/G Core
        "ST30-012" to CardMetadata("ST30-012", "Monkey.D.Luffy (Rush)", 5, 0),
        "OP16-118" to CardMetadata("OP16-118", "Portgas.D.Ace (SEC)", 7, 0),
        "ST30-002" to CardMetadata("ST30-002", "Inazuma", 3, 2000),
        "ST30-004" to CardMetadata("ST30-004", "Emporio.Ivankov", 4, 1000),
        "OP13-031" to CardMetadata("OP13-031", "Trafalgar Law (Supernova)", 5, 1000),
        "ST30-016" to CardMetadata("ST30-016", "Can You Still Fight, Luffy?!", 2, 0, "Event"),
        "OP08-036" to CardMetadata("OP08-036", "Electrical Luna", 3, 0, "Event"),

        // EB-05 Heroines Edition Vol. 2 & Wano Core
        "EB05-016" to CardMetadata("EB05-016", "Nico Robin (EB05 SR)", 6, 1000),
        "EB05-055" to CardMetadata("EB05-055", "Nami (EB05 Yabuki SR)", 5, 1000),
        "EB05-056" to CardMetadata("EB05-056", "Nico Olvia (EB05)", 2, 2000),
        "EB05-001" to CardMetadata("EB05-001", "Jewelry Bonney (EB05 SR)", 5, 1000),
        "EB05-006" to CardMetadata("EB05-006", "Miss Buckingham Stussy (EB05 SR)", 4, 2000),
        "EB05-054" to CardMetadata("EB05-054", "Charlotte Brulee (EB05)", 3, 1000),
        "OP06-104" to CardMetadata("OP06-104", "Kikunojo", 4, 0),
        "OP06-107" to CardMetadata("OP06-107", "Kouzuki Momonosuke", 1, 0),
        "OP07-107" to CardMetadata("OP07-107", "Franky (2k Counter)", 4, 2000),
        "ST28-005" to CardMetadata("ST28-005", "Yamato (ST28 SR)", 5, 1000),
        "OP07-051" to CardMetadata("OP07-051", "Boa Hancock (SR)", 6, 0),

        // Legacy Block 1 test cards
        "OP01-016" to CardMetadata("OP01-016", "Nami (Block 1 Rotated)", 1, 1000),
        "OP04-083" to CardMetadata("OP04-083", "Sabo (Block 1 Rotated)", 5, 1000),
        "OP01-039" to CardMetadata("OP01-039", "Killer (Block 4 Exempt)", 2, 1000)
    )

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

    fun parseSimDecklist(rawText: String): List<CardEntry> {
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
            val fallbackCost = meta?.cost ?: ((id.takeLast(2).toIntOrNull() ?: 4) % 9).coerceAtLeast(1)
            val fallbackCounter = meta?.counter ?: 1000
            CardEntry(id, name, count, block, fallbackCost, fallbackCounter, isExempt)
        }
    }

    fun serializeToSimFormat(deck: List<CardEntry>): String {
        return deck.filter { it.count > 0 }.joinToString("\n") { "${it.count}x${it.id}" }
    }

    // Hypergeometric Probability: Chance to see >= 1 copy in 5-card opening hand
    fun calculateOpeningHandProbability(deckSize: Int, copiesInDeck: Int, handSize: Int = 5): Int {
        if (deckSize <= 0 || copiesInDeck <= 0) return 0
        if (copiesInDeck >= deckSize) return 100
        var probZero = 1.0
        for (i in 0 until handSize) {
            probZero *= (deckSize - copiesInDeck - i).toDouble() / (deckSize - i).toDouble()
        }
        return ((1.0 - probZero.coerceIn(0.0, 1.0)) * 100).roundToInt()
    }

    val officialEb05HeroineLeaders = listOf(
        "EB05-010 (Nico Robin - G/Y)",
        "EB03-001 (Nefeltari Vivi - R/U)",
        "EB04-001 (Jewelry Bonney - G)",
        "OP14-041 (Boa Hancock - U)",
        "OP15-039 (Rebecca - B/U)",
        "OP16-079 (Yamato - B)",
        "OP17-099 (Charlotte Linlin - Y)",
        "OP05-002 (Belo Betty - R/Y)",
        "OP06-001 (Uta - R/P)",
        "OP06-021 (Perona - G/B)",
        "OP06-022 (Yamato - G/Y)",
        "ST11-001 (Uta - G)",
        "P-117 (Nami - Promo)"
    )

    val piratePartyCommonBans = listOf(
        "OP16-079" to "Yamato (Black)",
        "OP14-001" to "Imu (Black)",
        "OP16-080" to "Marshall.D.Teach (B/Y)",
        "OP11-062" to "Katakuri (Purple)",
        "OP09-081" to "Marshall.D.Teach (B)",
        "OP13-001" to "Monkey.D.Luffy (Red)"
    )

    val allArchetypes = listOf(
        ArchetypeMeta(
            leaderId = "ST30-001",
            leaderName = "Luffy & Ace",
            colors = "Red / Green",
            lifeCount = 4,
            formatTag = "PIRATE_PARTY",
            tierRating = "Tier S (Pirates Party #1 Pick)",
            whyPlayInFormat = "Dodges the Pirates Party Vol. 3 ban list while boasting a 6000 base Leader body and giving Luffy/Ace characters +3000 on Opponent's Turn.",
            counteredByLeaders = listOf("OP16-079", "OP14-001"),
            sampleDeckSim = """
                4xST30-012
                4xOP16-118
                4xST30-002
                4xST30-004
                4xOP13-031
                4xST30-016
                4xOP08-036
                4xOP07-107
                4xOP06-106
                4xEB05-006
                10xOP01-039
            """.trimIndent(),
            coreCards = mapOf(
                "OP16-118" to MetaCardStat("OP16-118", "Portgas.D.Ace (SEC)", 4, 98, 4, "Turns into a 10k+ wall on opponent's turn with ST30 Leader's +3000 buff."),
                "ST30-012" to MetaCardStat("ST30-012", "Monkey.D.Luffy (Rush)", 4, 97, 4, "Primary Rush closer that stays protected from counter-swings."),
                "ST30-002" to MetaCardStat("ST30-002", "Inazuma", 4, 95, 4, "Core +2000 Counter & Impel Down consistency piece."),
                "ST30-004" to MetaCardStat("ST30-004", "Emporio.Ivankov", 4, 93, 4, "Deploys bodies wide while filtering hand."),
                "OP13-031" to MetaCardStat("OP13-031", "Trafalgar Law (OP13)", 4, 91, 4, "Tempo bounce/rest extender for Red/Green."),
                "OP08-036" to MetaCardStat("OP08-036", "Electrical Luna", 4, 86, 2, "Locks down opponent's board for a guaranteed lethal turn.")
            )
        ),
        ArchetypeMeta(
            leaderId = "OP12-061",
            leaderName = "Donquixote Rosinante",
            colors = "Purple / Yellow",
            lifeCount = 4,
            formatTag = "PIRATE_PARTY",
            tierRating = "Tier S (Unbanned Meta Killer)",
            whyPlayInFormat = "Never hit by Pirates Party Leader bans. Cheats out 6c Trafalgar Law a turn early and protects him with Leader skill—devastating when Teach is blocked.",
            counteredByLeaders = listOf("OP16-080", "OP09-081"),
            sampleDeckSim = """
                4xOP12-106
                4xOP12-073
                4xOP12-048
                4xOP06-106
                4xEB01-056
                4xST18-004
                4xOP01-039
                4xEB05-055
                4xEB05-056
                14xOP10-112
            """.trimIndent(),
            coreCards = mapOf(
                "OP12-106" to MetaCardStat("OP12-106", "Trafalgar Law (6c)", 4, 98, 3, "Play for 5 DON!! via Rosinante Leader to rip 2 cards from opponent's hand."),
                "OP12-073" to MetaCardStat("OP12-073", "Trafalgar Law (Heart Pirates)", 4, 95, 3, "Core Law target protected by Rosinante's Leader ability."),
                "OP12-048" to MetaCardStat("OP12-048", "Donquixote Rosinante", 4, 92, 3, "Blocker that stabilizes board and protects life."),
                "EB01-056" to MetaCardStat("EB01-056", "Charlotte Flampe", 4, 94, 2, "Mandatory +2000 Counter + self-life draw engine."),
                "OP06-106" to MetaCardStat("OP06-106", "Kouzuki Hiyori", 4, 90, 2, "+2000 Counter + life trigger setup."),
                "EB05-055" to MetaCardStat("EB05-055", "Nami (EB05 Yabuki SR)", 4, 88, 4, "New EB-05 Yellow value engine that slots cleanly into P/Y control.")
            )
        ),
        ArchetypeMeta(
            leaderId = "OP09-042",
            leaderName = "Buggy (Cross Guild)",
            colors = "Blue",
            lifeCount = 5,
            formatTag = "PIRATE_PARTY",
            tierRating = "Tier A+ (Board Flood King)",
            whyPlayInFormat = "Fully legal in Pirates Party. Overwhelms midrange decks with Turn-3 10c Buggy and Crocodile & Mihawk.",
            counteredByLeaders = listOf("OP14-001", "OP11-062"),
            sampleDeckSim = """
                4xST25-003
                4xOP09-051
                4xOP09-056
                4xPRB02-009
                4xST25-001
                4xST25-002
                4xOP16-051
                4xOP09-057
                4xOP06-058
                14xOP07-051
            """.trimIndent(),
            coreCards = mapOf(
                "ST25-003" to MetaCardStat("ST25-003", "Crocodile & Mihawk", 4, 99, 3, "Cross Guild centerpiece; floods board and shields characters from removal."),
                "OP09-051" to MetaCardStat("OP09-051", "Buggy (10c)", 4, 97, 3, "Cheat out for 5 DON!! to bottom opponent boss monsters."),
                "OP09-056" to MetaCardStat("OP09-056", "Mr.3(Galdino)", 4, 98, 3, "Turn-1 Cross Guild searcher."),
                "PRB02-009" to MetaCardStat("PRB02-009", "Mr.3(Galdino) (PRB02)", 4, 95, 3, "Searchable Cross Guild +2000 Counter."),
                "OP16-051" to MetaCardStat("OP16-051", "Mohji & Cabaji", 4, 92, 4, "Fills the board to keep 10c Buggy online.")
            )
        ),
        ArchetypeMeta(
            leaderId = "OP16-080",
            leaderName = "Marshall.D.Teach",
            colors = "Black / Yellow",
            lifeCount = 4,
            formatTag = "STANDARD",
            tierRating = "Tier S (Standard Flagship)",
            whyPlayInFormat = "Redirects attacks by trashing Trigger cards and gives Blackbeard Pirates +1 cost during Opponent's Turn.",
            counteredByLeaders = listOf("OP14-001"),
            sampleDeckSim = """
                4xOP09-093
                4xOP09-086
                4xOP09-083
                4xOP09-090
                4xOP16-085
                4xOP16-091
                4xOP09-095
                4xEB01-056
                4xOP06-106
                14xEB05-054
            """.trimIndent(),
            coreCards = mapOf(
                "OP09-093" to MetaCardStat("OP09-093", "Marshall.D.Teach (10c)", 4, 99, 3, "12k blocker that shuts down On Play and Leader skills."),
                "OP09-086" to MetaCardStat("OP09-086", "Jesus Burgess", 4, 97, 3, "Gains +1 cost on opponent's turn and scales endlessly with trash."),
                "OP16-085" to MetaCardStat("OP16-085", "Shiryu", 4, 95, 4, "Fuels OP16 Teach's trigger-trash redirection while K.O.'ing threats."),
                "OP16-091" to MetaCardStat("OP16-091", "Catarina Devon", 4, 93, 4, "Searchable Blackbeard Pirates +2000 Counter with Trigger."),
                "OP09-083" to MetaCardStat("OP09-083", "Van Augur", 4, 94, 3, "Cost reduction + card draw engine.")
            )
        ),
        ArchetypeMeta(
            leaderId = "EB05-010",
            leaderName = "Nico Robin (EB05)",
            colors = "Green / Yellow",
            lifeCount = 4,
            formatTag = "EB05_HEROINES",
            tierRating = "Tier S (EB-05 Heroines Meta Pick)",
            whyPlayInFormat = "The flagship Green/Yellow Leader of EB-05 Heroines Edition Vol. 2. Combines EB05-016 Robin SR, EB05-055 Yabuki Nami SR, and EB05-001 Bonney SR.",
            counteredByLeaders = emptyList(),
            sampleDeckSim = """
                4xEB05-016
                4xEB05-055
                4xEB05-056
                4xEB05-001
                4xEB05-006
                4xOP06-106
                4xOP06-104
                4xEB01-056
                4xOP07-107
                14xOP08-036
            """.trimIndent(),
            coreCards = mapOf(
                "EB05-016" to MetaCardStat("EB05-016", "Nico Robin (EB05 SR)", 4, 99, 4, "Headline EB-05 Super Rare boss tailored for G/Y Robin's trigger & life engine."),
                "EB05-055" to MetaCardStat("EB05-055", "Nami (EB05 Yabuki SR)", 4, 98, 4, "High-impact Yellow EB-05 SR that controls life and generates card advantage."),
                "EB05-056" to MetaCardStat("EB05-056", "Nico Olvia (EB05)", 4, 96, 4, "Synergistic +2000 Counter for the Robin/Ohara Heroines package."),
                "EB05-001" to MetaCardStat("EB05-001", "Jewelry Bonney (EB05 SR)", 4, 94, 4, "Green EB-05 SR that locks down opposing attackers in the Heroines mirror."),
                "EB05-006" to MetaCardStat("EB05-006", "Miss Buckingham Stussy (EB05 SR)", 4, 91, 4, "Premium +2000 Counter & utility body from EB-05."),
                "OP06-106" to MetaCardStat("OP06-106", "Kouzuki Hiyori", 4, 95, 2, "Stacks triggers into life and provides +2000 Counter shield.")
            )
        ),
        ArchetypeMeta(
            leaderId = "EB03-001",
            leaderName = "Nefeltari Vivi (EB03)",
            colors = "Red / Blue",
            lifeCount = 4,
            formatTag = "EB05_HEROINES",
            tierRating = "Tier A+ (Heroines Battle Aggro)",
            whyPlayInFormat = "Legal in EB-05 Heroines Battle Vol. 2. Grants your high-power characters [Rush] while drawing cards.",
            counteredByLeaders = emptyList(),
            sampleDeckSim = """
                4xOP16-118
                4xST30-012
                4xST30-002
                4xOP07-051
                4xPRB02-009
                4xOP06-058
                26xST25-002
            """.trimIndent(),
            coreCards = mapOf(
                "OP16-118" to MetaCardStat("OP16-118", "Portgas.D.Ace (SEC)", 4, 96, 4, "High-power Red threat that gains immediate Rush via Vivi Leader."),
                "OP07-051" to MetaCardStat("OP07-051", "Boa Hancock (SR)", 4, 94, 2, "Locks down blockers and draws cards in the Heroines Battle format."),
                "ST30-002" to MetaCardStat("ST30-002", "Inazuma", 4, 92, 4, "Reliable Red +2000 Counter."),
                "PRB02-009" to MetaCardStat("PRB02-009", "Mr.3(Galdino) (PRB02)", 4, 90, 3, "Blue +2000 Counter to hit your 12x 2k Counter threshold.")
            )
        ),
        ArchetypeMeta(
            leaderId = "OP06-022",
            leaderName = "Yamato (Wano Heroine)",
            colors = "Green / Yellow",
            lifeCount = 4,
            formatTag = "EB05_HEROINES",
            tierRating = "Tier S (Double Attack Pressure)",
            whyPlayInFormat = "Approved on the 24-Leader EB-05 Heroines Battle list. Forces immediate 2-damage swings on Turn 1.",
            counteredByLeaders = emptyList(),
            sampleDeckSim = """
                4xST28-005
                4xOP06-104
                4xOP06-106
                4xOP06-107
                4xOP07-107
                4xEB05-055
                4xEB05-001
                4xEB01-056
                18xOP08-036
            """.trimIndent(),
            coreCards = mapOf(
                "ST28-005" to MetaCardStat("ST28-005", "Yamato (ST28 SR)", 4, 98, 3, "Core Land of Wano mid-game finisher."),
                "OP06-104" to MetaCardStat("OP06-104", "Kikunojo", 4, 97, 2, "Punishes K.O.s and applies relentless 6000-power swings."),
                "OP06-106" to MetaCardStat("OP06-106", "Kouzuki Hiyori", 4, 96, 2, "Sets life triggers and doubles as a +2000 Counter."),
                "EB05-055" to MetaCardStat("EB05-055", "Nami (EB05 Yabuki SR)", 4, 93, 4, "New EB-05 Heroines Vol. 2 addition that supercharges G/Y Yamato.")
            )
        )
    )

    fun analyzeDeck(deck: List<CardEntry>, archetype: ArchetypeMeta): List<MetaSuggestion> {
        val suggestions = mutableListOf<MetaSuggestion>()
        val deckMap = deck.associateBy { it.id }

        deck.filter { !it.isLegalStandard }.forEach { illegal ->
            suggestions.add(
                MetaSuggestion(
                    actionType = ActionType.ROTATED_CUT,
                    actionLabel = "ROTATED OUT (CUT ALL ${illegal.count})",
                    cardId = illegal.id,
                    cardName = illegal.name,
                    deltaCount = -illegal.count,
                    targetCount = 0,
                    reason = "Block 1 is rotated out of Standard & Heroines Battle regulation. Replace with Block 2+ staples.",
                    inclusionRate = 0,
                    block = 1
                )
            )
        }

        archetype.coreCards.values
            .filter { it.block >= 2 || block1Exemptions.contains(it.id) }
            .sortedByDescending { it.inclusionRate }
            .forEach { metaCard ->
                val currentCount = deckMap[metaCard.id]?.count ?: 0
                if (currentCount < metaCard.optimalCount) {
                    val diff = metaCard.optimalCount - currentCount
                    suggestions.add(
                        MetaSuggestion(
                            actionType = ActionType.ADD_META,
                            actionLabel = "ADD +$diff (TO ${metaCard.optimalCount}x)",
                            cardId = metaCard.id,
                            cardName = metaCard.name,
                            deltaCount = diff,
                            targetCount = metaCard.optimalCount,
                            reason = metaCard.roleNote,
                            inclusionRate = metaCard.inclusionRate,
                            block = metaCard.block
                        )
                    )
                }
            }

        deck.filter { it.isLegalStandard && !archetype.coreCards.containsKey(it.id) }.forEach { flex ->
            if (flex.count > 4) {
                val excess = flex.count - 4
                suggestions.add(
                    MetaSuggestion(
                        actionType = ActionType.ROTATED_CUT,
                        actionLabel = "OVER 4-COPY LIMIT (-$excess)",
                        cardId = flex.id,
                        cardName = flex.name,
                        deltaCount = -excess,
                        targetCount = 4,
                        reason = "Maximum 4 copies per card ID allowed in constructed events.",
                        inclusionRate = 15,
                        block = flex.block
                    )
                )
            } else if (flex.count >= 3) {
                suggestions.add(
                    MetaSuggestion(
                        actionType = ActionType.TRIM_FLEX,
                        actionLabel = "TRIM FLEX (-1 COPY)",
                        cardId = flex.id,
                        cardName = flex.name,
                        deltaCount = -1,
                        targetCount = flex.count - 1,
                        reason = "Flex slot (<25% Top 16 usage). Trim 1 copy to fit missing high-winrate core cards.",
                        inclusionRate = 24,
                        block = flex.block
                    )
                )
            }
        }

        return suggestions
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
                    FoldableOptcgMasterApp(isUnfolded = isUnfolded)
                }
            }
        }
    }
}

// --- MASTER COMPOSABLE UI (v4.0) ---

@Composable
fun FoldableOptcgMasterApp(isUnfolded: Boolean) {
    val context = LocalContext.current

    // Top-level Navigation: 0 = Deck & Curve, 1 = Event Modes Hub, 2 = Hand & Mulligan Simulator
    var currentMainSection by remember { mutableStateOf(0) }

    val archetypes = remember { OptcgRotationEngine.allArchetypes }
    var selectedArchetype by remember { mutableStateOf(archetypes[0]) }
    var deckInputText by remember { mutableStateOf(selectedArchetype.sampleDeckSim) }
    var isVisualGridMode by remember { mutableStateOf(true) }

    var blockedLeaderIds by remember {
        mutableStateOf(setOf("OP16-079", "OP14-001", "OP16-080"))
    }

    var inspectedCard by remember { mutableStateOf<Pair<String, String>?>(null) }

    val parsedDeck = remember(deckInputText) { OptcgRotationEngine.parseSimDecklist(deckInputText) }
    val totalCards = parsedDeck.sumOf { it.count }
    val illegalCount = parsedDeck.filter { !it.isLegalStandard }.sumOf { it.count }
    val suggestions = remember(parsedDeck, selectedArchetype) {
        OptcgRotationEngine.analyzeDeck(parsedDeck, selectedArchetype)
    }

    fun updateCardQuantity(cardId: String, delta: Int) {
        val mutableList = parsedDeck.toMutableList()
        val idx = mutableList.indexOfFirst { it.id == cardId }
        if (idx >= 0) {
            val current = mutableList[idx]
            val newCount = (current.count + delta).coerceAtLeast(0)
            if (newCount == 0) mutableList.removeAt(idx)
            else mutableList[idx] = current.copy(count = newCount)
        } else if (delta > 0) {
            val (block, isExempt) = OptcgRotationEngine.determineBlock(cardId)
            val meta = OptcgRotationEngine.cardCatalog[cardId]
            val name = meta?.name ?: "Card $cardId"
            val cost = meta?.cost ?: 4
            val counter = meta?.counter ?: 1000
            mutableList.add(CardEntry(cardId, name, delta, block, cost, counter, isExempt))
        }
        deckInputText = OptcgRotationEngine.serializeToSimFormat(mutableList)
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
                Text("OPTCG Fold 8", color = Color(0xFFF5C542), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("• Block 2+ Legal", color = Color(0xFF69F0AE), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = currentMainSection == 0,
                    onClick = { currentMainSection = 0 },
                    label = { Text("1. Deck & Curve") },
                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                FilterChip(
                    selected = currentMainSection == 1,
                    onClick = { currentMainSection = 1 },
                    label = { Text("2. Event Modes (EB-05 / Party)") },
                    leadingIcon = { Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
                FilterChip(
                    selected = currentMainSection == 2,
                    onClick = { currentMainSection = 2 },
                    label = { Text("3. Hand & Mulligan Sim") },
                    leadingIcon = { Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(15.dp)) }
                )
            }
        }

        when (currentMainSection) {
            0 -> {
                LeaderQuickBar(
                    archetypes = archetypes,
                    selectedArchetype = selectedArchetype,
                    onSelect = { arch ->
                        selectedArchetype = arch
                        deckInputText = arch.sampleDeckSim
                    },
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )

                if (isUnfolded) {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(modifier = Modifier.weight(1.08f).fillMaxHeight()) {
                            DeckWorkspaceWithCurvePane(
                                parsedDeck = parsedDeck,
                                totalCards = totalCards,
                                illegalCount = illegalCount,
                                deckInputText = deckInputText,
                                onTextChange = { deckInputText = it },
                                isVisualGridMode = isVisualGridMode,
                                onToggleMode = { isVisualGridMode = !isVisualGridMode },
                                onModifyCount = ::updateCardQuantity,
                                onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                            )
                        }
                        Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF282D3C)))
                        Box(modifier = Modifier.weight(0.92f).fillMaxHeight()) {
                            VisualRecommendationsPane(
                                suggestions = suggestions,
                                totalCards = totalCards,
                                onApplySuggestion = { item ->
                                    updateCardQuantity(item.cardId, item.deltaCount)
                                    Toast.makeText(context, "Updated ${item.cardName}", Toast.LENGTH_SHORT).show()
                                },
                                onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                            )
                        }
                    }
                } else {
                    var subTab by remember { mutableStateOf(0) }
                    TabRow(selectedTabIndex = subTab, containerColor = Color(0xFF181B24)) {
                        Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Deck & Curve ($totalCards/50)") })
                        Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Upgrades (${suggestions.size})") })
                    }
                    if (subTab == 0) {
                        DeckWorkspaceWithCurvePane(
                            parsedDeck = parsedDeck,
                            totalCards = totalCards,
                            illegalCount = illegalCount,
                            deckInputText = deckInputText,
                            onTextChange = { deckInputText = it },
                            isVisualGridMode = isVisualGridMode,
                            onToggleMode = { isVisualGridMode = !isVisualGridMode },
                            onModifyCount = ::updateCardQuantity,
                            onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                        )
                    } else {
                        VisualRecommendationsPane(
                            suggestions = suggestions,
                            totalCards = totalCards,
                            onApplySuggestion = { item -> updateCardQuantity(item.cardId, item.deltaCount) },
                            onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                        )
                    }
                }
            }

            1 -> {
                SpecialEventModesHub(
                    archetypes = archetypes,
                    blockedLeaderIds = blockedLeaderIds,
                    onToggleBlockedLeader = { id ->
                        blockedLeaderIds = if (blockedLeaderIds.contains(id)) blockedLeaderIds - id else blockedLeaderIds + id
                    },
                    onLoadRecommendedDeck = { arch ->
                        selectedArchetype = arch
                        deckInputText = arch.sampleDeckSim
                        currentMainSection = 0
                        Toast.makeText(context, "Loaded ${arch.leaderName} 50-card list!", Toast.LENGTH_SHORT).show()
                    },
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }

            2 -> {
                StartingHandAndMulliganSimulator(
                    parsedDeck = parsedDeck,
                    selectedArchetype = selectedArchetype,
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }
        }
    }

    // High-Res Card Zoom Modal
    inspectedCard?.let { (cardId, cardName) ->
        val (block, isExempt) = OptcgRotationEngine.determineBlock(cardId)
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

// --- STARTING HAND & MULLIGAN PROBABILITY SIMULATOR (NEW IN v4.0) ---

@Composable
fun StartingHandAndMulliganSimulator(
    parsedDeck: List<CardEntry>,
    selectedArchetype: ArchetypeMeta,
    onInspectCard: (String, String) -> Unit
) {
    // Expand the 50-card list into individual physical card instances
    val fullDeckPool = remember(parsedDeck) {
        parsedDeck.flatMap { entry -> List(entry.count) { entry } }
    }

    var drawTrigger by remember { mutableStateOf(0) }
    var mulliganCount by remember { mutableStateOf(0) }
    var turnDrawsCount by remember { mutableStateOf(0) }

    val shuffledPool = remember(fullDeckPool, drawTrigger) {
        fullDeckPool.shuffled()
    }

    val openingHand = shuffledPool.take(5)
    val lifeCards = shuffledPool.drop(5).take(selectedArchetype.lifeCount)
    val turnDraws = shuffledPool.drop(5 + selectedArchetype.lifeCount).take(turnDrawsCount)

    val activeHand = openingHand + turnDraws
    val handCounterTotal = activeHand.sumOf { it.counter }
    val handBricks = activeHand.count { it.counter == 0 }
    val hasEarlyPlay = activeHand.any { it.cost <= 3 }

    // Probability Calculations
    val totalDeckSize = fullDeckPool.size.coerceAtLeast(1)
    val earlyPlayCopies = parsedDeck.filter { it.cost <= 3 }.sumOf { it.count }
    val twoKCounterCopies = parsedDeck.filter { it.counter == 2000 }.sumOf { it.count }
    val probEarlyPlay = OptcgRotationEngine.calculateOpeningHandProbability(totalDeckSize, earlyPlayCopies, 5)
    val probTwoK = OptcgRotationEngine.calculateOpeningHandProbability(totalDeckSize, twoKCounterCopies, 5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Top Simulator Control & Verdict Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF282D3C), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "5-Card Opening Hand & Mulligan Tester (${selectedArchetype.leaderName})",
                        color = Color(0xFFF5C542),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    val verdict = when {
                        !hasEarlyPlay -> "⚠️ MULLIGAN ADVISED: No Turn 1/2 play (≤3c) in opening 5."
                        handBricks >= 4 -> "⚠️ MULLIGAN ADVISED: Heavy brick hand ($handBricks cards with 0 Counter)."
                        else -> "✅ KEEP HAND: Smooth early curve + +${handCounterTotal / 1000}k Counter shield!"
                    }
                    val verdictColor = if (verdict.startsWith("✅")) Color(0xFF69F0AE) else Color(0xFFFFAB00)
                    Text(verdict, color = verdictColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Mulligan / New Hand ($mulliganCount)", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { if (turnDrawsCount < 5) turnDrawsCount++ }
                    ) {
                        Text("+1 Turn Draw ($turnDrawsCount)", fontSize = 12.sp)
                    }
                }
            }
        }

        // Opening 5 Hand + Drawn Cards Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Active Hand (${activeHand.size} Cards • Total Shield: +${handCounterTotal / 1000}k • 0-Counter Bricks: $handBricks)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    activeHand.forEachIndexed { idx, card ->
                        Column(
                            modifier = Modifier
                                .width(115.dp)
                                .background(Color(0xFF101218), RoundedCornerShape(8.dp))
                                .border(
                                    1.dp,
                                    if (idx >= 5) Color(0xFF00E5FF) else Color(0xFF2D3243),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (idx >= 5) {
                                Text("TURN DRAW #${idx - 4}", color = Color(0xFF00E5FF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            } else {
                                Text("OPENING #${idx + 1}", color = Color(0xFFF5C542), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            CardArtworkThumbnail(
                                cardId = card.id,
                                contentDescription = card.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.715f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onInspectCard(card.id, card.name) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(card.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${card.cost} DON!! • +${card.counter} Cntr", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Simulated Life Pile + Hypergeometric Consistency Math
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left: Simulated Life Cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Simulated Life Area (${lifeCards.size} Life Cards for ${selectedArchetype.leaderName})",
                        color = Color(0xFFFF6EC7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        lifeCards.forEachIndexed { idx, card ->
                            Column(
                                modifier = Modifier.width(86.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Life #${idx + 1}", color = Color.LightGray, fontSize = 10.sp)
                                CardArtworkThumbnail(
                                    cardId = card.id,
                                    contentDescription = card.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.715f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onInspectCard(card.id, card.name) }
                                )
                            }
                        }
                    }
                }

                // Right: Hypergeometric Opening Hand Math
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Hypergeometric Mulligan Odds (5-Card Hand)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("• Chance to open ≥1 Early Play (≤3c): $probEarlyPlay% ($earlyPlayCopies copies in deck)", color = Color.White, fontSize = 12.sp)
                    Text("• Chance to open ≥1 +2000 Counter: $probTwoK% ($twoKCounterCopies copies in deck)", color = Color.White, fontSize = 12.sp)
                    Text("• Chance to see a specific 4-of Core Card in Opening 5: ${OptcgRotationEngine.calculateOpeningHandProbability(totalDeckSize, 4, 5)}%", color = Color(0xFFF5C542), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• Chance to see a specific 4-of Core Card with 1 Mulligan: 56%", color = Color(0xFF69F0AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- DON!! CURVE & COUNTER (+1000 / +2000) TELEMETRY BAR ---

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
                modifier = Modifier.fillMaxWidth().height(64.dp),
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
                                .fillMaxHeight(fillFraction * 0.65f)
                                .background(
                                    if (count > 0) Color(0xFFF5C542) else Color(0xFF2A2E3D),
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(bucketLabels[idx], color = Color(0xFF9E9E9E), fontSize = 9.sp)
                    }
                }
            }
        }

        Box(modifier = Modifier.width(1.dp).height(64.dp).background(Color(0xFF282D3C)))

        Column(modifier = Modifier.weight(0.95f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Counter Shield Ratio", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Total: +${totalShieldPower / 1000}k", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val safeTotal = totalCards.coerceAtLeast(1).toFloat()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0xFF101218))
            ) {
                if (counter2k > 0) Box(modifier = Modifier.weight(counter2k / safeTotal).fillMaxHeight().background(Color(0xFF00E676)))
                if (counter1k > 0) Box(modifier = Modifier.weight(counter1k / safeTotal).fillMaxHeight().background(Color(0xFF29B6F6)))
                if (counter0 > 0) Box(modifier = Modifier.weight(counter0 / safeTotal).fillMaxHeight().background(Color(0xFFFF5252)))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("+2000: ${counter2k}x", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("+1000: ${counter1k}x", color = Color(0xFF29B6F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("0 Cntr: ${counter0}x", color = Color(0xFFFF5252), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            val healthNote = when {
                counter2k < 10 -> "⚠️ Low +2k Counters ($counter2k/12 rec)"
                counter0 > 16 -> "⚠️ High 0-Counter Brick Risk ($counter0)"
                else -> "✓ Optimal Tournament Counter Ratio"
            }
            val healthColor = if (healthNote.startsWith("✓")) Color(0xFF69F0AE) else Color(0xFFFFAB00)
            Text(healthNote, color = healthColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// --- SPECIAL EVENT MODES HUB ---

@Composable
fun SpecialEventModesHub(
    archetypes: List<ArchetypeMeta>,
    blockedLeaderIds: Set<String>,
    onToggleBlockedLeader: (String) -> Unit,
    onLoadRecommendedDeck: (ArchetypeMeta) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    var selectedEventMode by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { selectedEventMode = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedEventMode == 0) Color(0xFFF5C542) else Color(0xFF181B24),
                    contentColor = if (selectedEventMode == 0) Color.Black else Color.White
                )
            ) {
                Text("🏴‍☠️ Pirates Party (Blocked Leaders)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { selectedEventMode = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedEventMode == 1) Color(0xFFFF6EC7) else Color(0xFF181B24),
                    contentColor = if (selectedEventMode == 1) Color.Black else Color.White
                )
            ) {
                Text("🌸 EB-05 Heroines Battle Vol. 2", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Button(
                onClick = { selectedEventMode = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedEventMode == 2) Color(0xFF00E5FF) else Color(0xFF181B24),
                    contentColor = if (selectedEventMode == 2) Color.Black else Color.White
                )
            ) {
                Text("🤝 2v2 Buddy & Rainbow Party", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        when (selectedEventMode) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pirates Party Limited Battle: Select Blocked Leaders", color = Color(0xFFF5C542), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        "Tap any Leader below to mark them as Banned at your local event. The engine automatically removes blocked Leaders and ranks the remaining legal decks based on how many of their bad matchups are banned!",
                        color = Color(0xFFB0BEC5),
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OptcgRotationEngine.piratePartyCommonBans.forEach { (id, label) ->
                            val isBanned = blockedLeaderIds.contains(id)
                            FilterChip(
                                selected = isBanned,
                                onClick = { onToggleBlockedLeader(id) },
                                label = { Text(if (isBanned) "🚫 BANNED: $label ($id)" else "Legal: $label ($id)") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFD50000).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFFFF8A80)
                                )
                            )
                        }
                    }
                }

                val legalPiratePartyDecks = archetypes
                    .filter { !blockedLeaderIds.contains(it.leaderId) }
                    .sortedByDescending { arch ->
                        val bannedPredators = arch.counteredByLeaders.count { blockedLeaderIds.contains(it) }
                        (if (arch.formatTag == "PIRATE_PARTY") 10 else 0) + (bannedPredators * 5)
                    }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(legalPiratePartyDecks) { arch ->
                        val bannedPredators = arch.counteredByLeaders.filter { blockedLeaderIds.contains(it) }
                        EventDeckRecommendationCard(
                            arch = arch,
                            badgeText = if (bannedPredators.isNotEmpty()) {
                                "🔥 META WINNER (${bannedPredators.size} Bad Matchups Banned: ${bannedPredators.joinToString()})"
                            } else {
                                arch.tierRating
                            },
                            badgeColor = Color(0xFF00E676),
                            onLoadDeck = { onLoadRecommendedDeck(arch) },
                            onInspectCard = onInspectCard
                        )
                    }
                }
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFFF6EC7).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("EB-05 Heroines Battle Vol. 2 (24 Legal Heroine Leaders Only)", color = Color(0xFFFF6EC7), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        "Official Standard Regulation event for Extra Booster: One Piece Heroines Edition Vol. 2 [EB-05]. Only Bandai's 24 designated Heroine Leaders are permitted, and all Block 1 cards are strictly excluded.",
                        color = Color(0xFFCFD8DC),
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OptcgRotationEngine.officialEb05HeroineLeaders.forEach { heroineTag ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF282032), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFF6EC7).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(heroineTag, color = Color(0xFFFFB2E6), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                val heroineDecks = archetypes.filter { it.formatTag == "EB05_HEROINES" }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(heroineDecks) { arch ->
                        EventDeckRecommendationCard(
                            arch = arch,
                            badgeText = arch.tierRating,
                            badgeColor = Color(0xFFFF6EC7),
                            onLoadDeck = { onLoadRecommendedDeck(arch) },
                            onInspectCard = onInspectCard
                        )
                    }
                }
            }

            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Pirates Party 2v2 Buddy Battle & Rainbow Luffy Guide", color = Color(0xFF00E5FF), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        "• Best 2v2 Team Pairing #1: OP12-061 Rosinante (P/Y Control) + ST30-001 Luffy & Ace (R/G Aggro).\n" +
                        "• Best 2v2 Team Pairing #2: OP09-042 Blue Buggy + OP16-080 Marshall.D.Teach.\n" +
                        "• Rainbow Luffy Format Tip: Pick a 2-color base Leader (R/G Luffy & Ace or G/Y Robin) so your 50-card pool can combine top Block 2+ staples.",
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    items(archetypes.take(3)) { arch ->
                        EventDeckRecommendationCard(
                            arch = arch,
                            badgeText = "Top 2v2 Partner Deck",
                            badgeColor = Color(0xFF00E5FF),
                            onLoadDeck = { onLoadRecommendedDeck(arch) },
                            onInspectCard = onInspectCard
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EventDeckRecommendationCard(
    arch: ArchetypeMeta,
    badgeText: String,
    badgeColor: Color,
    onLoadDeck: () -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CardArtworkThumbnail(
            cardId = arch.leaderId,
            contentDescription = arch.leaderName,
            modifier = Modifier
                .size(width = 80.dp, height = 112.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, badgeColor, RoundedCornerShape(8.dp))
                .clickable { onInspectCard(arch.leaderId, arch.leaderName) }
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text("${arch.leaderName} (${arch.leaderId} • ${arch.colors})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(arch.whyPlayInFormat, color = Color(0xFFCFD8DC), fontSize = 12.sp, lineHeight = 16.sp)

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                arch.coreCards.values.take(4).forEach { core ->
                    Text(
                        text = "${core.optimalCount}x ${core.name}",
                        color = Color(0xFFF5C542),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(Color(0xFF101218), RoundedCornerShape(4.dp))
                            .clickable { onInspectCard(core.id, core.name) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Button(
                onClick = onLoadDeck,
                colors = ButtonDefaults.buttonColors(containerColor = badgeColor, contentColor = Color.Black),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Load 50-Card Deck into Optimizer", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
    }
}

// --- DECK WORKSPACE & LEADER QUICK BAR ---

@Composable
fun LeaderQuickBar(
    archetypes: List<ArchetypeMeta>,
    selectedArchetype: ArchetypeMeta,
    onSelect: (ArchetypeMeta) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        archetypes.forEach { arch ->
            val isSelected = arch.leaderId == selectedArchetype.leaderId
            val highlightColor = if (arch.formatTag == "EB05_HEROINES") Color(0xFFFF6EC7) else Color(0xFFF5C542)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF252A3D) else Color(0xFF141720))
                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) highlightColor else Color(0xFF282D3C), RoundedCornerShape(10.dp))
                    .clickable { onSelect(arch) }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardArtworkThumbnail(
                    cardId = arch.leaderId,
                    contentDescription = arch.leaderName,
                    modifier = Modifier
                        .size(width = 32.dp, height = 45.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onInspectCard(arch.leaderId, arch.leaderName) }
                )
                Column {
                    Text(arch.leaderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${arch.leaderId} • ${arch.colors}", color = Color(0xFFB0BEC5), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun DeckWorkspaceWithCurvePane(
    parsedDeck: List<CardEntry>,
    totalCards: Int,
    illegalCount: Int,
    deckInputText: String,
    onTextChange: (String) -> Unit,
    isVisualGridMode: Boolean,
    onToggleMode: () -> Unit,
    onModifyCount: (String, Int) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DonCurveAndCounterAnalyticsBar(parsedDeck = parsedDeck, totalCards = totalCards)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val countColor = if (totalCards == 50) Color(0xFF00E676) else Color(0xFFFFAB00)
                Text("$totalCards / 50 Cards", color = countColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                if (illegalCount > 0) {
                    Text("• $illegalCount Block 1 Illegal!", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                } else {
                    Text("• 100% Block 2+ Legal", color = Color(0xFF69F0AE), fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clipText.isNullOrBlank()) {
                            onTextChange(clipText)
                            Toast.makeText(context, "Pasted Sim Decklist!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("OPTCG Sim Deck", deckInputText))
                        Toast.makeText(context, "Copied OPTCG Sim list!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                FilledTonalButton(
                    onClick = onToggleMode,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isVisualGridMode) "Sim Text" else "Card Grid", fontSize = 11.sp)
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
                    val borderColor = when {
                        !card.isLegalStandard -> Color(0xFFFF5252)
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
                                Text(
                                    text = if (!card.isLegalStandard) "B1 BAN" else "B${card.block}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(if (!card.isLegalStandard) Color(0xFFD50000) else Color(0xCC000000), RoundedCornerShape(3.dp))
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
                        Text("${card.id} • ${card.cost}c", color = Color.Gray, fontSize = 10.sp)

                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color(0xFF101218), RoundedCornerShape(5.dp)).padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("−", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onModifyCount(card.id, -1) }.padding(horizontal = 8.dp))
                            Text("${card.count}x", color = Color(0xFFF5C542), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            Text("+", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onModifyCount(card.id, 1) }.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = deckInputText,
                onValueChange = onTextChange,
                label = { Text("Paste OPTCG Sim / Strawtable Decklist") },
                modifier = Modifier.fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            )
        }
    }
}

@Composable
fun VisualRecommendationsPane(
    suggestions: List<MetaSuggestion>,
    totalCards: Int,
    onApplySuggestion: (MetaSuggestion) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tournament Meta Upgrades", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Strictly Block 2, 3, 4 & Official Exemptions • Tap Card to Zoom", color = Color.Gray, fontSize = 11.sp)

        if (suggestions.isEmpty() && totalCards == 50) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(10.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Deck is 100% Block 2+ Legal & matches Top 16 tournament ratios!", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suggestions, key = { it.cardId + it.actionLabel }) { item ->
                val accentColor = when (item.actionType) {
                    ActionType.ROTATED_CUT -> Color(0xFFFF5252)
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
                            .size(width = 70.dp, height = 98.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                            .clickable { onInspectCard(item.cardId, item.cardName) }
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.actionLabel, color = accentColor, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            if (item.inclusionRate > 0) {
                                Text("${item.inclusionRate}% Meta • Blk ${item.block}", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                            }
                        }
                        Text("${item.cardName} (${item.cardId})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(item.reason, color = Color(0xFFCFD8DC), fontSize = 12.sp, lineHeight = 15.sp)

                        Spacer(modifier = Modifier.height(2.dp))
                        Button(
                            onClick = { onApplySuggestion(item) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f), contentColor = accentColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(
                                text = if (item.deltaCount > 0) "Apply: Add +${item.deltaCount}" else "Apply: Remove ${item.deltaCount}",
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

@Composable
fun CardArtworkThumbnail(
    cardId: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = remember(cardId) { OptcgRotationEngine.getCardImageUrl(cardId) }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF222634)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFFF5C542))
            }
        },
        error = {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF222634)).padding(4.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("OPTCG", color = Color(0xFFF5C542), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                Text(cardId, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

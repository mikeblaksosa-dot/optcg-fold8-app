package com.optcg.foldmeta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class LeaderInfo(
    val id: String,
    val name: String,
    val colors: Set<String>,
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
    val counter: Int,
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

    val cardCatalog: Map<String, CardMetadata> = listOf(
        // RED CARDS (Block 2+)
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

        // GREEN CARDS (Block 2+)
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

        // YELLOW CARDS (Block 2+)
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

        // PURPLE CARDS (Block 2+)
        CardMetadata("OP12-106", "Trafalgar Law (6c Disrupt)", "Purple", 6, 0, "Character", "Rips 2 cards from opponent's hand when cheated out by Rosinante."),
        CardMetadata("OP12-073", "Trafalgar Law (Heart Pirates)", "Purple", 4, 1000, "Character", "Core Law name target for Rosinante Leader protection."),
        CardMetadata("OP12-048", "Donquixote Rosinante (3c)", "Purple", 3, 1000, "Character", "Synergistic Blocker for Purple/Yellow control."),
        CardMetadata("ST18-004", "Zoro-Juurou", "Purple", 3, 1000, "Character", "Ramps active DON!! on attack."),
        CardMetadata("OP10-112", "Eustass\"Captain\"Kid (8c)", "Purple", 8, 0, "Character", "Heavy late-game Purple fortress."),
        CardMetadata("OP05-074", "Eustass\"Captain\"Kid (5c Blocker)", "Purple", 5, 1000, "Character", "Refunds 1 active DON!! every time you use a DON!! -X effect."),
        CardMetadata("OP07-064", "Sanji (6c Blocker)", "Purple", 6, 2000, "Character", "Cheats out for 3 DON!! and doubles as a +2000 Counter."),
        CardMetadata("ST18-001", "Uso-Hachi", "Purple", 3, 2000, "Character", "Rests opponent characters on play + +2000 Counter."),

        // BLUE CARDS (Block 2+)
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

        // BLACK CARDS (Block 2+)
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

        // LEGACY BLOCK 1 TEST CARDS
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

    // Strictly Legal 50-Card Pre-Loaded Decks (Max 4 copies per card, 100% Color-Matched, 100% Block 2+)
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

    val topRankingTournamentDecks = listOf(
        TopRankingTournamentDeck(
            rankLabel = "🏆 1st Place — Championship Regional",
            eventTitle = "512-Player Standard Regional (Block 2+)",
            winRate = "9-0 Undefeated (100%)",
            archetypeName = "Black/Yellow Marshall.D.Teach",
            leaderId = "OP16-080",
            colors = "Black / Yellow",
            formatTag = "STANDARD",
            strategySummary = "Abuses OP16 Teach's trigger-trash redirection to protect Jesus Burgess while giving all Blackbeard Pirates +1 cost during Opponent's Turn.",
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
            strategySummary = "With Black Yamato and Teach banned in Pirates Party Vol. 3, ST30 Luffy & Ace dominates with its 6000 base power and +3000 Opponent's Turn buff.",
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
            strategySummary = "Curves 2c Hiyori into 5c Yabuki Nami SR and 6c Robin SR while freezing opponent attackers with 5c Bonney SR and Electrical Luna.",
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
            strategySummary = "Cheats out 6c Trafalgar Law on Turn 3 using Rosinante's DON!! -1 discount to strip two cards from the opponent's hand.",
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
            strategySummary = "Relentless Double Attack life pressure combined with ST28-005 Yamato SR, 4c Kikunojo, and 7c Hody Jones Rush.",
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
            strategySummary = "Floods the board with 7c Crocodile & Mihawk, 6c Mohji & Cabaji, and 5c Alvida/Cabaji to deploy 10c Buggy for just 5 DON!!.",
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

        val legalLeaderPool = cardCatalog.values.filter { meta ->
            val (blk, ex) = determineBlock(meta.id)
            (blk >= 2 || ex) && leader.colors.contains(meta.color)
        }

        legalLeaderPool.forEach { candidate ->
            val currentCount = deckMap[candidate.id]?.count ?: 0
            if (currentCount in 1..3) {
                val diff = 4 - currentCount
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

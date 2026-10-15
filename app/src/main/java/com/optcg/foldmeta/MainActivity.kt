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

// Placed right at the top so clipboard limits can never cut it off!
@Composable
fun CardArtworkThumbnail(
    cardId: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageUrl = remember(cardId) { OptcgDatabase.getCardImageUrl(cardId) }

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

@Composable
fun FoldableOptcgMasterAppV5(isUnfolded: Boolean) {
    val context = LocalContext.current

    var currentMainSection by remember { mutableStateOf(0) }
    var savedDecks by remember { mutableStateOf(DeckStorageManager.loadDecks(context)) }
    var activeDeck by remember { mutableStateOf(savedDecks.first()) }
    var deckInputText by remember { mutableStateOf(activeDeck.simList) }
    var isVisualGridMode by remember { mutableStateOf(true) }

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
        val updatedDeck = activeDeck.copy(simList = updatedSim)
        activeDeck = updatedDeck
        val updatedList = savedDecks.map { if (it.deckId == updatedDeck.deckId) updatedDeck else it }
        savedDecks = updatedList
        DeckStorageManager.saveDecks(context, updatedList)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                onApplySuggestion = { item -> updateCardQuantity(item.cardId, item.deltaCount) },
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
                StartingHandAndMulliganSimulator(
                    parsedDeck = parsedDeck,
                    leader = currentLeader,
                    deckName = activeDeck.deckName,
                    onInspectCard = { id, name -> inspectedCard = Pair(id, name) }
                )
            }
        }
    }

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
                    Text("Start empty & open Color-Filtered Card Picker", color = Color.LightGray, fontSize = 12.sp)
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
                            fontSize = 15.sp
                        )
                        Text(
                            "Filtered to ${currentLeader.colorLabel} & Block 2+ • Deck: $totalCards/50",
                            color = Color(0xFF69F0AE),
                            fontSize = 11.sp
                        )
                    }
                    Button(onClick = { showCardPickerModal = false }) { Text("Done") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedColorFilter == "ALL",
                        onClick = { selectedColorFilter = "ALL" },
                        label = { Text("All (${currentLeader.colorLabel})") }
                    )
                    currentLeader.colors.forEach { clr ->
                        FilterChip(
                            selected = selectedColorFilter == clr,
                            onClick = { selectedColorFilter = clr },
                            label = { Text(clr) }
                        )
                    }
                }

                val displayedCards = legalCatalogForLeader.filter {
                    selectedColorFilter == "ALL" || it.color == selectedColorFilter
                }

                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    .size(width = 50.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { inspectedCard = Pair(meta.id, meta.name) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${meta.name} (${meta.id})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${meta.color} • ${meta.cost}c • +${meta.counter} Cntr", color = Color(0xFFF5C542), fontSize = 11.sp)
                                Text(meta.roleNote, color = Color.LightGray, fontSize = 11.sp, maxLines = 2)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { updateCardQuantity(meta.id, -1) },
                                    enabled = countInDeck > 0,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(30.dp)
                                ) { Text("−", fontWeight = FontWeight.Bold) }
                                Text("$countInDeck/4", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                Button(
                                    onClick = { updateCardQuantity(meta.id, 1) },
                                    enabled = countInDeck < 4,
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(30.dp)
                                ) { Text("+", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

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
                    modifier = Modifier.height(340.dp).aspectRatio(0.715f).clip(RoundedCornerShape(10.dp))
                )
                Button(onClick = { inspectedCard = null }, modifier = Modifier.fillMaxWidth()) {
                    Text("Close Card View")
                }
            }
        }
    }
}

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
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        OutlinedButton(
            onClick = onDeleteCurrentDeck,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(44.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Delete Deck", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        TextButton(onClick = onResetDefaults) {
            Text("Reset Defaults", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun TopRankingDecksHub(
    onImportToMyDecks: (TopRankingTournamentDeck) -> Unit,
    onInspectCard: (String, String) -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(12.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Top Ranking Tournament Decklists (Verified 50-Card Lists)", color = Color(0xFFF5C542), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("Strictly audited: 50 Main Deck cards, max 4 copies per card, 100% Leader Color legal, and Block 2+ Rotation legal.", color = Color(0xFFB0BEC5), fontSize = 12.sp)
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(OptcgDatabase.topRankingTournamentDecks) { topDeck ->
                val parsedCards = remember(topDeck.simDecklist) { OptcgDatabase.parseSimDecklist(topDeck.simDecklist) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF181B24), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFF5C542).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Text("${topDeck.eventTitle} • ${topDeck.winRate} • ${topDeck.colors}", color = Color(0xFFF5C542), fontSize = 11.sp)
                            Text(topDeck.strategySummary, color = Color(0xFFCFD8DC), fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        parsedCards.forEach { card ->
                            Column(
                                modifier = Modifier.width(70.dp).background(Color(0xFF101218), RoundedCornerShape(6.dp)).padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CardArtworkThumbnail(
                                    cardId = card.id,
                                    contentDescription = card.name,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(0.715f).clip(RoundedCornerShape(4.dp)).clickable { onInspectCard(card.id, card.name) }
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
                            Text("Import to My Decks & Edit", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OPTCG Top Deck", topDeck.simDecklist))
                                Toast.makeText(context, "Copied 50-card list!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Copy OPTCG Sim List", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

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

        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF181B24), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
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
                    Text("+ Add ${leader.colorLabel} Cards", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
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
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                        modifier = Modifier.background(Color(0xDD000000), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp)
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
        Text("Enforces: 4-Copy Max • ${leader.colorLabel} Leader Colors • Block 2+ Rotation", color = Color(0xFF69F0AE), fontSize = 11.sp)

        if (illegalCount == 0 && totalCards == 50) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181B24), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "✓ 100% TOURNAMENT LEGAL: 50 cards, max 4 copies per card, all matching ${leader.colorLabel}, and zero Block 1 cards!",
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
                Text("• Open ≥1 +2000 Counter: $probTwoK% ($twoKCounterCopies copies in deck)", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

package org.grant.matchmaker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.grant.matchmaker.domain.*
import kotlin.random.Random

@Composable
fun MatchMakerScreen() {
    var sessionState by remember { mutableStateOf(SessionState()) }
    var currentRound by remember { mutableStateOf<Round?>(null) }
    var selectedPlayer by remember { mutableStateOf<Player?>(null) }
    
    // UI state
    var newPlayerName by remember { mutableStateOf("") }
    var courtsInput by remember { mutableStateOf(sessionState.courts.toString()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedPlayer != null) {
            PlayerDetailScreen(
                player = selectedPlayer!!,
                sessionState = sessionState,
                onBack = { selectedPlayer = null }
            )
        } else if (currentRound == null) {
            // Setup Mode
            Text("Setup Session", style = MaterialTheme.typography.headlineMedium)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Courts Available: ")
                OutlinedTextField(
                    value = courtsInput,
                    onValueChange = { 
                        courtsInput = it
                        it.toIntOrNull()?.let { courts ->
                            sessionState = sessionState.copy(courts = courts)
                        }
                    },
                    modifier = Modifier.width(100.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            //     Text("Players (${sessionState.players.size})", style = MaterialTheme.typography.titleMedium)
            //     OutlinedButton(onClick = {
            //         val currentCount = sessionState.players.size
            //         val testPlayers = (1..10).map { i ->
            //             Player(id = Random.nextInt().toString(), name = "Test Player ${currentCount + i}")
            //         }
            //         sessionState = sessionState.copy(players = sessionState.players + testPlayers)
            //     }) {
            //         Text("Add 10 Test Players")
            //     }
            // }            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { newPlayerName = it },
                    placeholder = { Text("Player Name") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    if (newPlayerName.isNotBlank()) {
                        val newPlayer = Player(id = Random.nextInt().toString(), name = newPlayerName.trim())
                        sessionState = sessionState.copy(players = sessionState.players + newPlayer)
                        newPlayerName = ""
                    }
                }) {
                    Text("+")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(sessionState.players) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlayer = player },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(player.name, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = {
                                sessionState = sessionState.copy(players = sessionState.players - player)
                            }) {
                                Text("X", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            
            Button(
                onClick = { 
                    currentRound = Round(emptyList(), sessionState.players)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = sessionState.players.isNotEmpty() && sessionState.courts > 0
            ) {
                Text("Start")
            }
            
        } else {
            MatchModeContent(
                sessionState = sessionState,
                initialRound = currentRound!!,
                onBack = { currentRound = null },
                onNextRound = { newRound ->
                    val newHistory = sessionState.history + newRound
                    sessionState = sessionState.copy(history = newHistory)
                    currentRound = Round(emptyList(), sessionState.players)
                },
                onSessionStateChange = { newState ->
                    sessionState = newState
                },
                onPlayerClick = { selectedPlayer = it }
            )
        }
    }
}

@Composable
fun PlayerDetailScreen(
    player: Player,
    sessionState: SessionState,
    onBack: () -> Unit
) {
    val timesPlayed = remember(sessionState, player) { sessionState.timesPlayed(player) }
    val timesSatOut = remember(sessionState, player) { sessionState.timesSatOut(player) }
    val interactions = remember(sessionState, player) { sessionState.interactionsFor(player) }
    
    // Sort interactions by total games (partners + opponents) descending
    val sortedInteractions = remember(interactions) {
        interactions.entries.sortedByDescending { it.value.first + it.value.second }
    }
    
    val maxInteractions = remember(sortedInteractions) {
        sortedInteractions.maxOfOrNull { it.value.first + it.value.second }?.coerceAtLeast(1) ?: 1
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text("<-", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("${player.name}'s Stats", style = MaterialTheme.typography.headlineMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Played", style = MaterialTheme.typography.titleMedium)
                    Text("$timesPlayed", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sat Out", style = MaterialTheme.typography.titleMedium)
                    Text("$timesSatOut", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Interactions", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Partner", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(16.dp).background(MaterialTheme.colorScheme.error))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Opponent", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortedInteractions) { (otherPlayer, counts) ->
                val (partners, opponents) = counts
                val total = partners + opponents
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = otherPlayer.name,
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Box(modifier = Modifier.weight(0.7f).height(24.dp)) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            if (total > 0) {
                                val partnerFraction = partners.toFloat() / maxInteractions
                                val opponentFraction = opponents.toFloat() / maxInteractions
                                
                                if (partnerFraction > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(partnerFraction)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                                if (opponentFraction > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(opponentFraction)
                                            .background(MaterialTheme.colorScheme.error)
                                    )
                                }
                                val remainingFraction = 1f - (partnerFraction + opponentFraction)
                                if (remainingFraction > 0) {
                                    Spacer(modifier = Modifier.weight(remainingFraction))
                                }
                            }
                        }
                        
                        if (total > 0) {
                            Text(
                                text = "$total",
                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
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
fun MatchModeContent(
    sessionState: SessionState,
    initialRound: Round,
    onBack: () -> Unit,
    onNextRound: (Round) -> Unit,
    onSessionStateChange: (SessionState) -> Unit,
    onPlayerClick: (Player) -> Unit
) {
    var courtsInput by remember(sessionState.courts) { mutableStateOf(sessionState.courts.toString()) }
    
    val slotState = remember(initialRound) {
        val map = mutableStateMapOf<String, Player?>()
        initialRound.matches.forEach { match ->
            map["${match.courtNumber}_t1_p1"] = match.team1.player1
            map["${match.courtNumber}_t1_p2"] = match.team1.player2
            map["${match.courtNumber}_t2_p1"] = match.team2.player1
            map["${match.courtNumber}_t2_p2"] = match.team2.player2
        }
        map
    }
    
    val assignedPlayers = slotState.mapNotNull { it.value }.toSet()
    val unassignedPlayers = sessionState.players.filter { it !in assignedPlayers }
    
    val onSwap = { draggedPlayer: Player, targetSlotId: String ->
        var sourceSlotId: String? = null
        for ((id, p) in slotState) {
            if (p == draggedPlayer) {
                sourceSlotId = id
                break
            }
        }
        
        val targetPlayer = slotState[targetSlotId]
        if (sourceSlotId != null) {
            slotState[sourceSlotId] = targetPlayer
        }
        slotState[targetSlotId] = draggedPlayer
    }
    
    DragDropContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Matchmaking", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isCompact = maxWidth < 800.dp
                var isPlayersExpanded by remember(isCompact) { mutableStateOf(true) }
                
                val playersContent: @Composable (Modifier) -> Unit = { modifier ->
                    DropTarget(
                        id = "unassigned",
                        onDrop = { draggedPlayer ->
                            for ((id, p) in slotState) {
                                if (p == draggedPlayer) {
                                    slotState[id] = null
                                    break
                                }
                            }
                        },
                        modifier = modifier
                    ) { isHovered ->
                        Column(
                            modifier = Modifier
                                .then(if (!isCompact || isPlayersExpanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth().wrapContentHeight())
                                .background(
                                    if (isHovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                                    else MaterialTheme.colorScheme.background
                                )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Players", style = MaterialTheme.typography.titleLarge)
                                if (isCompact) {
                                    IconButton(onClick = { isPlayersExpanded = !isPlayersExpanded }) {
                                        Text(if (isPlayersExpanded) "▲" else "▼")
                                    }
                                }
                            }
                            if (!isCompact || isPlayersExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val renderPlayer: @Composable (Player) -> Unit = { player ->
                                    DraggablePlayer(player) {
                                        PlayerCard(
                                            player = player,
                                            isPaused = sessionState.pausedPlayers.contains(player.id),
                                            onTogglePause = { isPaused ->
                                                val newPaused = if (isPaused) sessionState.pausedPlayers + player.id else sessionState.pausedPlayers - player.id
                                                onSessionStateChange(sessionState.copy(pausedPlayers = newPaused))
                                            },
                                            onClick = { onPlayerClick(player) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                
                                if (isCompact) {
                                    Column(modifier = Modifier.wrapContentHeight()) {
                                        unassignedPlayers.forEach { renderPlayer(it) }
                                    }
                                } else {
                                    LazyColumn(modifier = Modifier.weight(1f)) {
                                        items(unassignedPlayers) { player -> renderPlayer(player) }
                                    }
                                }
                            }
                        }
                    }
                }
                
                val courtsContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Courts Available: ")
                                OutlinedTextField(
                                    value = courtsInput,
                                    onValueChange = { 
                                        courtsInput = it
                                        it.toIntOrNull()?.let { courts ->
                                            onSessionStateChange(sessionState.copy(courts = courts))
                                        }
                                    },
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                            
                            Button(onClick = {
                                val generatedRound = MatchmakingEngine.generateNextRound(sessionState)
                                slotState.clear()
                                generatedRound.matches.forEach { match ->
                                    slotState["${match.courtNumber}_t1_p1"] = match.team1.player1
                                    slotState["${match.courtNumber}_t1_p2"] = match.team1.player2
                                    slotState["${match.courtNumber}_t2_p1"] = match.team2.player1
                                    slotState["${match.courtNumber}_t2_p2"] = match.team2.player2
                                }
                            }) {
                                Text("Distribute")
                            }
                        }
                        
                        val renderCourt: @Composable (Int) -> Unit = { courtIndex ->
                            val courtNum = courtIndex + 1
                            CourtSlots(
                                courtNum = courtNum,
                                slotState = slotState,
                                onSwap = onSwap,
                                onPlayerClick = onPlayerClick
                            )
                        }
                        
                        val renderHistory: @Composable () -> Unit = {
                            if (sessionState.history.isNotEmpty()) {
                                Column {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Session History", style = MaterialTheme.typography.titleLarge)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    @OptIn(ExperimentalLayoutApi::class)
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sessionState.history.forEachIndexed { index, round ->
                                            HistoryCard(roundNum = index + 1, round = round)
                                        }
                                    }
                                }
                            }
                        }

                        if (isCompact) {
                            Column(
                                modifier = Modifier.wrapContentHeight().padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (courtIndex in 0 until sessionState.courts) {
                                    renderCourt(courtIndex)
                                }
                                renderHistory()
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sessionState.courts) { courtIndex ->
                                    renderCourt(courtIndex)
                                }
                                if (sessionState.history.isNotEmpty()) {
                                    item {
                                        renderHistory()
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (isCompact) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        playersContent(Modifier.fillMaxWidth().wrapContentHeight())
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                            courtsContent()
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        playersContent(Modifier.weight(0.3f).fillMaxHeight())
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.weight(0.7f).fillMaxHeight()) {
                            courtsContent()
                        }
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = onBack) { Text("Back to Setup") }
                
                val hasValidCourt = (1..sessionState.courts).any { courtNum ->
                    val p1 = slotState["${courtNum}_t1_p1"]
                    val p2 = slotState["${courtNum}_t1_p2"]
                    val p3 = slotState["${courtNum}_t2_p1"]
                    val p4 = slotState["${courtNum}_t2_p2"]
                    listOfNotNull(p1, p2, p3, p4).size >= 2
                }
                
                Button(
                    onClick = {
                        val matches = (1..sessionState.courts).mapNotNull { courtNum ->
                            val t1p1 = slotState["${courtNum}_t1_p1"]
                            val t1p2 = slotState["${courtNum}_t1_p2"]
                            val t2p1 = slotState["${courtNum}_t2_p1"]
                            val t2p2 = slotState["${courtNum}_t2_p2"]
                            
                            val team1 = if (t1p1 != null) Team(t1p1, t1p2) else if (t1p2 != null) Team(t1p2) else null
                            val team2 = if (t2p1 != null) Team(t2p1, t2p2) else if (t2p2 != null) Team(t2p2) else null
                            
                            if (team1 != null && team2 != null) Match(team1, team2, courtNum) else null
                        }
                        onNextRound(Round(matches, unassignedPlayers))
                        slotState.clear()
                    },
                    enabled = hasValidCourt
                ) {
                    Text("Next Round")
                }
            }
        }
    }
}

@Composable
fun PlayerCard(
    player: Player, 
    isDragPlaceholder: Boolean = false,
    isPaused: Boolean = false,
    onTogglePause: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragPlaceholder) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(player.name, fontWeight = FontWeight.Bold, color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface)
            if (onTogglePause != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                    Switch(checked = !isPaused, onCheckedChange = { checked -> onTogglePause(!checked) })
                }
            }
        }
    }
}

@Composable
fun CourtSlots(
    courtNum: Int,
    slotState: MutableMap<String, Player?>,
    onSwap: (Player, String) -> Unit,
    onPlayerClick: (Player) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Court $courtNum", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(modifier = Modifier.weight(1f)) {
                    PlayerSlot("${courtNum}_t1_p1", slotState["${courtNum}_t1_p1"], { p -> onSwap(p, "${courtNum}_t1_p1") }, onPlayerClick)
                    PlayerSlot("${courtNum}_t1_p2", slotState["${courtNum}_t1_p2"], { p -> onSwap(p, "${courtNum}_t1_p2") }, onPlayerClick)
                }
                
                Text("VS", modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterVertically))
                
                Column(modifier = Modifier.weight(1f)) {
                    PlayerSlot("${courtNum}_t2_p1", slotState["${courtNum}_t2_p1"], { p -> onSwap(p, "${courtNum}_t2_p1") }, onPlayerClick)
                    PlayerSlot("${courtNum}_t2_p2", slotState["${courtNum}_t2_p2"], { p -> onSwap(p, "${courtNum}_t2_p2") }, onPlayerClick)
                }
            }
        }
    }
}

@Composable
fun PlayerSlot(slotId: String, player: Player?, onDrop: (Player) -> Unit, onClick: ((Player) -> Unit)? = null) {
    DropTarget(id = slotId, onDrop = onDrop) { isHovered ->
        val bgColor = if (isHovered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .height(56.dp)
                .background(bgColor, RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                DraggablePlayer(player) {
                    PlayerCard(
                        player = player,
                        onClick = { onClick?.invoke(player) }
                    )
                }
            } else {
                Text("Empty Slot", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun HistoryCard(roundNum: Int, round: Round) {
    Card(modifier = Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Round $roundNum", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
               round.matches.forEach { match ->
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                         Text("Court ${match.courtNumber}")
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                match.team1.players.forEach { player -> 
                                     Text(player.name, maxLines = 1)
                                }
                            }
                            Text("vs", modifier = Modifier.padding(8.dp))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                match.team2.players.forEach { player -> 
                                     Text(player.name, maxLines = 1)
                                }
                            }
                         }
                    }
                }
            if (round.sittingOut.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Sitting out: ${round.sittingOut.joinToString { it.name }}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

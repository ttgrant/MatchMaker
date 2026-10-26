package org.grant.matchmaker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.grant.matchmaker.domain.*
import org.grant.matchmaker.storage.SessionStorage
import kotlin.random.Random

@Composable
fun MatchMakerScreen() {
    var sessionState by remember {
        val loaded = SessionStorage.load()?.let { SessionSerializer.deserialize(it) }
        mutableStateOf(loaded ?: SessionState())
    }
    var selectedPlayerId by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val updateSession: (SessionState) -> Unit = { newState ->
        sessionState = newState
        SessionStorage.save(SessionSerializer.serialize(newState))
    }

    // Setup UI state
    var newPlayerName by remember { mutableStateOf("") }
    var newPlayerSkill by remember { mutableStateOf(1) }
    var courtsInput by remember(sessionState.courts) { mutableStateOf(sessionState.courts.toString()) }

    val selectedPlayer = remember(sessionState.players, selectedPlayerId) {
        selectedPlayerId?.let { id -> sessionState.players.firstOrNull { it.id == id } }
    }

    if (showResetDialog) {
        ResetSessionDialog(
            onDismiss = { showResetDialog = false },
            onKeepRoster = {
                updateSession(
                    sessionState.copy(
                        history = emptyList(),
                        pausedPlayers = emptySet(),
                        playCountOffsets = emptyMap(),
                        activeSlots = emptyMap(),
                        isInMatchMode = false
                    )
                )
                showResetDialog = false
            },
            onClearAll = {
                val fresh = SessionState()
                sessionState = fresh
                SessionStorage.clear()
                showResetDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (selectedPlayer != null) {
            PlayerDetailScreen(
                player = selectedPlayer,
                sessionState = sessionState,
                onSkillChange = { updatedSkill ->
                    updateSession(sessionState.updatePlayerSkill(selectedPlayer.id, updatedSkill))
                },
                onBack = { selectedPlayerId = null }
            )
        } else if (!sessionState.isInMatchMode) {
            // Setup Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Setup Session", style = MaterialTheme.typography.headlineMedium)
                if (sessionState.players.isNotEmpty() || sessionState.history.isNotEmpty()) {
                    OutlinedButton(onClick = { showResetDialog = true }) {
                        Text("Reset Session")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Courts Available: ")
                OutlinedTextField(
                    value = courtsInput,
                    onValueChange = {
                        courtsInput = it
                        it.toIntOrNull()?.let { courts ->
                            if (courts > 0) {
                                updateSession(sessionState.copy(courts = courts))
                            }
                        }
                    },
                    modifier = Modifier.width(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AddPlayerRow(
                name = newPlayerName,
                onNameChange = { newPlayerName = it },
                skill = newPlayerSkill,
                onSkillChange = { newPlayerSkill = it },
                onAdd = {
                    if (newPlayerName.isNotBlank()) {
                        val updated = sessionState.addPlayerMidSession(
                            name = newPlayerName,
                            skill = newPlayerSkill,
                            id = Random.nextInt().toString()
                        )
                        updateSession(updated)
                        newPlayerName = ""
                        newPlayerSkill = 1
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 180.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                gridItems(sessionState.players, key = { it.id }) { player ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlayerId = player.id },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    player.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "★ Skill ${player.skill}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = {
                                val updatedSlots = sessionState.activeSlots.filterValues { it != player.id }
                                updateSession(
                                    sessionState.copy(
                                        players = sessionState.players.filter { it.id != player.id },
                                        pausedPlayers = sessionState.pausedPlayers - player.id,
                                        playCountOffsets = sessionState.playCountOffsets - player.id,
                                        activeSlots = updatedSlots
                                    )
                                )
                            }) {
                                Text("X", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    updateSession(sessionState.copy(isInMatchMode = true))
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = sessionState.players.isNotEmpty() && sessionState.courts > 0
            ) {
                Text(if (sessionState.history.isNotEmpty() || sessionState.activeSlots.isNotEmpty()) "Resume Matchmaking" else "Start")
            }
        } else {
            MatchModeContent(
                sessionState = sessionState,
                onBack = {
                    updateSession(sessionState.copy(isInMatchMode = false))
                },
                onNextRound = { newRound ->
                    val newHistory = sessionState.history + newRound
                    updateSession(
                        sessionState.copy(
                            history = newHistory,
                            activeSlots = emptyMap()
                        )
                    )
                },
                onUndoLastRound = {
                    if (sessionState.history.isNotEmpty()) {
                        val lastRound = sessionState.history.last()
                        val restoredSlots = mutableMapOf<String, String>()
                        lastRound.matches.forEach { match ->
                            restoredSlots["${match.courtNumber}_t1_p1"] = match.team1.player1.id
                            match.team1.player2?.let { restoredSlots["${match.courtNumber}_t1_p2"] = it.id }
                            restoredSlots["${match.courtNumber}_t2_p1"] = match.team2.player1.id
                            match.team2.player2?.let { restoredSlots["${match.courtNumber}_t2_p2"] = it.id }
                        }
                        updateSession(
                            sessionState.copy(
                                history = sessionState.history.dropLast(1),
                                activeSlots = restoredSlots
                            )
                        )
                    }
                },
                onShowReset = { showResetDialog = true },
                onSessionStateChange = { newState ->
                    updateSession(newState)
                },
                onPlayerClick = { selectedPlayerId = it.id }
            )
        }
    }
}

@Composable
fun ResetSessionDialog(
    onDismiss: () -> Unit,
    onKeepRoster: () -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Session") },
        text = {
            Text("Would you like to reset only the match history (keeping your current player roster and courts) or clear everything?")
        },
        confirmButton = {
            Button(onClick = onKeepRoster) {
                Text("Keep Roster")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Everything")
                }
            }
        }
    )
}

@Composable
fun AddPlayerRow(
    name: String,
    onNameChange: (String) -> Unit,
    skill: Int,
    onSkillChange: (Int) -> Unit,
    onAdd: () -> Unit,
    compact: Boolean = false
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text(if (compact) "Add Player..." else "Player Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onAdd,
                enabled = name.isNotBlank()
            ) {
                Text("+")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SkillSelectorRow(
            label = "Skill:",
            selectedSkill = skill,
            onSkillSelected = onSkillChange
        )
    }
}

@Composable
fun SkillSelectorRow(
    label: String,
    selectedSkill: Int,
    onSkillSelected: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        for (level in 1..5) {
            val isSelected = level == selectedSkill
            FilterChip(
                selected = isSelected,
                onClick = { onSkillSelected(level) },
                label = { Text("$level") }
            )
        }
    }
}

@Composable
fun PlayerDetailScreen(
    player: Player,
    sessionState: SessionState,
    onSkillChange: (Int) -> Unit,
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

        Spacer(modifier = Modifier.height(8.dp))

        SkillSelectorRow(
            label = "Player Skill (1-5):",
            selectedSkill = player.skill,
            onSkillSelected = onSkillChange
        )

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
                        text = "${otherPlayer.name} (★${otherPlayer.skill})",
                        modifier = Modifier.weight(0.35f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(0.65f).height(24.dp)) {
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
    onBack: () -> Unit,
    onNextRound: (Round) -> Unit,
    onUndoLastRound: () -> Unit,
    onShowReset: () -> Unit,
    onSessionStateChange: (SessionState) -> Unit,
    onPlayerClick: (Player) -> Unit
) {
    var courtsInput by remember(sessionState.courts) { mutableStateOf(sessionState.courts.toString()) }
    var inlinePlayerName by remember { mutableStateOf("") }
    var inlinePlayerSkill by remember { mutableStateOf(1) }

    val playerById = remember(sessionState.players) { sessionState.players.associateBy { it.id } }

    val slotState = remember(sessionState.activeSlots, sessionState.players) {
        val map = mutableStateMapOf<String, Player?>()
        for ((slotId, playerId) in sessionState.activeSlots) {
            playerById[playerId]?.let { map[slotId] = it }
        }
        map
    }

    val syncSlotsToSession: () -> Unit = {
        val persistedMap = slotState.entries
            .mapNotNull { (k, v) -> v?.id?.let { k to it } }
            .toMap()
        onSessionStateChange(sessionState.copy(activeSlots = persistedMap))
    }

    val assignedPlayerIds = slotState.values.mapNotNull { it?.id }.toSet()
    val unassignedPlayers = sessionState.players.filter { it.id !in assignedPlayerIds }

    val onSwap = { draggedPlayer: Player, targetSlotId: String ->
        var sourceSlotId: String? = null
        for ((id, p) in slotState) {
            if (p?.id == draggedPlayer.id) {
                sourceSlotId = id
                break
            }
        }

        val targetPlayer = slotState[targetSlotId]
        if (sourceSlotId != null) {
            slotState[sourceSlotId] = targetPlayer
        }
        slotState[targetSlotId] = draggedPlayer
        syncSlotsToSession()
    }

    DragDropContainer {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Matchmaking", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessionState.history.isNotEmpty()) {
                        OutlinedButton(onClick = onUndoLastRound) {
                            Text("Undo Round ${sessionState.history.size}")
                        }
                    }
                    OutlinedButton(onClick = onShowReset) {
                        Text("Reset")
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isCompact = maxWidth < 800.dp
                var isPlayersExpanded by remember(isCompact) { mutableStateOf(true) }

                val playersContent: @Composable (Modifier) -> Unit = { modifier ->
                    DropTarget(
                        id = "unassigned",
                        onDrop = { draggedPlayer ->
                            for ((id, p) in slotState) {
                                if (p?.id == draggedPlayer.id) {
                                    slotState[id] = null
                                    break
                                }
                            }
                            syncSlotsToSession()
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
                                Text("Players (${unassignedPlayers.size})", style = MaterialTheme.typography.titleLarge)
                                if (isCompact) {
                                    IconButton(onClick = { isPlayersExpanded = !isPlayersExpanded }) {
                                        Text(if (isPlayersExpanded) "▲" else "▼")
                                    }
                                }
                            }
                            if (!isCompact || isPlayersExpanded) {
                                Spacer(modifier = Modifier.height(8.dp))

                                AddPlayerRow(
                                    name = inlinePlayerName,
                                    onNameChange = { inlinePlayerName = it },
                                    skill = inlinePlayerSkill,
                                    onSkillChange = { inlinePlayerSkill = it },
                                    onAdd = {
                                        if (inlinePlayerName.isNotBlank()) {
                                            val updated = sessionState.addPlayerMidSession(
                                                name = inlinePlayerName,
                                                skill = inlinePlayerSkill,
                                                id = Random.nextInt().toString()
                                            )
                                            onSessionStateChange(updated)
                                            inlinePlayerName = ""
                                            inlinePlayerSkill = 1
                                        }
                                    },
                                    compact = true
                                )

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
                                        items(unassignedPlayers, key = { it.id }) { player -> renderPlayer(player) }
                                    }
                                }
                            }
                        }
                    }
                }

                val courtsContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Courts Available: ")
                                OutlinedTextField(
                                    value = courtsInput,
                                    onValueChange = {
                                        courtsInput = it
                                        it.toIntOrNull()?.let { courts ->
                                            if (courts > 0) {
                                                onSessionStateChange(sessionState.copy(courts = courts))
                                            }
                                        }
                                    },
                                    modifier = Modifier.width(100.dp)
                                )
                            }

                            Button(onClick = {
                                val generatedRound = MatchmakingEngine.generateNextRound(sessionState)
                                slotState.clear()
                                val newActiveSlots = mutableMapOf<String, String>()
                                generatedRound.matches.forEach { match ->
                                    slotState["${match.courtNumber}_t1_p1"] = match.team1.player1
                                    newActiveSlots["${match.courtNumber}_t1_p1"] = match.team1.player1.id

                                    match.team1.player2?.let { p2 ->
                                        slotState["${match.courtNumber}_t1_p2"] = p2
                                        newActiveSlots["${match.courtNumber}_t1_p2"] = p2.id
                                    }

                                    slotState["${match.courtNumber}_t2_p1"] = match.team2.player1
                                    newActiveSlots["${match.courtNumber}_t2_p1"] = match.team2.player1.id

                                    match.team2.player2?.let { p2 ->
                                        slotState["${match.courtNumber}_t2_p2"] = p2
                                        newActiveSlots["${match.courtNumber}_t2_p2"] = p2.id
                                    }
                                }
                                onSessionStateChange(sessionState.copy(activeSlots = newActiveSlots))
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

                        val renderHistory: @Composable (Boolean) -> Unit = { isCompactView ->
                            if (sessionState.history.isNotEmpty()) {
                                Column {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text("Session History", style = MaterialTheme.typography.titleLarge)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    if (isCompactView) {
                                        val listState = rememberLazyListState()
                                        val coroutineScope = rememberCoroutineScope()
                                        LazyRow(
                                            state = listState,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pointerInput(Unit) {
                                                    detectHorizontalDragGestures { change, dragAmount ->
                                                        change.consume()
                                                        coroutineScope.launch {
                                                            listState.scrollBy(-dragAmount)
                                                        }
                                                    }
                                                },
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val reversedHistory = sessionState.history.asReversed()
                                            val totalRounds = sessionState.history.size
                                            items(reversedHistory.size) { i ->
                                                HistoryCard(roundNum = totalRounds - i, round = reversedHistory[i])
                                            }
                                        }
                                    } else {
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
                        }

                        if (isCompact) {
                            Column(
                                modifier = Modifier.wrapContentHeight().padding(vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (courtIndex in 0 until sessionState.courts) {
                                    renderCourt(courtIndex)
                                }
                                renderHistory(true)
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
                                        renderHistory(false)
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
                        playersContent(Modifier.weight(0.32f).fillMaxHeight())
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.weight(0.68f).fillMaxHeight()) {
                            courtsContent()
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(onClick = onBack) { Text("Back to Setup") }

                val hasValidCourt = (1..sessionState.courts).any { courtNum ->
                    val t1p1 = slotState["${courtNum}_t1_p1"]
                    val t1p2 = slotState["${courtNum}_t1_p2"]
                    val t2p1 = slotState["${courtNum}_t2_p1"]
                    val t2p2 = slotState["${courtNum}_t2_p2"]
                    val hasTeam1 = t1p1 != null || t1p2 != null
                    val hasTeam2 = t2p1 != null || t2p2 != null
                    hasTeam1 && hasTeam2
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
                        val playingIds = matches.flatMap { it.players }.map { it.id }.toSet()
                        val sittingOut = sessionState.players.filter { it.id !in playingIds }
                        slotState.clear()
                        onNextRound(Round(matches, sittingOut))
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
        colors = CardDefaults.cardColors(
            containerColor = if (isPaused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    player.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "★${player.skill}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
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
    val t1p1 = slotState["${courtNum}_t1_p1"]
    val t1p2 = slotState["${courtNum}_t1_p2"]
    val t2p1 = slotState["${courtNum}_t2_p1"]
    val t2p2 = slotState["${courtNum}_t2_p2"]

    val team1Skill = (t1p1?.skill ?: 0) + (t1p2?.skill ?: 0)
    val team2Skill = (t2p1?.skill ?: 0) + (t2p2?.skill ?: 0)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Court $courtNum", fontWeight = FontWeight.Bold)
                if (team1Skill > 0 || team2Skill > 0) {
                    Text(
                        "Team Skill: ★$team1Skill vs ★$team2Skill",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(modifier = Modifier.weight(1f)) {
                    PlayerSlot("${courtNum}_t1_p1", t1p1, { p -> onSwap(p, "${courtNum}_t1_p1") }, onPlayerClick)
                    PlayerSlot("${courtNum}_t1_p2", t1p2, { p -> onSwap(p, "${courtNum}_t1_p2") }, onPlayerClick)
                }

                Text("VS", modifier = Modifier.padding(horizontal = 16.dp).align(Alignment.CenterVertically))

                Column(modifier = Modifier.weight(1f)) {
                    PlayerSlot("${courtNum}_t2_p1", t2p1, { p -> onSwap(p, "${courtNum}_t2_p1") }, onPlayerClick)
                    PlayerSlot("${courtNum}_t2_p2", t2p2, { p -> onSwap(p, "${courtNum}_t2_p2") }, onPlayerClick)
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
                    Text(
                        "Court ${match.courtNumber} (★${match.team1.totalSkill} vs ★${match.team2.totalSkill})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            match.team1.players.forEach { player ->
                                Text("${player.name} (★${player.skill})", maxLines = 1)
                            }
                        }
                        Text("vs", modifier = Modifier.padding(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            match.team2.players.forEach { player ->
                                Text("${player.name} (★${player.skill})", maxLines = 1)
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

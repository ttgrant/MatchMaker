package org.grant.matchmaker.domain

data class Player(
    val id: String,
    val name: String,
    val skill: Int = 1
) {
    init {
        require(skill in 1..5) { "Player skill must be between 1 and 5, got $skill" }
    }
}

data class Team(val player1: Player, val player2: Player? = null) {
    val players get() = listOfNotNull(player1, player2)
    val isDoubles get() = player2 != null
    val totalSkill get() = players.sumOf { it.skill }
}

data class Match(val team1: Team, val team2: Team, val courtNumber: Int) {
    val players get() = team1.players + team2.players
}

data class Round(
    val matches: List<Match>,
    val sittingOut: List<Player>
)

data class SessionState(
    val players: List<Player> = emptyList(),
    val courts: Int = 1,
    val history: List<Round> = emptyList(),
    val pausedPlayers: Set<String> = emptySet(),
    val playCountOffsets: Map<String, Int> = emptyMap(),
    val activeSlots: Map<String, String> = emptyMap(),
    val isInMatchMode: Boolean = false
) {
    fun playCounts(): Map<Player, Int> {
        val counts = players.associateWith { 0 }.toMutableMap()
        for (round in history) {
            for (match in round.matches) {
                for (p in match.players) {
                    val current = counts.entries.firstOrNull { it.key.id == p.id }?.key ?: p
                    counts[current] = (counts[current] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun effectivePlayCounts(): Map<Player, Int> {
        val actual = playCounts()
        return players.associateWith { player ->
            (actual[player] ?: 0) + (playCountOffsets[player.id] ?: 0)
        }
    }

    fun minActiveEffectivePlayCount(): Int {
        val activePlayers = players.filter { it.id !in pausedPlayers }
        if (activePlayers.isEmpty()) return 0
        val effective = effectivePlayCounts()
        return activePlayers.minOfOrNull { effective[it] ?: 0 } ?: 0
    }

    private fun makePair(p1: Player, p2: Player): Pair<String, String> {
        return if (p1.id < p2.id) Pair(p1.id, p2.id) else Pair(p2.id, p1.id)
    }

    fun partnerCountsById(): Map<Pair<String, String>, Int> {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        for (round in history) {
            for (match in round.matches) {
                if (match.team1.isDoubles) {
                    val pair = makePair(match.team1.player1, match.team1.player2!!)
                    counts[pair] = (counts[pair] ?: 0) + 1
                }
                if (match.team2.isDoubles) {
                    val pair = makePair(match.team2.player1, match.team2.player2!!)
                    counts[pair] = (counts[pair] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun opponentCountsById(): Map<Pair<String, String>, Int> {
        val counts = mutableMapOf<Pair<String, String>, Int>()
        for (round in history) {
            for (match in round.matches) {
                for (p1 in match.team1.players) {
                    for (p2 in match.team2.players) {
                        val pair = makePair(p1, p2)
                        counts[pair] = (counts[pair] ?: 0) + 1
                    }
                }
            }
        }
        return counts
    }

    fun partnerCounts(): Map<Pair<Player, Player>, Int> {
        val byId = partnerCountsById()
        val playerById = players.associateBy { it.id }
        val result = mutableMapOf<Pair<Player, Player>, Int>()
        for ((idPair, count) in byId) {
            val p1 = playerById[idPair.first]
            val p2 = playerById[idPair.second]
            if (p1 != null && p2 != null) {
                result[Pair(p1, p2)] = count
            }
        }
        return result
    }

    fun opponentCounts(): Map<Pair<Player, Player>, Int> {
        val byId = opponentCountsById()
        val playerById = players.associateBy { it.id }
        val result = mutableMapOf<Pair<Player, Player>, Int>()
        for ((idPair, count) in byId) {
            val p1 = playerById[idPair.first]
            val p2 = playerById[idPair.second]
            if (p1 != null && p2 != null) {
                result[Pair(p1, p2)] = count
            }
        }
        return result
    }

    fun timesPlayed(player: Player): Int {
        return history.count { round -> round.matches.any { match -> match.players.any { it.id == player.id } } }
    }

    fun timesSatOut(player: Player): Int {
        return history.count { round -> round.sittingOut.any { it.id == player.id } }
    }

    fun interactionsFor(player: Player): Map<Player, Pair<Int, Int>> {
        val partnerCounts = mutableMapOf<String, Int>()
        val opponentCounts = mutableMapOf<String, Int>()

        for (round in history) {
            for (match in round.matches) {
                if (match.players.any { it.id == player.id }) {
                    val isOnTeam1 = match.team1.players.any { it.id == player.id }
                    val ownTeam = if (isOnTeam1) match.team1 else match.team2
                    val opposingTeam = if (isOnTeam1) match.team2 else match.team1

                    ownTeam.players.filter { it.id != player.id }.forEach { partner ->
                        partnerCounts[partner.id] = (partnerCounts[partner.id] ?: 0) + 1
                    }

                    opposingTeam.players.forEach { opponent ->
                        opponentCounts[opponent.id] = (opponentCounts[opponent.id] ?: 0) + 1
                    }
                }
            }
        }

        return players.filter { it.id != player.id }.associateWith { p ->
            Pair(partnerCounts[p.id] ?: 0, opponentCounts[p.id] ?: 0)
        }
    }

    fun addPlayerMidSession(name: String, skill: Int, id: String): SessionState {
        val offset = if (history.isNotEmpty()) minActiveEffectivePlayCount() else 0
        val newPlayer = Player(id = id, name = name.trim(), skill = skill.coerceIn(1, 5))
        val updatedOffsets = if (offset > 0) playCountOffsets + (newPlayer.id to offset) else playCountOffsets
        return copy(
            players = players + newPlayer,
            playCountOffsets = updatedOffsets
        )
    }

    fun updatePlayerSkill(playerId: String, newSkill: Int): SessionState {
        val coerced = newSkill.coerceIn(1, 5)
        val updatedPlayers = players.map { if (it.id == playerId) it.copy(skill = coerced) else it }
        val updatedHistory = history.map { round ->
            Round(
                matches = round.matches.map { match ->
                    Match(
                        team1 = Team(
                            player1 = if (match.team1.player1.id == playerId) match.team1.player1.copy(skill = coerced) else match.team1.player1,
                            player2 = match.team1.player2?.let { if (it.id == playerId) it.copy(skill = coerced) else it }
                        ),
                        team2 = Team(
                            player1 = if (match.team2.player1.id == playerId) match.team2.player1.copy(skill = coerced) else match.team2.player1,
                            player2 = match.team2.player2?.let { if (it.id == playerId) it.copy(skill = coerced) else it }
                        ),
                        courtNumber = match.courtNumber
                    )
                },
                sittingOut = round.sittingOut.map { if (it.id == playerId) it.copy(skill = coerced) else it }
            )
        }
        return copy(players = updatedPlayers, history = updatedHistory)
    }
}

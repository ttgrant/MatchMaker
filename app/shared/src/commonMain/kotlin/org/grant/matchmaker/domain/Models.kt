package org.grant.matchmaker.domain

data class Player(val id: String, val name: String)

data class Team(val player1: Player, val player2: Player? = null) {
    val players get() = listOfNotNull(player1, player2)
    val isDoubles get() = player2 != null
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
    val pausedPlayers: Set<String> = emptySet()
) {
    fun playCounts(): Map<Player, Int> {
        val counts = players.associateWith { 0 }.toMutableMap()
        for (round in history) {
            for (match in round.matches) {
                for (p in match.players) {
                    counts[p] = (counts[p] ?: 0) + 1
                }
            }
        }
        return counts
    }
    
    private fun makePair(p1: Player, p2: Player): Pair<Player, Player> {
        return if (p1.id < p2.id) Pair(p1, p2) else Pair(p2, p1)
    }

    fun partnerCounts(): Map<Pair<Player, Player>, Int> {
        val counts = mutableMapOf<Pair<Player, Player>, Int>()
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

    fun opponentCounts(): Map<Pair<Player, Player>, Int> {
        val counts = mutableMapOf<Pair<Player, Player>, Int>()
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
    
    fun timesPlayed(player: Player): Int {
        return history.count { round -> round.matches.any { match -> match.players.contains(player) } }
    }
    
    fun timesSatOut(player: Player): Int {
        return history.count { round -> round.sittingOut.contains(player) }
    }
    
    fun interactionsFor(player: Player): Map<Player, Pair<Int, Int>> {
        val partnerCounts = mutableMapOf<Player, Int>()
        val opponentCounts = mutableMapOf<Player, Int>()
        
        for (round in history) {
            for (match in round.matches) {
                if (match.players.contains(player)) {
                    val isOnTeam1 = match.team1.players.contains(player)
                    val ownTeam = if (isOnTeam1) match.team1 else match.team2
                    val opposingTeam = if (isOnTeam1) match.team2 else match.team1
                    
                    ownTeam.players.filter { it != player }.forEach { partner ->
                        partnerCounts[partner] = (partnerCounts[partner] ?: 0) + 1
                    }
                    
                    opposingTeam.players.forEach { opponent ->
                        opponentCounts[opponent] = (opponentCounts[opponent] ?: 0) + 1
                    }
                }
            }
        }
        
        return players.filter { it != player }.associateWith { p ->
            Pair(partnerCounts[p] ?: 0, opponentCounts[p] ?: 0)
        }
    }
}

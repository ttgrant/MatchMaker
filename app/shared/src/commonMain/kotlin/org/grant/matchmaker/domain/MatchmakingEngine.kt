package org.grant.matchmaker.domain

import kotlin.random.Random

object MatchmakingEngine {
    
    private fun makePair(p1: Player, p2: Player): Pair<Player, Player> {
        return if (p1.id < p2.id) Pair(p1, p2) else Pair(p2, p1)
    }

    private fun calculateScore(
        playing: List<Player>, 
        numCourts: Int, 
        partnerCounts: Map<Pair<Player, Player>, Int>,
        opponentCounts: Map<Pair<Player, Player>, Int>
    ): Int {
        var score = 0
        var courtIdx = 1
        var pIdx = 0
        
        // Doubles matches
        while (playing.size - pIdx >= 4 && courtIdx <= numCourts) {
            val p1 = playing[pIdx]
            val p2 = playing[pIdx+1]
            val p3 = playing[pIdx+2]
            val p4 = playing[pIdx+3]
            
            val t1Pair = makePair(p1, p2)
            val t2Pair = makePair(p3, p4)
            
            // Heavy penalty for repeating partners
            score += (partnerCounts[t1Pair] ?: 0) * (partnerCounts[t1Pair] ?: 0) * 10
            score += (partnerCounts[t2Pair] ?: 0) * (partnerCounts[t2Pair] ?: 0) * 10
            
            val opp1 = makePair(p1, p3)
            val opp2 = makePair(p1, p4)
            val opp3 = makePair(p2, p3)
            val opp4 = makePair(p2, p4)
            
            // Moderate penalty for repeating opponents
            score += (opponentCounts[opp1] ?: 0) * (opponentCounts[opp1] ?: 0) * 5
            score += (opponentCounts[opp2] ?: 0) * (opponentCounts[opp2] ?: 0) * 5
            score += (opponentCounts[opp3] ?: 0) * (opponentCounts[opp3] ?: 0) * 5
            score += (opponentCounts[opp4] ?: 0) * (opponentCounts[opp4] ?: 0) * 5
            
            pIdx += 4
            courtIdx++
        }
        
        // Singles matches
        while (playing.size - pIdx >= 2 && courtIdx <= numCourts) {
            val p1 = playing[pIdx]
            val p2 = playing[pIdx+1]
            val opp = makePair(p1, p2)
            
            score += (opponentCounts[opp] ?: 0) * (opponentCounts[opp] ?: 0) * 5
            
            pIdx += 2
            courtIdx++
        }
        
        return score
    }

    fun generateNextRound(session: SessionState): Round {
        val activePlayers = session.players.filter { it.id !in session.pausedPlayers }
        val permanentlySittingOut = session.players.filter { it.id in session.pausedPlayers }

        if (activePlayers.isEmpty() || session.courts <= 0) {
            return Round(emptyList(), session.players)
        }

        // We want to minimize play count differences.
        val playCounts = session.playCounts()
        
        // Sort players by play count (ascending), shuffle first to randomly break ties
        val sortedPlayers = activePlayers.shuffled().sortedBy { playCounts[it] ?: 0 }
        
        val maxPlayersNeeded = session.courts * 4
        val actualPlayersToPlayCount = minOf(sortedPlayers.size, maxPlayersNeeded)
        
        // These are the players selected to play this round
        val selectedToPlay = sortedPlayers.take(actualPlayersToPlayCount)
        val sittingOut = sortedPlayers.drop(actualPlayersToPlayCount).toMutableList()
        sittingOut.addAll(permanentlySittingOut)
        
        val partnerCounts = session.partnerCounts()
        val opponentCounts = session.opponentCounts()
        
        // Hill-climbing algorithm to find the best configuration
        var bestConfig = selectedToPlay.toMutableList()
        var bestScore = calculateScore(bestConfig, session.courts, partnerCounts, opponentCounts)
        
        var currentConfig = bestConfig.toMutableList()
        var currentScore = bestScore
        
        // 500 iterations is very fast in Kotlin Wasm and sufficient for small N
        val iterations = 500
        for (i in 0 until iterations) {
            if (currentConfig.size < 2) break
            
            // Pick two random indices to swap
            val idx1 = Random.nextInt(currentConfig.size)
            val idx2 = Random.nextInt(currentConfig.size)
            
            if (idx1 != idx2) {
                // Swap
                val temp = currentConfig[idx1]
                currentConfig[idx1] = currentConfig[idx2]
                currentConfig[idx2] = temp
                
                val newScore = calculateScore(currentConfig, session.courts, partnerCounts, opponentCounts)
                
                if (newScore <= currentScore) {
                    // Keep the swap (accept equal to allow walking flat regions)
                    currentScore = newScore
                    if (newScore < bestScore) {
                        bestScore = newScore
                        bestConfig = currentConfig.toMutableList()
                    }
                } else {
                    // Revert the swap
                    val revertTemp = currentConfig[idx1]
                    currentConfig[idx1] = currentConfig[idx2]
                    currentConfig[idx2] = revertTemp
                }
            }
        }
        
        // Construct the actual matches from the best configuration
        val matches = mutableListOf<Match>()
        var courtIdx = 1
        var pIdx = 0
        
        while (bestConfig.size - pIdx >= 4 && courtIdx <= session.courts) {
            val t1 = Team(bestConfig[pIdx], bestConfig[pIdx+1])
            val t2 = Team(bestConfig[pIdx+2], bestConfig[pIdx+3])
            matches.add(Match(t1, t2, courtIdx))
            pIdx += 4
            courtIdx++
        }
        
        while (bestConfig.size - pIdx >= 2 && courtIdx <= session.courts) {
            val t1 = Team(bestConfig[pIdx])
            val t2 = Team(bestConfig[pIdx+1])
            matches.add(Match(t1, t2, courtIdx))
            pIdx += 2
            courtIdx++
        }
        
        // Anyone left over (e.g. 1 player if total was odd and we filled singles) sits out
        while (pIdx < bestConfig.size) {
            sittingOut.add(bestConfig[pIdx])
            pIdx++
        }
        
        return Round(matches, sittingOut)
    }
}

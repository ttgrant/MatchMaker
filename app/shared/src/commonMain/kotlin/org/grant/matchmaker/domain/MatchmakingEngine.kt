package org.grant.matchmaker.domain

import kotlin.random.Random

object MatchmakingEngine {
    
    private fun makePair(p1: Player, p2: Player): Pair<Player, Player> {
        return if (p1.id < p2.id) Pair(p1, p2) else Pair(p2, p1)
    }

    private fun calculateScore(
        config: List<Player>, 
        numCourts: Int, 
        partnerCounts: Map<Pair<Player, Player>, Int>,
        opponentCounts: Map<Pair<Player, Player>, Int>,
        playCounts: Map<Player, Int>
    ): Long {
        var score = 0L
        var courtIdx = 1
        var pIdx = 0
        
        val PLAY_COUNT_WEIGHT = 1_000_000L
        val PARTNER_WEIGHT = 10L
        val OPPONENT_WEIGHT = 5L
        
        // Doubles matches
        while (config.size - pIdx >= 4 && courtIdx <= numCourts) {
            val p1 = config[pIdx]
            val p2 = config[pIdx+1]
            val p3 = config[pIdx+2]
            val p4 = config[pIdx+3]
            
            val pc1 = (playCounts[p1] ?: 0).toLong()
            val pc2 = (playCounts[p2] ?: 0).toLong()
            val pc3 = (playCounts[p3] ?: 0).toLong()
            val pc4 = (playCounts[p4] ?: 0).toLong()
            
            score += pc1 * pc1 * PLAY_COUNT_WEIGHT
            score += pc2 * pc2 * PLAY_COUNT_WEIGHT
            score += pc3 * pc3 * PLAY_COUNT_WEIGHT
            score += pc4 * pc4 * PLAY_COUNT_WEIGHT
            
            val t1Pair = makePair(p1, p2)
            val t2Pair = makePair(p3, p4)
            
            val part1 = (partnerCounts[t1Pair] ?: 0).toLong()
            val part2 = (partnerCounts[t2Pair] ?: 0).toLong()
            
            score += part1 * part1 * PARTNER_WEIGHT
            score += part2 * part2 * PARTNER_WEIGHT
            
            val opp1 = makePair(p1, p3)
            val opp2 = makePair(p1, p4)
            val opp3 = makePair(p2, p3)
            val opp4 = makePair(p2, p4)
            
            val oc1 = (opponentCounts[opp1] ?: 0).toLong()
            val oc2 = (opponentCounts[opp2] ?: 0).toLong()
            val oc3 = (opponentCounts[opp3] ?: 0).toLong()
            val oc4 = (opponentCounts[opp4] ?: 0).toLong()
            
            score += oc1 * oc1 * OPPONENT_WEIGHT
            score += oc2 * oc2 * OPPONENT_WEIGHT
            score += oc3 * oc3 * OPPONENT_WEIGHT
            score += oc4 * oc4 * OPPONENT_WEIGHT
            
            pIdx += 4
            courtIdx++
        }
        
        // Singles matches
        while (config.size - pIdx >= 2 && courtIdx <= numCourts) {
            val p1 = config[pIdx]
            val p2 = config[pIdx+1]
            
            val pc1 = (playCounts[p1] ?: 0).toLong()
            val pc2 = (playCounts[p2] ?: 0).toLong()
            
            score += pc1 * pc1 * PLAY_COUNT_WEIGHT
            score += pc2 * pc2 * PLAY_COUNT_WEIGHT
            
            val opp = makePair(p1, p2)
            val oc = (opponentCounts[opp] ?: 0).toLong()
            
            score += oc * oc * OPPONENT_WEIGHT
            
            pIdx += 2
            courtIdx++
        }
        
        return score
    }

    fun generateNextRound(session: SessionState): Round {
        val activePlayers = session.players.filter { it.id !in session.pausedPlayers }
        val permanentlySittingOut = session.players.filter { it.id in session.pausedPlayers }.toMutableList()

        if (activePlayers.isEmpty() || session.courts <= 0) {
            return Round(emptyList(), session.players)
        }

        var playersPlaying = 0
        var c = 1
        while (activePlayers.size - playersPlaying >= 4 && c <= session.courts) { playersPlaying += 4; c++ }
        while (activePlayers.size - playersPlaying >= 2 && c <= session.courts) { playersPlaying += 2; c++ }
        
        if (playersPlaying == 0) {
            return Round(emptyList(), session.players)
        }

        val playCounts = session.playCounts()
        val partnerCounts = session.partnerCounts()
        val opponentCounts = session.opponentCounts()
        
        // Multi-start hill climbing algorithm to find the best configuration
        var bestConfig = activePlayers.toMutableList()
        var bestScore = calculateScore(bestConfig, session.courts, partnerCounts, opponentCounts, playCounts)
        
        // Dynamically scale iterations based on the number of players to ensure we search enough of the problem space
        val numRestarts = maxOf(100, activePlayers.size * 5)
        val iterationsPerRestart = maxOf(5000, activePlayers.size * 200)
        
        for (r in 0 until numRestarts) {
            val currentConfig = activePlayers.shuffled().toMutableList()
            var currentScore = calculateScore(currentConfig, session.courts, partnerCounts, opponentCounts, playCounts)
            
            if (currentScore < bestScore) {
                bestScore = currentScore
                bestConfig = currentConfig.toMutableList()
            }
            
            for (i in 0 until iterationsPerRestart) {
                if (currentConfig.size < 2) break
                
                // Pick idx1 from those who are playing, idx2 from anywhere
                val idx1 = Random.nextInt(playersPlaying)
                val idx2 = Random.nextInt(currentConfig.size)
                
                if (idx1 != idx2) {
                    // Swap
                    val temp = currentConfig[idx1]
                    currentConfig[idx1] = currentConfig[idx2]
                    currentConfig[idx2] = temp
                    
                    val newScore = calculateScore(currentConfig, session.courts, partnerCounts, opponentCounts, playCounts)
                    
                    if (newScore <= currentScore) {
                        // Keep the swap
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
        
        val sittingOut = permanentlySittingOut
        while (pIdx < bestConfig.size) {
            sittingOut.add(bestConfig[pIdx])
            pIdx++
        }
        
        return Round(matches, sittingOut)
    }
}

package org.grant.matchmaker.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatchmakingTest {

    @Test
    fun equalCourtTimeInvariantAcrossRoundsAndLateArrivals() {
        val initialPlayers = (1..6).map { i ->
            Player(id = "p$i", name = "Player $i", skill = (i % 5) + 1)
        }
        var state = SessionState(players = initialPlayers, courts = 1)

        // Simulate 4 rounds with 6 active players on 1 court (4 play, 2 sit out each round)
        repeat(4) {
            val round = MatchmakingEngine.generateNextRound(state)
            state = state.copy(history = state.history + round)

            val effective = state.effectivePlayCounts()
            val maxPlayed = effective.values.maxOrNull() ?: 0
            val minPlayed = effective.values.minOrNull() ?: 0
            assertTrue(
                maxPlayed - minPlayed <= 1,
                "Effective play count spread exceeded 1: max=$maxPlayed, min=$minPlayed"
            )
        }

        // Now add a late arrival mid-session after 4 rounds
        state = state.addPlayerMidSession(name = "Late Arrival", skill = 3, id = "p_late")
        val latePlayer = state.players.first { it.id == "p_late" }

        // Actual games played must still be 0, while offset matches the min active play count
        assertEquals(0, state.timesPlayed(latePlayer))
        assertTrue((state.playCountOffsets["p_late"] ?: 0) >= 2)

        // Run 4 more rounds and verify the late arrival rotates fairly without monopolizing the court
        repeat(4) {
            val round = MatchmakingEngine.generateNextRound(state)
            state = state.copy(history = state.history + round)

            val effective = state.effectivePlayCounts()
            val maxPlayed = effective.values.maxOrNull() ?: 0
            val minPlayed = effective.values.minOrNull() ?: 0
            assertTrue(
                maxPlayed - minPlayed <= 1,
                "Effective play count spread after late arrival exceeded 1: max=$maxPlayed, min=$minPlayed"
            )
        }
    }

    @Test
    fun pausedPlayerIsolationInvariant() {
        val players = (1..6).map { i -> Player(id = "p$i", name = "Player $i", skill = 3) }
        val pausedIds = setOf("p2", "p5")
        val state = SessionState(
            players = players,
            courts = 1,
            pausedPlayers = pausedIds
        )

        repeat(5) {
            val round = MatchmakingEngine.generateNextRound(state)
            val playingIds = round.matches.flatMap { it.players }.map { it.id }.toSet()
            val sittingIds = round.sittingOut.map { it.id }.toSet()

            for (pausedId in pausedIds) {
                assertFalse(pausedId in playingIds, "Paused player $pausedId was scheduled into a match")
                assertTrue(pausedId in sittingIds, "Paused player $pausedId was missing from sittingOut")
            }
        }
    }

    @Test
    fun antiStackingSkillInvariant() {
        val players = listOf(
            Player(id = "high1", name = "Pro 1", skill = 5),
            Player(id = "high2", name = "Pro 2", skill = 5),
            Player(id = "low1", name = "Rookie 1", skill = 1),
            Player(id = "low2", name = "Rookie 2", skill = 1)
        )
        val state = SessionState(players = players, courts = 1)

        val round = MatchmakingEngine.generateNextRound(state)
        assertEquals(1, round.matches.size)
        val match = round.matches.first()

        // Instead of stacking (5, 5) vs (1, 1) -> 10 vs 2, teams must be balanced (5, 1) vs (5, 1) -> 6 vs 6
        assertEquals(6, match.team1.totalSkill, "Team 1 should have combined skill 6")
        assertEquals(6, match.team2.totalSkill, "Team 2 should have combined skill 6")
    }

    @Test
    fun equalWeightingForPartnerAndOpponentVariety() {
        assertEquals(
            MatchmakingEngine.PARTNER_WEIGHT,
            MatchmakingEngine.OPPONENT_WEIGHT,
            "Partner and Opponent weights must be equal"
        )
    }

    @Test
    fun persistenceRoundTripInvariant() {
        val p1 = Player(id = "id-1", name = "Alice: The Great, Jr.", skill = 5)
        val p2 = Player(id = "id-2", name = "Bob | Builder ~ 100%", skill = 2)
        val p3 = Player(id = "id-3", name = "Charlie", skill = 4)
        val p4 = Player(id = "id-4", name = "Dana", skill = 1)
        val p5 = Player(id = "id-5", name = "Eve (Late)", skill = 3)

        val round1 = Round(
            matches = listOf(
                Match(
                    team1 = Team(p1, p4),
                    team2 = Team(p2, p3),
                    courtNumber = 1
                )
            ),
            sittingOut = listOf(p5)
        )

        val original = SessionState(
            players = listOf(p1, p2, p3, p4, p5),
            courts = 2,
            history = listOf(round1),
            pausedPlayers = setOf("id-5"),
            playCountOffsets = mapOf("id-5" to 2),
            activeSlots = mapOf("1_t1_p1" to "id-1", "1_t2_p1" to "id-2"),
            isInMatchMode = true
        )

        val encoded = SessionSerializer.serialize(original)
        val restored = SessionSerializer.deserialize(encoded)

        assertNotNull(restored)
        assertEquals(original, restored)
    }
}

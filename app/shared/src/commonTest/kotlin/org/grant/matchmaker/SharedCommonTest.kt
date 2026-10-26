package org.grant.matchmaker

import org.grant.matchmaker.domain.*
import org.grant.matchmaker.storage.SessionStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SharedCommonTest {

    @Test
    fun sessionStorageRoundTripTest() {
        val p1 = Player(id = "1", name = "Player One", skill = 4)
        val p2 = Player(id = "2", name = "Player Two", skill = 2)
        val state = SessionState(
            players = listOf(p1, p2),
            courts = 1,
            activeSlots = mapOf("1_t1_p1" to "1", "1_t2_p1" to "2"),
            isInMatchMode = true
        )

        SessionStorage.save(SessionSerializer.serialize(state))
        val loadedRaw = SessionStorage.load()
        assertNotNull(loadedRaw)
        val decoded = SessionSerializer.deserialize(loadedRaw)
        assertEquals(state, decoded)
        SessionStorage.clear()
    }
}

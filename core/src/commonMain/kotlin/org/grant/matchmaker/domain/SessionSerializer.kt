package org.grant.matchmaker.domain

/**
 * Lossless, dependency-free multiplatform serializer for [SessionState].
 * Uses percent-encoding for arbitrary player names/IDs so special characters,
 * whitespace, and Unicode round-trip safely on all KMP targets.
 */
object SessionSerializer {

    private fun escape(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when {
                ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' -> sb.append(ch)
                else -> {
                    val code = ch.code
                    sb.append('%')
                    sb.append(code.toString(16).padStart(4, '0'))
                }
            }
        }
        return sb.toString()
    }

    private fun unescape(encoded: String): String {
        val sb = StringBuilder(encoded.length)
        var i = 0
        while (i < encoded.length) {
            val ch = encoded[i]
            if (ch == '%' && i + 4 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 5)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 5
                    continue
                }
            }
            sb.append(ch)
            i++
        }
        return sb.toString()
    }

    private fun encodePlayer(player: Player): String =
        "${escape(player.id)}:${escape(player.name)}:${player.skill}"

    private fun decodePlayer(raw: String): Player {
        val parts = raw.split(':')
        require(parts.size >= 2) { "Invalid player record: $raw" }
        val id = unescape(parts[0])
        val name = unescape(parts[1])
        val skill = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 5) ?: 1
        return Player(id = id, name = name, skill = skill)
    }

    private fun encodeTeam(team: Team): String {
        val p1 = encodePlayer(team.player1)
        val p2 = team.player2?.let { encodePlayer(it) } ?: ""
        return "$p1~$p2"
    }

    private fun decodeTeam(raw: String): Team {
        val parts = raw.split('~')
        val p1 = decodePlayer(parts[0])
        val p2 = if (parts.size > 1 && parts[1].isNotEmpty()) decodePlayer(parts[1]) else null
        return Team(p1, p2)
    }

    fun serialize(state: SessionState): String {
        val lines = mutableListOf<String>()
        lines.add("V1")
        lines.add("COURTS=${state.courts}")
        lines.add("MATCH_MODE=${state.isInMatchMode}")
        lines.add("PLAYERS=${state.players.joinToString(",") { encodePlayer(it) }}")
        lines.add("PAUSED=${state.pausedPlayers.joinToString(",") { escape(it) }}")
        lines.add(
            "OFFSETS=${
                state.playCountOffsets.entries.joinToString(",") { (k, v) -> "${escape(k)}:$v" }
            }"
        )
        lines.add(
            "SLOTS=${
                state.activeSlots.entries.joinToString(",") { (k, v) -> "${escape(k)}:${escape(v)}" }
            }"
        )
        for (round in state.history) {
            val matchesStr = round.matches.joinToString(";") { match ->
                "${match.courtNumber}@${encodeTeam(match.team1)}vs${encodeTeam(match.team2)}"
            }
            val sittingStr = round.sittingOut.joinToString(",") { encodePlayer(it) }
            lines.add("ROUND=$matchesStr|$sittingStr")
        }
        return lines.joinToString("\n")
    }

    fun deserialize(data: String): SessionState? {
        if (data.isBlank()) return null
        return try {
            val lines = data.lines().filter { it.isNotBlank() }
            if (lines.isEmpty() || lines.first() != "V1") return null

            var courts = 1
            var isInMatchMode = false
            var players = emptyList<Player>()
            var pausedPlayers = emptySet<String>()
            var playCountOffsets = emptyMap<String, Int>()
            var activeSlots = emptyMap<String, String>()
            val history = mutableListOf<Round>()

            for (line in lines.drop(1)) {
                val eqIdx = line.indexOf('=')
                if (eqIdx < 0) continue
                val key = line.substring(0, eqIdx)
                val value = line.substring(eqIdx + 1)

                when (key) {
                    "COURTS" -> courts = value.toIntOrNull() ?: 1
                    "MATCH_MODE" -> isInMatchMode = value.toBooleanStrictOrNull() ?: false
                    "PLAYERS" -> {
                        players = if (value.isEmpty()) emptyList()
                        else value.split(',').map { decodePlayer(it) }
                    }
                    "PAUSED" -> {
                        pausedPlayers = if (value.isEmpty()) emptySet()
                        else value.split(',').map { unescape(it) }.toSet()
                    }
                    "OFFSETS" -> {
                        playCountOffsets = if (value.isEmpty()) emptyMap()
                        else value.split(',').mapNotNull { entry ->
                            val parts = entry.split(':')
                            if (parts.size == 2) {
                                val k = unescape(parts[0])
                                val v = parts[1].toIntOrNull()
                                if (v != null) k to v else null
                            } else null
                        }.toMap()
                    }
                    "SLOTS" -> {
                        activeSlots = if (value.isEmpty()) emptyMap()
                        else value.split(',').mapNotNull { entry ->
                            val parts = entry.split(':')
                            if (parts.size == 2) unescape(parts[0]) to unescape(parts[1]) else null
                        }.toMap()
                    }
                    "ROUND" -> {
                        val pipeIdx = value.indexOf('|')
                        val matchesPart = if (pipeIdx >= 0) value.substring(0, pipeIdx) else value
                        val sittingPart = if (pipeIdx >= 0) value.substring(pipeIdx + 1) else ""

                        val matches = if (matchesPart.isEmpty()) emptyList()
                        else matchesPart.split(';').map { matchRaw ->
                            val atIdx = matchRaw.indexOf('@')
                            val courtNum = matchRaw.substring(0, atIdx).toInt()
                            val teamsRaw = matchRaw.substring(atIdx + 1).split("vs")
                            val team1 = decodeTeam(teamsRaw[0])
                            val team2 = decodeTeam(teamsRaw[1])
                            Match(team1, team2, courtNum)
                        }

                        val sittingOut = if (sittingPart.isEmpty()) emptyList()
                        else sittingPart.split(',').map { decodePlayer(it) }

                        history.add(Round(matches, sittingOut))
                    }
                }
            }

            SessionState(
                players = players,
                courts = courts,
                history = history,
                pausedPlayers = pausedPlayers,
                playCountOffsets = playCountOffsets,
                activeSlots = activeSlots,
                isInMatchMode = isInMatchMode
            )
        } catch (_: Exception) {
            null
        }
    }
}

# MatchMaker — Product & Architecture Document

## 1. Product Overview

**MatchMaker** is a local-first, cross-platform court and player scheduling application built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**. It is designed for recreational and club sports sessions (such as badminton, pickleball, tennis, or padel) where a variable number of players of varying skill levels rotate across a fixed number of courts over multiple rounds.

### Core Value Proposition
Organizing fair round-robin or open-play sessions manually becomes difficult when player counts do not divide evenly into available courts, when skill levels vary, or when players arrive late / take breaks. MatchMaker solves this by:
1. **Maximizing Play Time Fairness**: Ensuring no player sits out disproportionately more than others, including late arrivals who join mid-session.
2. **Preventing Stacked Matches**: Using player skill ratings (`1–5`) to balance combined team skill levels on each court (avoiding `5+5` vs `1+1` blowouts) while still allowing small skill differences so everyone gets to play with everyone.
3. **Maximizing Social Variety**: Weighting repeat partnerships and repeat opponent matchups equally (`10L` each in separate buckets) so players rotate through all teammates and opponents.
4. **Interactive Control & Session Recovery**: Combining one-click algorithmic court assignment with drag-and-drop manual adjustments, inline mid-session player additions, **Undo Last Round** slot restoration, and automatic cross-platform persistence.
5. **Real-Time Analytics**: Tracking individual play/sit-out counts, skill ratings, and partner/opponent interaction distributions.

---

## 2. System Architecture

MatchMaker is structured as a multi-module Gradle project leveraging Kotlin Multiplatform (`2.4.0`) and Compose Multiplatform (`1.11.1`) across Android, iOS, Web (WasmJS & JS), and a JVM Ktor backend.

```mermaid
flowchart TD
    subgraph Clients["Client Entry Points"]
        Android["Android App\n(:app:androidApp)"]
        iOS["iOS App\n(Xcode / SwiftUI)"]
        Web["Web App - WasmJS & JS\n(:app:webApp)"]
    end

    subgraph SharedUI["Shared Application Layer (:app:shared)"]
        Entry["App() Composable"]
        Screens["MatchMakerScreen / PlayerDetailScreen"]
        DnD["DragAndDrop System"]
        Storage["SessionStorage (expect/actual)"]
    end

    subgraph CoreMod["Shared Domain & Engine (:core)"]
        Models["Domain Models (Models.kt)"]
        Engine["MatchmakingEngine.kt"]
        Serializer["SessionSerializer.kt"]
    end

    subgraph Backend["Backend Server (:server - Deferred)"]
        Ktor["Ktor Netty Server (JVM 21)"]
    end

    Android --> Entry
    iOS --> Entry
    Web --> Entry
    Entry --> Screens
    Screens --> DnD
    Screens --> Storage
    Screens --> CoreMod
    Backend --> CoreMod
```

### Module Breakdown

| Module | Path | Targets | Responsibility |
| :--- | :--- | :--- | :--- |
| **`:core`** | `core/build.gradle.kts` | Android, iOS, JVM (`21`), JS, WasmJS | Houses all core domain models (`Models.kt`), the optimization engine (`MatchmakingEngine.kt`), lossless state serialization (`SessionSerializer.kt`), and the core invariant test suite (`MatchmakingTest.kt`). |
| **`:app:shared`** | `app/shared/build.gradle.kts` | `androidLibrary`, `iosArm64`, `iosSimulatorArm64`, `js`, `wasmJs` | Houses the shared Compose Multiplatform UI (`App.kt`, `MatchMakerScreen.kt`), custom drag-and-drop system (`DragAndDrop.kt`), and platform-specific local persistence (`SessionStorage.kt`). |
| **`:app:androidApp`** | `app/androidApp/build.gradle.kts` | Android (`minSdk 24`, `compileSdk 36`) | Android application wrapper hosting `MainActivity`, initializing `SessionStorage` with `SharedPreferences` and rendering Compose edge-to-edge. |
| **`:app:iosApp`** | `app/iosApp/iosApp/ContentView.swift` | iOS (`arm64`, Simulator `arm64`) | SwiftUI host app embedding the static `Shared` framework via `MainViewController` (`ComposeUIViewController`). |
| **`:app:webApp`** | `app/webApp/build.gradle.kts` | `wasmJs`, `js` (Browser) | Browser executable mounting `App()` into the DOM via `ComposeViewport`. |
| **`:server`** | `server/build.gradle.kts` | JVM (`Toolchain 21`) | Ktor `3.5.0` Netty server (`Application.kt`) with direct access to `:core` for future multi-device session sync. |

---

## 3. Domain Model & Data Structures

All core domain entities reside in `core/src/commonMain/kotlin/org/grant/matchmaker/domain/Models.kt` as immutable Kotlin data classes, supporting unidirectional state flow and full persistence.

```mermaid
classDiagram
    class SessionState {
        +List~Player~ players
        +Int courts
        +List~Round~ history
        +Set~String~ pausedPlayers
        +Map~String, Int~ playCountOffsets
        +Map~String, String~ activeSlots
        +Boolean isInMatchMode
        +playCounts() Map~Player, Int~
        +effectivePlayCounts() Map~Player, Int~
        +minActiveEffectivePlayCount() Int
        +partnerCounts() Map~Pair, Int~
        +opponentCounts() Map~Pair, Int~
        +timesPlayed(Player) Int
        +timesSatOut(Player) Int
        +interactionsFor(Player) Map~Player, Pair~
        +addPlayerMidSession(String, Int, String) SessionState
        +updatePlayerSkill(String, Int) SessionState
    }

    class Round {
        +List~Match~ matches
        +List~Player~ sittingOut
    }

    class Match {
        +Team team1
        +Team team2
        +Int courtNumber
        +players List~Player~
    }

    class Team {
        +Player player1
        +Player? player2
        +players List~Player~
        +isDoubles Boolean
        +totalSkill Int
    }

    class Player {
        +String id
        +String name
        +Int skill
    }

    SessionState "1" *-- "*" Player : roster
    SessionState "1" *-- "*" Round : history
    Round "1" *-- "*" Match : matches
    Round "1" o-- "*" Player : sittingOut
    Match "1" *-- "2" Team : team1 / team2
    Team "1" o-- "1..2" Player : players
```

### Key Domain Capabilities
* **Player Skill Ratings (`1–5`)**: `Player` enforces a skill rating in `1..5` (defaulting to `1`), and `Team.totalSkill` computes the combined skill of a team (`player1.skill + (player2?.skill ?: 0)`).
* **Late-Arrival Virtual Play-Count Offsets**: When a player joins mid-session via `addPlayerMidSession`, their `playCountOffsets[player.id]` is seeded with `minActiveEffectivePlayCount()`. `MatchmakingEngine` uses `effectivePlayCounts()` (`actual + offset`) so late arrivals enter the normal rotation immediately without hogging courts, while `timesPlayed(player)` still reports their true actual games played.
* **Normalized ID-Based Pair Tracking**: `SessionState` normalizes player pairs by `Player.id` (`partnerCountsById`, `opponentCountsById`) so updating a player's skill rating mid-session preserves all historical partner and opponent counts seamlessly.
* **Lossless Persistence**: `SessionSerializer` encodes and decodes the entire `SessionState` (including `activeSlots`, `isInMatchMode`, `playCountOffsets`, `pausedPlayers`, and `history`), backed by `SessionStorage` (`SharedPreferences` on Android, `NSUserDefaults` on iOS, `localStorage` on Web WasmJS/JS).

---

## 4. Matchmaking Engine & Optimization Algorithm

The scheduling engine in `core/src/commonMain/kotlin/org/grant/matchmaker/domain/MatchmakingEngine.kt` uses a **Multi-Start Stochastic Hill-Climbing** algorithm to generate optimal court assignments for each round.

### Court Allocation Policy
Given $N$ active (unpaused) players and $C$ available courts:
1. **Doubles Priority**: Courts are first filled with 4-player doubles matches while $\text{remainingPlayers} \ge 4$ and $\text{court} \le C$.
2. **Singles Fallback**: Remaining open courts are filled with 2-player singles matches while $\text{remainingPlayers} \ge 2$ and $\text{court} \le C$.
3. **Sit-Outs**: Any remaining active players, along with all `pausedPlayers`, are placed in the round's `sittingOut` list.

### Cost / Penalty Objective Function
Each candidate permutation of active players is evaluated by `calculateScore`. Lower scores represent better, fairer schedules. The total penalty $S$ is structured into a strict **three-tier priority hierarchy**:

$$S = S_{\text{play\_time}} + S_{\text{variety}} + S_{\text{skill}}$$

1. **Tier 1 — Equal Court Time & Anti-Consecutive Sit-Out Protection (`PLAY_COUNT_WEIGHT = 10,000,000`, `CONSECUTIVE_SIT_OUT_WEIGHT = 500,000`, `TOTAL_SIT_OUT_WEIGHT = 10,000`)**:
   $$S_{\text{play\_time}} = \sum_{p \in P_{\text{playing}}} W_{\text{play}} \cdot c_{\text{eff}}(p)^2 + \sum_{b \in P_{\text{bench}}} \left( W_{\text{streak}} \cdot s_{\text{streak}}(b)^2 + W_{\text{sat}} \cdot c_{\text{sat}}(b) \right)$$
   * $c_{\text{eff}}(p) = c_{\text{play}}(p) + \text{offset}(p)$ ensures players with fewer games always take priority.
   * $s_{\text{streak}}(b)$ is the number of consecutive immediately preceding rounds player $b$ sat out (`consecutiveSitOutsById()`). Any player who sat out last round incurs a `500,000` penalty if left on the bench again, preventing back-to-back sit-outs whenever $N_{\text{active}} \le 2 \times \text{courtCapacity}$.

2. **Tier 2 — Partner & Opponent Variety (`PARTNER_WEIGHT = 200`, `OPPONENT_WEIGHT = 200`)**:
   $$S_{\text{variety}} = \sum_{(p_i, p_j) \in E_{\text{partner}}} 200 \cdot c_{\text{partner}}(p_i, p_j)^2 + \sum_{(p_i, p_j) \in E_{\text{opponent}}} 200 \cdot c_{\text{opponent}}(p_i, p_j)^2$$
   * Tracked in separate buckets with equal weight (`200L` each), ensuring that rotating through all teammates and opponents is the primary driver of court groupings.

3. **Tier 3 — Intra-Court Anti-Stacking Skill Balance (`SKILL_WEIGHT = 15`, `SKILL_TIEBREAKER_WEIGHT = 1`)**:
   $$S_{\text{skill}} = \sum_{m \in M} \left( 15 \cdot (\Delta_{\text{actual}}(m) - \Delta_{\min}(m))^2 + 1 \cdot \Delta_{\text{actual}}(m)^2 \right)$$
   * $\Delta_{\text{actual}}(m) = |\text{skill}(\text{team}_1) - \text{skill}(\text{team}_2)|$ is the team skill gap for the chosen split on court $m$, and $\Delta_{\min}(m)$ is the minimum possible team skill gap across all 3 ways to split those 4 players on court $m$.
   * By penalizing **excess avoidable imbalance** ($\Delta_{\text{actual}} - \Delta_{\min}$), any 4 players can be scheduled onto a court together so everyone plays with and against everyone, while guaranteeing that the 4 players on each court are split into balanced teams (e.g., `★5 + ★1` vs `★5 + ★1` instead of `★5 + ★5` vs `★1 + ★1`).

---

## 5. Features & User Flows

The UI is implemented in `app/shared/src/commonMain/kotlin/org/grant/matchmaker/ui/MatchMakerScreen.kt` and transitions cleanly between three primary views:

```mermaid
stateDiagram-v2
    [*] --> SetupMode : Fresh Session
    [*] --> MatchMode : Auto-Restore Active Match Session
    SetupMode --> MatchMode : Click "Start" / "Resume Matchmaking"
    MatchMode --> SetupMode : Click "Back to Setup"
    SetupMode --> PlayerDetailScreen : Click Player Card
    MatchMode --> PlayerDetailScreen : Click Player Card (Unassigned or On-Court)
    PlayerDetailScreen --> SetupMode : Click Back "<-" (if from Setup)
    PlayerDetailScreen --> MatchMode : Click Back "<-" (if from MatchMode)
    MatchMode --> MatchMode : Distribute / Drag & Drop / Add Player / Undo Round / Next Round
```

### 5.1 Session Setup Mode
* **Court Configuration**: Define the number of available courts via numeric input.
* **Roster & Skill Entry**: Add players by name along with a `1–5` skill rating selector (`FilterChip` row, defaulting to `1`).
* **Reset Session Dialog**: Clicking **Reset Session** prompts with two choices:
  * **Keep Roster**: Clears match history, active slots, paused states, and offsets while preserving the player list and court count.
  * **Clear Everything**: Wipes the entire session and clears persisted local storage.

### 5.2 Matchmaking & Live Court Management (`MatchModeContent`)
* **Inline Mid-Session Player Addition**: Add late-arriving players (with name and `1–5` skill rating) directly from the **Players** panel during an active session without losing court slot assignments.
* **One-Click Auto-Distribution**: Clicking **Distribute** runs `MatchmakingEngine.generateNextRound` and populates court slots while persisting `activeSlots`.
* **Skill Badges & Team Skill Sums**: Every `PlayerCard` displays a `★Skill` badge, and each `CourtSlots` card displays `Team Skill: ★X vs ★Y` in real time as players are distributed or dragged between slots.
* **Interactive Manual Adjustments (Adaptive by Viewport)**:
  * **Compact View (`maxWidth < 800.dp`)**: Long-pressing any occupied court spot (or tapping/long-pressing an empty spot) opens `SlotPlayerPickerBottomSheet`, displaying all players sorted by availability (`Available`, `On Court X (Swap)`, `Paused`) along with a **Clear Spot** button.
  * **Expanded View (`maxWidth >= 800.dp`)**: Long-press any player card (`detectDragGesturesAfterLongPress`) to drag and swap players between court slots or back to the unassigned **Players** list.
* **Active / Paused Player Toggle**: Each unassigned player card includes an **Active** switch to pause/unpause players for upcoming distributions.
* **Undo Last Round**: Clicking **Undo Round N** pops the most recent `Round` from `sessionState.history` and restores its matches directly back onto the active court slots so mistakes can be corrected and re-committed.

### 5.3 Responsive Layout Adaptation
`MatchModeContent` uses `BoxWithConstraints` with a breakpoint at `800.dp`:

| Viewport | Layout Behavior |
| :--- | :--- |
| **Compact (`maxWidth < 800.dp`)** | Single vertically scrollable column; collapsible **Players** section (`▲` / `▼`); horizontal drag-scrollable `LazyRow` for Session History in reverse chronological order. |
| **Expanded (`maxWidth >= 800.dp`)** | Side-by-side split pane (`32%` left pane for unassigned players + inline add row, `68%` right pane for courts `LazyColumn` and wrapping `FlowRow` history cards). |

### 5.4 Player Detail & Analytics (`PlayerDetailScreen`)
Clicking any player card opens `PlayerDetailScreen`:
* **Live Skill Editor**: Edit the player's `1–5` skill rating at any point in the session.
* **Summary KPI Cards**: Displays actual rounds **Played** vs. **Sat Out**.
* **Stacked Interaction Bar Chart**: Lists every other player (with their `★Skill` badge) sorted by total shared games descending, rendering a proportional horizontal stacked bar split by **Partner** (primary color) and **Opponent** (error/red color) counts.

---

## 6. Build, Run & Test Reference

### Running Targets
* **Android**: `./gradlew :app:androidApp:assembleDebug`
* **Web (WasmJS - Modern Browsers)**: `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`
* **Web (JS - Fallback)**: `./gradlew :app:webApp:jsBrowserDevelopmentRun`
* **Server**: `./gradlew :server:run`
* **iOS**: Open `/app/iosApp` in Xcode and run on simulator or device.

### Automated Test Suite
The core test suite in `core/src/commonTest/kotlin/org/grant/matchmaker/domain/MatchmakingTest.kt` and `app/shared/src/commonTest/kotlin/org/grant/matchmaker/SharedCommonTest.kt` enforces four key invariants:
1. **Equal Court Time Invariant**: Effective play count spread among active players never exceeds `1`, including mid-session late arrivals using virtual play-count offsets.
2. **Paused Player Isolation**: Paused players are never scheduled into `Round.matches` and always appear in `Round.sittingOut`.
3. **Anti-Stacking Skill Invariant**: Two skill-`5` and two skill-`1` players on a court are split into balanced `(5, 1)` vs `(5, 1)` teams (`★6 vs ★6`) instead of stacked `(5, 5)` vs `(1, 1)` teams.
4. **Persistence Round-Trip Invariant**: `SessionState` serializes and deserializes losslessly across all fields and special characters.

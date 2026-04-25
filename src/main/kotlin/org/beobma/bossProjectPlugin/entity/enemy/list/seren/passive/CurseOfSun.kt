package org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.manager.GameManager
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.beobma.bossProjectPlugin.manager.PlayerStatusEffectManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.math.floor

class CurseOfSun : BossPassive(), Listener {
    private val maxGauge = 1000
    private val disabledMillis = 5_000L

    private val phase2GaugeIntervalTicks = 22L // 1.1s ~= 1.08s
    private val preShiftWarningTicks = 20L * 3L
    private val phase2TransitionParticleDurationTicks = 20L * 5L
    private val phase2TransitionParticleIntervalTicks = 2L
    private val phase2TransitionPostShiftMaintainTicks = 10L // 0.5s
    private val phase2TransitionParticleCountPerPlayer = 450
    private val mapShiftDistanceX = 35.0

    private val defaultNoonTicks = 20L * 5L //20L * 120L
    private val defaultSunsetTicks = 20L * 130L
    private val defaultMidnightTicks = 20L * 40L
    private val defaultDawnTicks = 20L * 130L
    private val midnightDecreaseStepTicks = defaultMidnightTicks / 8L // 5s
    private val midnightIncreaseStepTicks = 20L * 5L

    private val phase2AnchorX = -2.5
    private val phase2MinX = -2.5
    private val phase2MaxX = 27.5
    private val phase2MinY = -37.0
    private val phase2MaxY = -25.0
    private val phase2MinZ = -127.5
    private val phase2MaxZ = -98.5
    private val laneCount = 4
    private val miniMessage = MiniMessage.miniMessage()
    private val gaugeByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    private var listenerRegistered = false

    private var timeBar: BossBar? = null
    private var phase2GaugeTask: BukkitTask? = null
    private var phase2TransitionTask: BukkitTask? = null
    private var phase2BlindnessTask: BukkitTask? = null
    private var phase2CycleInitialized = false

    private var currentTimeZone: TimeZoneState = TimeZoneState.NOON
    private var currentZoneDurationTicks: Long = defaultNoonTicks
    private var currentZoneStartedTick: Long = 0L

    private var maxMidnightTicks: Long = defaultMidnightTicks
    private var maxDawnTicks: Long = defaultDawnTicks

    private var reachedFullGaugeInCurrentZone = false

    override val validPhases: Set<Int> = setOf(1, 2)

    override val name: String = "태양의 저주"
    override val description: List<String> = listOf(
        "<gray>세렌의 일부 패턴에 피격당할 경우 게이지가 증가한다.",
        "<gray>게이지가 가득 차면 5초간 행동 불가 및 회복 불가 상태가 된다.",
        "<gray>2페이즈에서는 시간대 순환에 따라 태양의 저주 수치가 자연 증감한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)

    override fun inject(enemyData: org.beobma.bossProjectPlugin.entity.enemy.EnemyData) {
        super.inject(enemyData)
        if (listenerRegistered) return
        Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
        listenerRegistered = true
    }

    override fun onTick() {
        if (!isPhaseValid()) return

        if (enemyData.phase == 2) {
            ensurePhase2Initialized()
            updateTimeBar()
        } else {
            clearPhase2State()
        }

        game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { it.isOnline }
            .forEach { player ->
                player.sendActionBar(buildActionBarText(player.uniqueId))
            }
    }

    override fun onGameEnd() {
        clearPhase2State()
    }

    fun increaseGauge(player: Player, amount: Int) {
        if (!isPhaseValid()) return
        if (!PlayerDeathLifecycleManager.canBeTargetedByPattern(player)) return

        val uuid = player.uniqueId
        val current = gaugeByPlayer[uuid] ?: 0
        val next = (current + amount).coerceIn(0, maxGauge)
        gaugeByPlayer[uuid] = next

        player.sendActionBar(buildActionBarText(uuid))

        if (next < maxGauge) return

        onGaugeReachedMax(player)
    }

    private fun onGaugeReachedMax(player: Player) {
        gaugeByPlayer[player.uniqueId] = 0
        PlayerStatusEffectManager.apply(player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED, disabledMillis)

        if (enemyData.phase != 2) return

        reachedFullGaugeInCurrentZone = true

        maxMidnightTicks = (maxMidnightTicks - midnightDecreaseStepTicks).coerceAtLeast(0L)
        maxDawnTicks += midnightDecreaseStepTicks
        checkMidnightExhausted()
    }

    private fun ensurePhase2Initialized() {
        if (phase2CycleInitialized) return

        phase2CycleInitialized = true
        currentTimeZone = TimeZoneState.NOON
        currentZoneDurationTicks = defaultNoonTicks
        currentZoneStartedTick = enemyStatus.elapsedTicks
        maxMidnightTicks = defaultMidnightTicks
        maxDawnTicks = defaultDawnTicks
        reachedFullGaugeInCurrentZone = false

        val created = Bukkit.createBossBar(currentTimeZone.displayName, currentTimeZone.barColor, BarStyle.SOLID)
        created.isVisible = true
        game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .forEach { created.addPlayer(it) }
        timeBar = created
        updateTimeBar()

        phase2GaugeTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPhase2StillRunning()) {
                    clearPhase2State()
                    return@Runnable
                }
                applyNaturalGaugeByTimeZone()
            },
            phase2GaugeIntervalTicks,
            phase2GaugeIntervalTicks
        )

        scheduleNextZoneTransition()
    }

    private fun isPhase2StillRunning(): Boolean {
        val currentGame = GameManager.getCurrentGame() ?: return false
        if (currentGame !== game) return false
        if (currentGame.bossData !== enemyData) return false
        return enemyData.phase == 2
    }

    private fun clearPhase2State() {
        phase2GaugeTask?.cancel()
        phase2GaugeTask = null

        phase2TransitionTask?.cancel()
        phase2TransitionTask = null

        phase2BlindnessTask?.cancel()
        phase2BlindnessTask = null

        timeBar?.removeAll()
        timeBar = null

        phase2CycleInitialized = false
    }

    private fun applyNaturalGaugeByTimeZone() {
        val amount = currentTimeZone.gaugeDelta
        if (amount == 0) return

        game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .forEach { increaseGauge(it, amount) }
    }

    private fun scheduleNextZoneTransition() {
        phase2TransitionTask?.cancel()

        phase2TransitionTask = BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPhase2StillRunning()) return@Runnable
                executePreShiftEvent()
            },
            currentZoneDurationTicks
        )
    }

    private fun onTimeZoneEnded() {
        if (!reachedFullGaugeInCurrentZone) {
            maxMidnightTicks += midnightIncreaseStepTicks
            maxDawnTicks = (maxDawnTicks - midnightIncreaseStepTicks).coerceAtLeast(0L)
        }

        reachedFullGaugeInCurrentZone = false

        currentTimeZone = currentTimeZone.next()
        currentZoneDurationTicks = durationFor(currentTimeZone)
        currentZoneStartedTick = enemyStatus.elapsedTicks
        updateTimeBar()

        checkMidnightExhausted()
        if (!isPhase2StillRunning()) return

        scheduleNextZoneTransition()
    }

    private fun updateTimeBar() {
        val bar = timeBar ?: return
        if (!isPhase2StillRunning()) return

        game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .forEach { if (!bar.players.contains(it)) bar.addPlayer(it) }

        bar.setTitle(currentTimeZone.displayName)
        bar.color = currentTimeZone.barColor

        val elapsed = (enemyStatus.elapsedTicks - currentZoneStartedTick).coerceAtLeast(0L)
        val remaining = (currentZoneDurationTicks - elapsed).coerceAtLeast(0L)
        val progress = if (currentZoneDurationTicks <= 0L) 0.0 else remaining.toDouble() / currentZoneDurationTicks.toDouble()
        bar.progress = progress.coerceIn(0.0, 1.0)
    }

    private fun durationFor(state: TimeZoneState): Long {
        return when (state) {
            TimeZoneState.NOON -> defaultNoonTicks
            TimeZoneState.SUNSET -> defaultSunsetTicks
            TimeZoneState.MIDNIGHT -> maxMidnightTicks
            TimeZoneState.DAWN -> maxDawnTicks
        }
    }

    private fun checkMidnightExhausted() {
        if (maxMidnightTicks > 0L) return
        GameManager.terminateCurrentGame("자정의 시간이 모두 소진되어 전투에서 패배했습니다.")
    }

    private fun executePreShiftEvent() {
        val world = enemyData.mapData.world()
        if (world.players.isEmpty()) return

        val safeZone = SafeZone.entries.random()

        world.playSound(Location(world, 12.5, -34.0, -113.0), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.MASTER, 0.7f, 0.8f)
        world.players.forEach { player ->
            player.sendMessage(miniMessage.deserialize("<yellow>[시간 전환]</yellow> <green>${safeZone.displayName}</green><gray>이(가) 안전지대입니다. 3초 안에 이동하세요.</gray>"))
        }

        BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPhase2StillRunning()) return@Runnable

                startPhase2TransitionParticle(world) {
                    if (!isPhase2StillRunning()) return@startPhase2TransitionParticle

                    world.players
                        .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
                        .forEach { player ->
                            if (!safeZone.contains(player.location)) {
                                game.consumeDeathIfAvailable(player.uniqueId)
                                player.level = game.remainingDeaths(player.uniqueId) ?: player.level
                                player.sendMessage(miniMessage.deserialize("<red>안전지대 밖에 있어 데스 카운트를 1 소모했습니다.</red>"))
                            }
                        }

                    world.players
                        .filter { it.isOnline }
                        .forEach { shiftPlayerToNextLane(it) }

                    onTimeZoneEnded()
                }
            },
            preShiftWarningTicks
        )
    }

    private fun startPhase2TransitionParticle(world: org.bukkit.World, onCompleted: () -> Unit) {
        phase2BlindnessTask?.cancel()

        val totalSteps = (phase2TransitionParticleDurationTicks / phase2TransitionParticleIntervalTicks).toInt().coerceAtLeast(1)
        val maintainSteps = (phase2TransitionPostShiftMaintainTicks / phase2TransitionParticleIntervalTicks).toInt().coerceAtLeast(1)
        var step = 0
        var transitionCompleted = false
        var remainingMaintainSteps = maintainSteps
        val markerBlockData = Material.WHITE_CONCRETE.createBlockData()

        phase2BlindnessTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPhase2StillRunning()) {
                    phase2BlindnessTask?.cancel()
                    phase2BlindnessTask = null
                    return@Runnable
                }

                world.players
                    .asSequence()
                    .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
                    .forEach { player ->
                        val eyeLocation = player.eyeLocation
                        world.spawnParticle(
                            Particle.BLOCK_MARKER,
                            eyeLocation,
                            phase2TransitionParticleCountPerPlayer,
                            0.75,
                            0.45,
                            0.75,
                            0.0,
                            markerBlockData
                        )
                    }

                step++
                if (!transitionCompleted && step > totalSteps) {
                    transitionCompleted = true
                    onCompleted()
                }

                if (transitionCompleted) {
                    remainingMaintainSteps--
                    if (remainingMaintainSteps <= 0) {
                        phase2BlindnessTask?.cancel()
                        phase2BlindnessTask = null
                    }
                }
            },
            0L,
            phase2TransitionParticleIntervalTicks
        )
    }

    private fun shiftPlayerToNextLane(player: Player) {
        val location = player.location.clone()
        val laneIndex = laneIndexFor(location.x)
        val nextLane = (laneIndex + 1) % laneCount

        val laneOriginX = phase2AnchorX + laneIndex * mapShiftDistanceX
        val nextOriginX = phase2AnchorX + nextLane * mapShiftDistanceX
        val normalizedX = location.x - laneOriginX
        val nextX = nextOriginX + normalizedX

        val safeX = nextX.coerceIn(phase2MinX + nextLane * mapShiftDistanceX, phase2MaxX + nextLane * mapShiftDistanceX)
        val safeY = location.y.coerceIn(phase2MinY, phase2MaxY + 10.0)
        val safeZ = location.z.coerceIn(phase2MinZ, phase2MaxZ)

        player.teleport(Location(location.world, safeX, safeY, safeZ, location.yaw, location.pitch))
    }

    private fun laneIndexFor(x: Double): Int {
        val relative = floor((x - phase2AnchorX) / mapShiftDistanceX).toInt()
        return relative.coerceIn(0, laneCount - 1)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!isPhaseValid()) return
        if (!PlayerStatusEffectManager.isActive(event.player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        val from = event.from
        val to = event.to ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (!isPhaseValid()) return
        if (!PlayerStatusEffectManager.isActive(event.player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        if (event.action == Action.PHYSICAL) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        if (!isPhaseValid()) return
        if (!PlayerStatusEffectManager.isActive(event.player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwap(event: PlayerSwapHandItemsEvent) {
        if (!isPhaseValid()) return
        if (!PlayerStatusEffectManager.isActive(event.player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDrop(event: PlayerDropItemEvent) {
        if (!isPhaseValid()) return
        if (!PlayerStatusEffectManager.isActive(event.player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        if (!isPhaseValid()) return
        val attacker = event.damager as? Player ?: return
        if (!PlayerStatusEffectManager.isActive(attacker.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerRegainHealth(event: EntityRegainHealthEvent) {
        if (!isPhaseValid()) return
        val player = event.entity as? Player ?: return
        if (!PlayerStatusEffectManager.isActive(player.uniqueId, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED)) return
        event.isCancelled = true
    }

    private fun buildActionBarText(uuid: UUID): net.kyori.adventure.text.Component {
        val gauge = gaugeByPlayer[uuid] ?: 0
        val barLength = 20
        val filledLength = ((gauge.toDouble() / maxGauge) * barLength).toInt().coerceIn(0, barLength)
        val emptyLength = barLength - filledLength
        val bar = "<gold>${"░".repeat(filledLength)}</gold><dark_gray>${"░".repeat(emptyLength)}</dark_gray>"

        return miniMessage.deserialize(
            "<yellow><bold>[</yellow> $bar <yellow><bold>]</yellow>"
        )
    }

    private enum class TimeZoneState(
        val displayName: String,
        val gaugeDelta: Int,
        val barColor: BarColor
    ) {
        NOON("정오", 2, BarColor.YELLOW),
        SUNSET("석양", 1, BarColor.PINK),
        MIDNIGHT("자정", -80, BarColor.PURPLE),
        DAWN("여명", 1, BarColor.BLUE);

        fun next(): TimeZoneState {
            return when (this) {
                NOON -> SUNSET
                SUNSET -> MIDNIGHT
                MIDNIGHT -> DAWN
                DAWN -> NOON
            }
        }
    }

    private enum class SafeZone(
        val displayName: String,
        private val minX: Double,
        private val minY: Double,
        private val minZ: Double,
        private val maxX: Double,
        private val maxY: Double,
        private val maxZ: Double
    ) {
        ZONE_1("1구역", -2.5, -37.0, -111.5, 11.5, -25.0, -98.5),
        ZONE_2("2구역", 14.5, -37.0, -111.5, 27.5, -25.0, -98.5),
        ZONE_3("3구역", 14.5, -37.0, -127.5, 27.5, -25.0, -114.5),
        ZONE_4("4구역", -2.5, -37.0, -127.5, 11.5, -25.0, -114.5);

        fun contains(location: Location): Boolean {
            return location.x in minX..maxX &&
                    location.y in minY..maxY &&
                    location.z in minZ..maxZ
        }

        fun center(world: org.bukkit.World): Location {
            return Location(
                world,
                (minX + maxX) / 2.0,
                (minY + maxY) / 2.0,
                (minZ + maxZ) / 2.0
            )
        }
    }
}

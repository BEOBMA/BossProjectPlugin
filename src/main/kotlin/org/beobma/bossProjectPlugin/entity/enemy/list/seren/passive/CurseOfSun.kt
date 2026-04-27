package org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.manager.GameManager
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.beobma.bossProjectPlugin.manager.PlayerStatusEffectManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
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
import kotlin.math.ceil
import kotlin.math.floor

class CurseOfSun : BossPassive(), Listener {
    companion object {
        private var timeBossBar: BossBar? = null

        private const val NOON_DURATION_MILLIS = 10_000L // 120_000L
        private const val SUNSET_DURATION_MILLIS = 10_000L // 130_000L
        private const val MIDNIGHT_DURATION_MILLIS = 10_000L // 40_000L
        private const val DAWN_DURATION_MILLIS = 10_000L // 130_000L
        private const val PERIOD_ADJUSTMENT_MILLIS = 5_000L

        private const val BASE_MIDNIGHT_MILLIS = MIDNIGHT_DURATION_MILLIS
        private const val BASE_DAWN_MILLIS = DAWN_DURATION_MILLIS
        private const val MIDNIGHT_REDUCTION_PER_FULL_GAUGE = BASE_MIDNIGHT_MILLIS / 8L

        private fun clearTimeBossBar() {
            timeBossBar?.removeAll()
            timeBossBar = null
        }
    }

    private enum class TimePeriod(
        val displayName: String,
        val defaultDurationMillis: Long,
        val gaugeDelta: Int,
        val cloneCommand: String?,
        val minecraftTime: Long
    ) {
        NOON("정오", NOON_DURATION_MILLIS, 2, "/clone 28 -25 -97 -3 -38 -128 31 -38 -92", 6_000L),
        SUNSET("석양", SUNSET_DURATION_MILLIS, 1, "/clone 62 -25 -97 31 -38 -128 31 -38 -92", 12_000L),
        MIDNIGHT("자정", BASE_MIDNIGHT_MILLIS, -80, "/clone 96 -25 -97 65 -38 -128 31 -38 -92", 18_000L),
        DAWN("여명", BASE_DAWN_MILLIS, 1, "/clone 130 -25 -97 99 -38 -128 31 -38 -92", 23_000L)
    }

    private data class SafeZone(
        val minX: Double,
        val minY: Double,
        val minZ: Double,
        val maxX: Double,
        val maxY: Double,
        val maxZ: Double
    ) {
        fun contains(player: Player): Boolean {
            val location = player.location
            return location.x in minX..maxX &&
                location.y in minY..maxY &&
                location.z in minZ..maxZ
        }
    }

    private val maxGauge = 1000
    private val disabledMillis = 5_000L
    private val naturalGaugeTickMillis = 1_080L
    private val safeZoneDecisionDelayMillis = 3_000L
    private val timeChangeDelayAfterPenaltyMillis = 5_000L

    private val miniMessage = MiniMessage.miniMessage()
    private val gaugeByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    private val safeZones: List<SafeZone> = listOf(
        SafeZone(48.5, -37.0, -75.5, 61.5, -25.0, -62.5),
        SafeZone(32.5, -37.0, -75.5, 45.5, -25.0, -62.5),
        SafeZone(32.5, -37.0, -91.5, 45.5, -25.0, -78.5),
        SafeZone(48.5, -37.0, -91.5, 61.5, -25.0, -78.5)
    )

    private var listenerRegistered = false
    private var initializedPhase = false

    private var currentTimePeriod: TimePeriod = TimePeriod.NOON
    private var currentTimePeriodElapsedMillis: Long = 0L
    private var naturalGaugeElapsedMillis: Long = 0L
    private var maxMidnightMillis: Long = BASE_MIDNIGHT_MILLIS
    private var maxDawnMillis: Long = BASE_DAWN_MILLIS
    private var safeZonePrepared = false
    private var currentSafeZone: SafeZone? = null
    private var pendingPeriodTransition = false
    private var pendingTransitionMillis = 0L
    private var transitionEffectTask: BukkitTask? = null
    private var midnightExpired = false

    override val validPhases: Set<Int> = setOf(1, 2)

    override val name: String = "태양의 저주"
    override val description: List<String> = listOf(
        "<gray>세렌의 일부 패턴에 피격당할 경우 게이지가 증가한다.",
        "<gray>게이지가 가득 차면 5초간 행동 불가 및 회복 불가 상태가 된다."
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)

    override fun inject(enemyData: org.beobma.bossProjectPlugin.entity.enemy.EnemyData) {
        super.inject(enemyData)
        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
            listenerRegistered = true
        }
    }

    override fun onTick() {
        if (!isPhaseValid()) return

        if (!initializedPhase) {
            initializeForCurrentPhase()
            initializedPhase = true
        }

        if (enemyData.phase == 2) {
            handlePhaseTwoTick()
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
        clearTimeBossBar()
        clearTransitionEffects()
        safeZonePrepared = false
        currentSafeZone = null
        pendingPeriodTransition = false
        pendingTransitionMillis = 0L
        midnightExpired = false
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

        gaugeByPlayer[uuid] = 0
        PlayerStatusEffectManager.apply(uuid, PlayerStatusEffectManager.Effect.ACTION_RESTRICTED, disabledMillis)

        if (enemyData.phase == 2) {
            maxMidnightMillis = (maxMidnightMillis - MIDNIGHT_REDUCTION_PER_FULL_GAUGE).coerceAtLeast(0L)
            maxDawnMillis += MIDNIGHT_REDUCTION_PER_FULL_GAUGE
            Bukkit.broadcast(miniMessage.deserialize("<red>태양이 강해져 빛을 잃는 시간이 감소됩니다.</red>"))
            if (maxMidnightMillis <= 0L) {
                triggerMidnightExpiredCinematic()
            }
        }
    }

    private fun initializeForCurrentPhase() {
        when (enemyData.phase) {
            1 -> {
                clearTimeBossBar()
                executeCloneCommand("/clone 28 -25 -97 -3 -38 -128 31 -38 -92")
            }
            2 -> {
                executeCloneCommand("/clone 28 -25 -97 -3 -38 -128 31 -38 -92")
                currentTimePeriod = TimePeriod.NOON
                currentTimePeriodElapsedMillis = 0L
                naturalGaugeElapsedMillis = 0L
                safeZonePrepared = false
                currentSafeZone = null
                pendingPeriodTransition = false
                pendingTransitionMillis = 0L
                clearTransitionEffects()
                initializeTimeBossBar()
                applyMinecraftTimeForCurrentPeriod()
                updateTimeBossBar()
                broadcastCurrentPeriodStartMessage()
            }
        }
    }

    private fun handlePhaseTwoTick() {
        naturalGaugeElapsedMillis += 1_000L
        while (naturalGaugeElapsedMillis >= naturalGaugeTickMillis) {
            naturalGaugeElapsedMillis -= naturalGaugeTickMillis
            applyNaturalGaugeByTimePeriod()
        }

        if (pendingPeriodTransition) {
            pendingTransitionMillis += 1_000L
            if (pendingTransitionMillis >= timeChangeDelayAfterPenaltyMillis) {
                clearTransitionEffects()
                finishCurrentPeriodAndMoveNext()
            }
            updateTimeBossBar()
            return
        }

        currentTimePeriodElapsedMillis += 1_000L

        val currentDuration = currentTimePeriodDurationMillis()
        if (!safeZonePrepared && currentTimePeriodElapsedMillis >= currentDuration) {
            prepareSafeZoneForNextTransition()
            safeZonePrepared = true
        }

        updateTimeBossBar()
    }

    private fun applyNaturalGaugeByTimePeriod() {
        val delta = currentTimePeriod.gaugeDelta
        game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .forEach { player ->
                if (delta >= 0) {
                    increaseGauge(player, delta)
                } else {
                    val uuid = player.uniqueId
                    val next = ((gaugeByPlayer[uuid] ?: 0) + delta).coerceIn(0, maxGauge)
                    gaugeByPlayer[uuid] = next
                }
            }
    }

    private fun finishCurrentPeriodAndMoveNext() {
        if (allTargetPlayersGaugeNotFull()) {
            maxMidnightMillis += PERIOD_ADJUSTMENT_MILLIS
            maxDawnMillis = (maxDawnMillis - PERIOD_ADJUSTMENT_MILLIS).coerceAtLeast(0L)
            Bukkit.broadcast(miniMessage.deserialize("<green>태양이 강해지지 않아 빛을 잃는 시간이 증가합니다.</green>"))
        }

        currentTimePeriod = nextTimePeriod(currentTimePeriod)
        currentTimePeriodElapsedMillis = 0L
        safeZonePrepared = false
        currentSafeZone = null
        pendingPeriodTransition = false
        pendingTransitionMillis = 0L
        executeCloneCommand(currentTimePeriod.cloneCommand)
        applyMinecraftTimeForCurrentPeriod()
        updateTimeBossBar()
        broadcastCurrentPeriodStartMessage()
    }

    private fun nextTimePeriod(current: TimePeriod): TimePeriod = when (current) {
        TimePeriod.NOON -> TimePeriod.SUNSET
        TimePeriod.SUNSET -> TimePeriod.MIDNIGHT
        TimePeriod.MIDNIGHT -> TimePeriod.DAWN
        TimePeriod.DAWN -> TimePeriod.NOON
    }

    private fun currentTimePeriodDurationMillis(): Long = when (currentTimePeriod) {
        TimePeriod.MIDNIGHT -> maxMidnightMillis
        TimePeriod.DAWN -> maxDawnMillis
        else -> currentTimePeriod.defaultDurationMillis
    }

    private fun allTargetPlayersGaugeNotFull(): Boolean {
        return game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .all { (gaugeByPlayer[it.uniqueId] ?: 0) < maxGauge }
    }

    private fun prepareSafeZoneForNextTransition() {
        currentSafeZone = safeZones.random()
        val players = game.playerDatas.map { it.player }.filter { it.isOnline }
        val zone = currentSafeZone ?: return
        val world = players.firstOrNull()?.world ?: return

        for (x in ceil(zone.minX).toInt()..floor(zone.maxX).toInt()) {
            for (z in ceil(zone.minZ).toInt()..floor(zone.maxZ).toInt()) {
                world.spawnParticle(
                    Particle.END_ROD,
                    x + 0.5,
                    zone.minY + 0.1,
                    z + 0.5,
                    8,
                    0.05,
                    0.03,
                    0.05,
                    0.0
                )
            }
        }

        Bukkit.broadcast(miniMessage.deserialize("<yellow>시간이 흐르고 태양 또한 정해진 순환에 따라 변화합니다.</yellow>"))
        BossProjectPlugin.instance.server.scheduler.runTaskLater(BossProjectPlugin.instance, Runnable {
            val currentZone = currentSafeZone ?: return@Runnable
            players
                .filter { player -> PlayerDeathLifecycleManager.canBeTargetedByPattern(player) && !currentZone.contains(player) }
                .forEach { player ->
                    increaseGauge(player, maxGauge)
                    PlayerDeathLifecycleManager.forceConsumeDeathCount(player)
                }
            pendingPeriodTransition = true
            pendingTransitionMillis = 0L
            startTransitionEffects()
        }, safeZoneDecisionDelayMillis / 50L)
    }

    private fun startTransitionEffects() {
        clearTransitionEffects()
        transitionEffectTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!pendingPeriodTransition) {
                    clearTransitionEffects()
                    return@Runnable
                }
                val players = game.playerDatas
                    .asSequence()
                    .map { it.player }
                    .filter { it.isOnline }
                    .toList()
                players.forEach { player ->
                    player.world.spawnParticle(
                        Particle.BLOCK_MARKER,
                        player.eyeLocation,
                        3000,
                        5.0,
                        5.0,
                        5.0,
                        0.0,
                        Material.WHITE_CONCRETE.createBlockData()
                    )
                    player.playSound(player.location, Sound.ENTITY_GUARDIAN_ATTACK, 0.55f, 1.65f)
                }
            },
            0L,
            2L
        )
    }

    private fun clearTransitionEffects() {
        transitionEffectTask?.cancel()
        transitionEffectTask = null
    }

    private fun triggerMidnightExpiredCinematic() {
        if (midnightExpired) return
        midnightExpired = true
        clearTransitionEffects()
        pendingPeriodTransition = false
        pendingTransitionMillis = 0L
        GameManager.runTypedSubtitleCinematic("태양이 지지 않는다면 누구도 나에게 대항할 수 없다.", postDelayTicks = 60L) {
            GameManager.terminateCurrentGame("자정 시간이 완전히 소멸했습니다.")
        }
    }

    private fun broadcastCurrentPeriodStartMessage() {
        val message = when (currentTimePeriod) {
            TimePeriod.NOON -> "태양의 빛으로 가득찬 정오가 시작됩니다."
            TimePeriod.SUNSET -> "황혼의 불타는 듯한 석양이 회복 효율을 낮추고 지속적으로 피해를 입힙니다."
            TimePeriod.MIDNIGHT -> "태양이 저물어 빛을 잃고 자정이 시작됩니다."
            TimePeriod.DAWN -> "태양이 서서히 떠올라 빛과 희망이 시작되는 여명이 다가옵니다."
        }
        Bukkit.broadcast(miniMessage.deserialize("<yellow>$message</yellow>"))
    }

    private fun initializeTimeBossBar() {
        clearTimeBossBar()
        val created = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID)
        created.isVisible = true
        game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { it.isOnline }
            .forEach { created.addPlayer(it) }
        timeBossBar = created
    }

    private fun updateTimeBossBar() {
        val target = timeBossBar ?: return
        val totalMillis = currentTimePeriodDurationMillis().coerceAtLeast(1L)
        val remainingMillis = (totalMillis - currentTimePeriodElapsedMillis).coerceAtLeast(0L)
        target.setTitle(currentTimePeriod.displayName)
        target.progress = (remainingMillis.toDouble() / totalMillis.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun executeCloneCommand(command: String?) {
        if (command.isNullOrBlank()) return
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.removePrefix("/"))
    }

    private fun applyMinecraftTimeForCurrentPeriod() {
        val players = game.playerDatas
            .asSequence()
            .map { it.player }
            .filter { it.isOnline }
            .toList()
        val world = players.firstOrNull()?.world ?: return
        world.time = currentTimePeriod.minecraftTime
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
}

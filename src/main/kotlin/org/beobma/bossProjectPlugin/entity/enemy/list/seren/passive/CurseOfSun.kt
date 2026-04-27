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
import java.util.UUID

class CurseOfSun : BossPassive(), Listener {
    companion object {
        private var timeBossBar: BossBar? = null

        private const val BASE_MIDNIGHT_MILLIS = 40_000L
        private const val BASE_DAWN_MILLIS = 130_000L
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
        val cloneCommand: String?
    ) {
        NOON("정오", 120_000L, 2, "/clone 28 -25 -97 -3 -38 -128 31 -38 -92"),
        SUNSET("석양", 130_000L, 1, "/clone 62 -25 -97 31 -38 -128 31 -38 -92"),
        MIDNIGHT("자정", BASE_MIDNIGHT_MILLIS, -80, "/clone 96 -25 -97 65 -38 -128 31 -38 -92"),
        DAWN("여명", BASE_DAWN_MILLIS, 1, "/clone 130 -25 -97 99 -38 -128 31 -38 -92")
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
    private val safeZoneWarningMillis = 3_000L

    private val miniMessage = MiniMessage.miniMessage()
    private val gaugeByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    private val safeZones: List<SafeZone> = listOf(
        SafeZone(-2.5, -37.0, -111.5, 11.5, -25.0, -98.5),
        SafeZone(14.5, -37.0, -111.5, 27.5, -25.0, -98.5),
        SafeZone(14.5, -37.0, -127.5, 27.5, -25.0, -114.5),
        SafeZone(-2.5, -37.0, -127.5, 11.5, -25.0, -114.5)
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
            if (maxMidnightMillis <= 0L) {
                GameManager.terminateCurrentGame("자정 시간이 모두 소진되어 전투에서 패배했습니다.")
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
                initializeTimeBossBar()
                updateTimeBossBar()
            }
        }
    }

    private fun handlePhaseTwoTick() {
        naturalGaugeElapsedMillis += 1_000L
        while (naturalGaugeElapsedMillis >= naturalGaugeTickMillis) {
            naturalGaugeElapsedMillis -= naturalGaugeTickMillis
            applyNaturalGaugeByTimePeriod()
        }

        currentTimePeriodElapsedMillis += 1_000L

        val currentDuration = currentTimePeriodDurationMillis()
        val warningStartMillis = (currentDuration - safeZoneWarningMillis).coerceAtLeast(0L)
        if (!safeZonePrepared && currentTimePeriodElapsedMillis >= warningStartMillis) {
            prepareSafeZoneForNextTransition()
            safeZonePrepared = true
        }

        if (currentTimePeriodElapsedMillis >= currentDuration) {
            finishCurrentPeriodAndMoveNext()
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
            maxMidnightMillis += 5_000L
            maxDawnMillis = (maxDawnMillis - 5_000L).coerceAtLeast(0L)
        }

        currentTimePeriod = nextTimePeriod(currentTimePeriod)
        currentTimePeriodElapsedMillis = 0L
        safeZonePrepared = false
        currentSafeZone = null
        executeCloneCommand(currentTimePeriod.cloneCommand)
        updateTimeBossBar()
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

        players.forEach { player ->
            repeat(150) {
                player.world.spawnParticle(
                    Particle.BLOCK_MARKER,
                    player.location,
                    1,
                    0.25,
                    1.0,
                    0.25,
                    0.0,
                    Material.WHITE_CONCRETE.createBlockData()
                )
            }
        }

        Bukkit.broadcast(miniMessage.deserialize("<yellow>안전 지대가 정해졌습니다! 3초 안에 이동하세요.</yellow>"))

        BossProjectPlugin.instance.server.scheduler.runTaskLater(BossProjectPlugin.instance, 20L * 3L) {
            val zone = currentSafeZone ?: return@runTaskLater
            players
                .filter { player -> PlayerDeathLifecycleManager.canBeTargetedByPattern(player) && !zone.contains(player) }
                .forEach { player ->
                    PlayerDeathLifecycleManager.forceConsumeDeathCount(player, "안전 지대에 있지 않아 데스 카운트가 1 감소했습니다.")
                }
        }
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

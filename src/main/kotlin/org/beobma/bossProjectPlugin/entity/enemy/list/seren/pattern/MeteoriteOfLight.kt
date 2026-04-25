package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.random.Random

class MeteoriteOfLight : PatternSkill(), Listener {
    private val meteoriteCount = 8
    private val cooldownTick = 20L * 10
    private val minSpawnY = -25.0
    private val minX = 32.5
    private val maxX = 61.5
    private val minZ = -91.5
    private val maxZ = -62.5
    private val collisionCheckPeriodTick = 2L
    private val minSpawnGapSquared = 9.0 // 3 blocks
    private val randomDropDelayRangeTick = 0L..80L
    private val activeDurationMillis = 5_000L
    private val missDurationMillis = 3_000L
    private val damageRatio = 0.15

    private val meteoriteStates: MutableList<MeteoriteState> = mutableListOf()
    private val missUntilByPlayer: MutableMap<UUID, Long> = mutableMapOf()
    private var listenerRegistered = false

    override val name: String = "빛의 운석"
    override val description: List<String> = listOf(
        "<gray>무작위 위치에 빛의 운석이 나눠서 낙하한다.",
        "<gray>피격 시 15%의 피해를 받고 3초간 빗나감 상태에 걸린다."
    )
    override val itemStack: ItemStack = ItemStack(Material.LIGHT)

    override fun inject(enemyData: org.beobma.bossProjectPlugin.entity.enemy.EnemyData) {
        super.inject(enemyData)

        if (meteoriteStates.isEmpty()) {
            val random = Random(System.nanoTime())
            meteoriteStates += (1..meteoriteCount).map { number ->
                MeteoriteState(
                    number = number,
                    nextAvailableTick = random.nextLong(0L, cooldownTick + 1)
                )
            }
        }

        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
            listenerRegistered = true
        }
    }

    override fun canUse(): Boolean = true

    override fun onUse() {
        val nowTick = enemyStatus.elapsedTicks

        val reservedLocations = meteoriteStates
            .mapNotNull { it.pendingSpawnLocation }
            .toMutableList()
        reservedLocations += meteoriteStates.mapNotNull { it.activeSpawnLocation }

        meteoriteStates.forEach { state ->
            if (state.isPending() || state.isActive()) return@forEach
            if (nowTick < state.nextAvailableTick) return@forEach

            val spawnLocation = pickSpawnLocation(reservedLocations) ?: return@forEach
            reservedLocations += spawnLocation

            state.pendingStartTick = nowTick + Random.nextLong(
                randomDropDelayRangeTick.first,
                randomDropDelayRangeTick.last + 1
            )
            state.pendingSpawnLocation = spawnLocation
            state.nextAvailableTick = nowTick + cooldownTick
        }

        meteoriteStates.forEach { state ->
            if (state.isPending() && nowTick >= (state.pendingStartTick ?: Long.MAX_VALUE)) {
                val spawnLocation = state.pendingSpawnLocation ?: return@forEach
                startMeteorite(state, spawnLocation)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        val attacker = event.damager as? Player
        val shooter = (event.damager as? org.bukkit.entity.Projectile)?.shooter as? Player
        val player = attacker ?: shooter ?: return

        if (!isAttackMissActive(player.uniqueId)) return
        event.isCancelled = true
    }

    private fun startMeteorite(state: MeteoriteState, spawnLocation: SpawnLocation) {
        val world = enemyData.mapData.world()
        if (world.uid != spawnLocation.worldUid) return

        runFunctionWithCommandBlockMinecartAt(
            spawnLocation,
            "meteorite_of_light_${state.number}:_/create"
        )
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "function meteorite_of_light_${state.number}:a/default/play_anim")

        val startedAt = System.currentTimeMillis()
        val hitCheckTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                val nowMillis = System.currentTimeMillis()
                if (nowMillis - startedAt >= activeDurationMillis) {
                    cleanupMeteorite(state)
                    return@Runnable
                }

                val display = resolveDisplayEntity(world, state, spawnLocation)
                state.activeDisplayUuid = display?.uniqueId ?: state.activeDisplayUuid

                val targetDisplay = state.activeDisplayUuid
                    ?.let { uuid -> world.getEntity(uuid) as? BlockDisplay }
                    ?: display
                    ?: return@Runnable

                val hitPlayer = world.players
                    .asSequence()
                    .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
                    .firstOrNull { player -> player.boundingBox.overlaps(targetDisplay.boundingBox) }

                if (hitPlayer != null) {
                    applyMeteoriteHit(hitPlayer)
                    cleanupMeteorite(state)
                }
            },
            1L,
            collisionCheckPeriodTick
        )

        state.pendingStartTick = null
        state.pendingSpawnLocation = null
        state.activeStartedAtMillis = startedAt
        state.activeSpawnLocation = spawnLocation
        state.hitCheckTask = hitCheckTask
    }

    private fun applyMeteoriteHit(player: Player) {
        player.damage(player.maxHealth * damageRatio, enemyData.entity)
        missUntilByPlayer[player.uniqueId] = System.currentTimeMillis() + missDurationMillis
    }

    private fun cleanupMeteorite(state: MeteoriteState) {
        state.hitCheckTask?.cancel()
        state.hitCheckTask = null

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "function meteorite_of_light_${state.number}:_/delete")

        state.pendingStartTick = null
        state.pendingSpawnLocation = null
        state.activeStartedAtMillis = null
        state.activeSpawnLocation = null
        state.activeDisplayUuid = null
    }

    private fun resolveDisplayEntity(
        world: org.bukkit.World,
        state: MeteoriteState,
        spawnLocation: SpawnLocation
    ): BlockDisplay? {
        val cached = state.activeDisplayUuid?.let { uuid -> world.getEntity(uuid) as? BlockDisplay }
        if (cached != null && isMeteoriteDisplay(cached, state.number)) return cached

        return world.entities
            .asSequence()
            .mapNotNull { it as? BlockDisplay }
            .filter { isMeteoriteDisplay(it, state.number) }
            .minByOrNull { it.location.distanceSquared(spawnLocation.toLocation(world)) }
    }

    private fun isMeteoriteDisplay(display: BlockDisplay, meteoriteNumber: Int): Boolean {
        return display.scoreboardTags.contains("meteorite_of_light_$meteoriteNumber")
    }

    private fun runFunctionWithCommandBlockMinecartAt(spawnLocation: SpawnLocation, functionId: String) {
        val worldName = enemyData.mapData.world().name
        val x = "%.3f".format(java.util.Locale.US, spawnLocation.x)
        val y = "%.3f".format(java.util.Locale.US, spawnLocation.y)
        val z = "%.3f".format(java.util.Locale.US, spawnLocation.z)
        val summonCommand = buildString {
            append("execute in ")
            append(worldName)
            append(" positioned ")
            append(x)
            append(" ")
            append(y)
            append(" ")
            append(z)
            append(" run summon command_block_minecart ~ ~ ~ {Command:\"function ")
            append(functionId)
            append("\"}")
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), summonCommand)
        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            "execute in $worldName positioned $x $y $z run kill @e[type=command_block_minecart,distance=..2,limit=1,sort=nearest]"
        )
    }

    private fun pickSpawnLocation(reservedLocations: List<SpawnLocation>): SpawnLocation? {
        val world = enemyData.mapData.world()

        repeat(32) {
            val x = Random.nextDouble(minX, maxX)
            val z = Random.nextDouble(minZ, maxZ)
            val candidate = SpawnLocation(world.uid, x, minSpawnY, z)

            val overlapped = reservedLocations.any { other ->
                candidate.worldUid == other.worldUid && candidate.distanceSquared(other) < minSpawnGapSquared
            }
            if (!overlapped) return candidate
        }

        return null
    }

    private fun isAttackMissActive(uuid: UUID): Boolean {
        val missUntil = missUntilByPlayer[uuid] ?: return false
        if (System.currentTimeMillis() <= missUntil) return true
        missUntilByPlayer.remove(uuid)
        return false
    }

    private data class MeteoriteState(
        val number: Int,
        var nextAvailableTick: Long,
        var pendingStartTick: Long? = null,
        var pendingSpawnLocation: SpawnLocation? = null,
        var activeStartedAtMillis: Long? = null,
        var activeSpawnLocation: SpawnLocation? = null,
        var activeDisplayUuid: UUID? = null,
        var hitCheckTask: BukkitTask? = null
    ) {
        fun isPending(): Boolean = pendingStartTick != null

        fun isActive(): Boolean = activeStartedAtMillis != null && activeSpawnLocation != null
    }

    private data class SpawnLocation(
        val worldUid: UUID,
        val x: Double,
        val y: Double,
        val z: Double
    ) {
        fun toLocation(world: org.bukkit.World): org.bukkit.Location {
            return org.bukkit.Location(world, x, y, z)
        }

        fun distanceSquared(other: SpawnLocation): Double {
            val dx = x - other.x
            val dy = y - other.y
            val dz = z - other.z
            return dx * dx + dy * dy + dz * dz
        }
    }
}

package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import kotlin.random.Random

class LightArrow : PatternSkill() {
    private val cooldownTick = 20L * 40
    private val barrageDurationTick = 20L * 5
    private val spawnIntervalTick = 4L
    private val arrowsPerWave = 12

    private val minX = 32.0
    private val maxX = 61.0
    private val minZ = -91.0
    private val maxZ = -62.0
    private val floorY = -37.0
    private val topY = -27.0
    private val fallDurationTick = 10L

    private val hitRadius = 0.75
    private val damageRatio = 0.05
    private val curseGaugeIncrease = 20

    private val activeTasks: MutableSet<BukkitTask> = mutableSetOf()

    override val name: String = "빛의 화살"
    override val description: List<String> = listOf(
        "<gray>40초마다 무작위 위치에 수직으로 떨어지는 빛의 화살을 대량으로 소환한다.",
        "<gray>피격 시 5%의 피해를 입고 태양의 저주 수치가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.END_ROD)

    override fun canUse(): Boolean = canUseOnCooldown(cooldownTick)

    override fun onUse() {
        markUsedNow()

        val world = enemyData.mapData.world()
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun
        var elapsed = 0L

        val barrageTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (elapsed >= barrageDurationTick) {
                    return@Runnable
                }

                repeat(arrowsPerWave) {
                    val x = Random.nextDouble(minX, maxX)
                    val z = Random.nextDouble(minZ, maxZ)
                    spawnFallingArrow(Location(world, x, topY, z), curseOfSun)
                }

                world.playSound(enemyData.entity.location, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.MASTER, 0.18f, 1.9f)
                elapsed += spawnIntervalTick
            },
            0L,
            spawnIntervalTick
        )
        activeTasks += barrageTask

        lateinit var stopTask: BukkitTask
        stopTask = BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                barrageTask.cancel()
                activeTasks.remove(barrageTask)
                activeTasks.remove(stopTask)
            },
            barrageDurationTick + spawnIntervalTick
        )
        activeTasks += stopTask
    }

    override fun onGameEnd() {
        activeTasks.toList().forEach { task ->
            task.cancel()
        }
        activeTasks.clear()
    }

    private fun spawnFallingArrow(start: Location, curseOfSun: CurseOfSun?) {
        val world = start.world ?: return
        val alreadyHit: MutableSet<UUID> = mutableSetOf()
        var tick = 0L

        val arrowTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (tick > fallDurationTick) {
                    return@Runnable
                }

                val progress = tick.toDouble() / fallDurationTick.toDouble()
                val y = topY - (topY - floorY) * progress
                val point = Location(world, start.x, y, start.z)

                world.spawnParticle(Particle.END_ROD, point, 6, 0.03, 0.55, 0.03, 0.0)

                world.players
                    .asSequence()
                    .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
                    .filter { it.location.distanceSquared(point) <= hitRadius * hitRadius }
                    .filterNot { alreadyHit.contains(it.uniqueId) }
                    .forEach { player ->
                        applyHit(player, curseOfSun)
                        alreadyHit += player.uniqueId
                    }

                tick += 1
            },
            0L,
            1L
        )

        activeTasks += arrowTask

        lateinit var cleanupTask: BukkitTask
        cleanupTask = BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                arrowTask.cancel()
                activeTasks.remove(arrowTask)
                activeTasks.remove(cleanupTask)
            },
            fallDurationTick + 1L
        )
        activeTasks += cleanupTask
    }

    private fun applyHit(player: Player, curseOfSun: CurseOfSun?) {
        player.damage(player.maxHealth * damageRatio, enemyData.entity)
        curseOfSun?.increaseGauge(player, curseGaugeIncrease)
        player.world.playSound(player.location, Sound.ENTITY_ARROW_HIT_PLAYER, SoundCategory.MASTER, 0.3f, 1.9f)
    }
}

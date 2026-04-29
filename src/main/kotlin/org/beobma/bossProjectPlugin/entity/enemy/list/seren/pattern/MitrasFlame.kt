package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.beobma.bossProjectPlugin.manager.PlayerStatusEffectManager
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class MitrasFlame : PatternSkill() {
    private val cycleIntervalTick = 20L * 12
    private val warningDurationTick = 35L // 약 1.77초

    private val warningCountPerCycle = 3
    private val warningSpawnRadius = 12.0
    private val warningAreaHalfWidth = 1.5

    private val centerX = 47.0
    private val centerY = -37.0
    private val centerZ = -76.0

    private val damageRatio = 0.35
    private val attackMissDurationMillis = 5_000L
    private val curseGaugeIncrease = 200

    private var cycleTask: BukkitTask? = null
    private val activeWarningTasks: MutableSet<BukkitTask> = mutableSetOf()

    override val name: String = "미트라의 불꽃"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 무작위 위치에 폭탄을 설치한다.",
        "<gray>일정 시간 후 폭발하며 피격 시 35%의 피해를 입고 빗나감 상태가 되며, 태양 게이지가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.FIRE_CHARGE)
    override val validPhases: Set<Int> = setOf(2)

    override fun canUse(): Boolean = true

    override fun onUse() {
        if (!isPatternAvailable()) {
            stopAllTasks()
            return
        }

        if (cycleTask != null) return

        cycleTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPatternAvailable()) {
                    stopAllTasks()
                    return@Runnable
                }

                repeat(warningCountPerCycle) {
                    spawnWarningAndScheduleExplosion()
                }
            },
            cycleIntervalTick, // 전투/페이즈 시작 직후에는 쿨타임을 적용한다.
            cycleIntervalTick
        )
    }

    override fun onGameEnd() {
        stopAllTasks()
    }

    private fun spawnWarningAndScheduleExplosion() {
        val world = enemyData.mapData.world()
        val warningCenter = randomPointNearMapCenter(world)

        var elapsed = 0L
        lateinit var warningTask: BukkitTask
        warningTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPatternAvailable()) {
                    warningTask.cancel()
                    activeWarningTasks.remove(warningTask)
                    return@Runnable
                }

                renderWarning(warningCenter)
                elapsed += 1L

                if (elapsed >= warningDurationTick) {
                    warningTask.cancel()
                    activeWarningTasks.remove(warningTask)
                }
            },
            0L,
            1L
        )
        activeWarningTasks += warningTask

        lateinit var explosionTask: BukkitTask
        explosionTask = BossProjectPlugin.instance.server.scheduler.runTaskLater(
            BossProjectPlugin.instance,
            Runnable {
                activeWarningTasks.remove(explosionTask)

                if (!isPatternAvailable()) {
                    return@Runnable
                }

                explode(warningCenter)
            },
            warningDurationTick
        )
        activeWarningTasks += explosionTask
    }

    private fun renderWarning(center: Location) {
        val world = center.world ?: return

        for (x in -1..1) {
            for (z in -1..1) {
                if (abs(x) != 1 && abs(z) != 1) continue
                val point = center.clone().add(x.toDouble(), 0.1, z.toDouble())
                world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.0)
            }
        }

        world.spawnParticle(Particle.FLAME, center.clone().add(0.0, 0.1, 0.0), 3, 0.15, 0.05, 0.15, 0.0)
        world.spawnParticle(Particle.SMOKE, center.clone().add(0.0, 0.1, 0.0), 2, 0.12, 0.02, 0.12, 0.0)
        world.playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.MASTER, 0.06f, 1.8f)
    }

    private fun explode(center: Location) {
        val world = center.world ?: return
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun

        world.spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0.0, 0.2, 0.0), 1)
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.MASTER, 0.85f, 1.2f)

        world.players
            .asSequence()
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .filter { player ->
                val location = player.location
                abs(location.x - center.x) <= warningAreaHalfWidth &&
                    abs(location.z - center.z) <= warningAreaHalfWidth &&
                    location.y in (centerY - 1.0)..(centerY + 4.0)
            }
            .forEach { player ->
                player.damage(player.maxHealth * damageRatio, enemyData.entity)
                PlayerStatusEffectManager.apply(
                    player.uniqueId,
                    PlayerStatusEffectManager.Effect.ATTACK_MISS,
                    attackMissDurationMillis
                )
                curseOfSun?.increaseGauge(player, curseGaugeIncrease)
            }
    }

    private fun randomPointNearMapCenter(world: org.bukkit.World): Location {
        val angle = Random.nextDouble(0.0, Math.PI * 2)
        val radius = Random.nextDouble(0.0, warningSpawnRadius)
        val x = centerX + cos(angle) * radius
        val z = centerZ + sin(angle) * radius
        return Location(world, x, centerY, z)
    }

    private fun isPatternAvailable(): Boolean {
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun ?: return false
        if (!isPhaseValid()) return false
        if (!curseOfSun.isCurrentPeriodNoon()) return false
        if (curseOfSun.isTimeChangePatternActive()) return false
        return true
    }

    private fun stopAllTasks() {
        cycleTask?.cancel()
        cycleTask = null

        activeWarningTasks.toList().forEach { task ->
            task.cancel()
        }
        activeWarningTasks.clear()
    }
}

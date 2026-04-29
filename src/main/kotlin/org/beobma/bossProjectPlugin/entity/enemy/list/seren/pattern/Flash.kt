package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Flash : PatternSkill() {
    private val cycleIntervalTick = 210L // 10.5s
    private val previewDurationTick = 40L // 2s
    private val firingDurationTick = 20L // 1s

    private val beamCount = 3
    private val beamLength = 90.0
    private val beamWidth = 1.8
    private val previewBeamWidth = 0.45
    private val beamStep = 0.35

    private val damageRatioPerTick = 0.1
    private val curseGaugePerTick = 100
    private val blindnessDurationTick = 20

    private val centerX = 47.0
    private val centerY = -35.57565
    private val centerZ = -76.0

    private var cycleTask: BukkitTask? = null
    private var previewTask: BukkitTask? = null
    private var firingTask: BukkitTask? = null

    override val name: String = "플래시"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 중심을 가로지르는 3갈래 태양빛이 대각선 교차 형태로 발사된다.",
        "<gray>피격당할 때마다 10%의 피해를 입고 태양 게이지가 증가하며, 1초 동안 실명 상태가 된다."
    )
    override val itemStack: ItemStack = ItemStack(Material.BLAZE_ROD)
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
                    stopActiveAttackTasks()
                    return@Runnable
                }
                startAttackCycle()
            },
            cycleIntervalTick,
            cycleIntervalTick
        )
    }

    override fun onGameEnd() {
        stopAllTasks()
    }

    private fun startAttackCycle() {
        stopActiveAttackTasks()

        val directions = buildBeamDirections(Random.nextDouble(0.0, PI))
        var previewTicks = 0L

        previewTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPatternAvailable()) {
                    stopActiveAttackTasks()
                    return@Runnable
                }

                renderBeams(directions, isPreview = true)
                previewTicks += 1

                if (previewTicks >= previewDurationTick) {
                    previewTask?.cancel()
                    previewTask = null
                    startFiring(directions)
                }
            },
            0L,
            1L
        )
    }

    private fun startFiring(directions: List<Vector>) {
        if (!isPatternAvailable()) {
            stopActiveAttackTasks()
            return
        }

        val world = enemyData.mapData.world()
        val center = beamCenter(world)
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun
        var firingTicks = 0L

        firingTask = BossProjectPlugin.instance.server.scheduler.runTaskTimer(
            BossProjectPlugin.instance,
            Runnable {
                if (!isPatternAvailable()) {
                    stopActiveAttackTasks()
                    return@Runnable
                }

                renderBeams(directions, isPreview = false)
                applyBeamHit(directions, center, curseOfSun)
                firingTicks += 1

                if (firingTicks >= firingDurationTick) {
                    firingTask?.cancel()
                    firingTask = null
                }
            },
            0L,
            1L
        )

        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.MASTER, 0.9f, 0.65f)
    }

    private fun applyBeamHit(directions: List<Vector>, center: Location, curseOfSun: CurseOfSun?) {
        val centerVector = center.toVector()

        center.world.players
            .asSequence()
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .filter { isHitByAnyBeam(it, centerVector, directions) }
            .forEach { player ->
                player.damage(player.maxHealth * damageRatioPerTick, enemyData.entity)
                curseOfSun?.increaseGauge(player, curseGaugePerTick)
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.BLINDNESS,
                        blindnessDurationTick,
                        0,
                        false,
                        false,
                        false
                    )
                )
            }
    }

    private fun isHitByAnyBeam(player: Player, center: Vector, directions: List<Vector>): Boolean {
        val point = player.location.toVector()
        if (abs(point.y - center.y) > 2.0) return false

        return directions.any { direction ->
            val relative = point.clone().subtract(center)
            val projection = relative.dot(direction)
            if (abs(projection) > beamLength / 2) return@any false

            val closest = center.clone().add(direction.clone().multiply(projection))
            val distance = point.clone().setY(0.0).distance(closest.clone().setY(0.0))
            distance <= beamWidth / 2
        }
    }

    private fun buildBeamDirections(baseAngle: Double): List<Vector> {
        return buildList {
            add(Vector(cos(baseAngle), 0.0, sin(baseAngle)).normalize())
            repeat(beamCount - 1) {
                val randomAngle = Random.nextDouble(0.0, PI)
                add(Vector(cos(randomAngle), 0.0, sin(randomAngle)).normalize())
            }
        }
    }

    private fun renderBeams(directions: List<Vector>, isPreview: Boolean) {
        val world = enemyData.mapData.world()
        val center = beamCenter(world)
        val width = if (isPreview) previewBeamWidth else beamWidth
        val particle = if (isPreview) {
            Particle.DUST
        } else {
            Particle.END_ROD
        }
        val dustOptions = Particle.DustOptions(Color.fromRGB(255, 218, 84), if (isPreview) 0.8f else 1.25f)

        directions.forEach { direction ->
            val perpendicular = Vector(-direction.z, 0.0, direction.x).normalize()
            var traveled = -beamLength / 2
            while (traveled <= beamLength / 2) {
                val basePoint = center.clone().add(direction.clone().multiply(traveled))

                var offset = -width / 2
                while (offset <= width / 2) {
                    val sample = basePoint.clone().add(perpendicular.clone().multiply(offset))
                    if (particle == Particle.DUST) {
                        world.spawnParticle(Particle.DUST, sample, 1, 0.0, 0.0, 0.0, 0.0, dustOptions, true)
                    } else {
                        world.spawnParticle(Particle.END_ROD, sample, 1, 0.0, 0.03, 0.0, 0.0, null, true)
                    }
                    offset += 0.3
                }

                traveled += beamStep
            }
        }

        val pitch = if (isPreview) 1.8f else 0.7f
        val volume = if (isPreview) 0.15f else 0.4f
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, volume, pitch)
    }

    private fun beamCenter(world: org.bukkit.World): Location = Location(world, centerX, centerY, centerZ)

    private fun isPatternAvailable(): Boolean {
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun ?: return false
        if (!isPhaseValid()) return false
        if (!curseOfSun.isCurrentPeriodNoon()) return false
        if (curseOfSun.isTimeChangePatternActive()) return false
        return true
    }

    private fun stopActiveAttackTasks() {
        previewTask?.cancel()
        previewTask = null
        firingTask?.cancel()
        firingTask = null
    }

    private fun stopAllTasks() {
        stopActiveAttackTasks()
        cycleTask?.cancel()
        cycleTask = null
    }
}

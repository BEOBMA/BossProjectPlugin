package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class SolarSwordAura : PatternSkill() {
    private val cooldownMillis = 9_500L
    private val travelTicks = 100
    private val hitRadius = 2.5
    private val hitHalfHeight = 3.2
    private val damageIntervalTicks = 10
    private val damagePercent = 0.2
    private val curseGaugeIncrease = 80

    private val minX = 32.0
    private val maxX = 61.0
    private val minZ = -91.0
    private val maxZ = -62.0
    private val slashY = -35.5

    private val random = Random(System.nanoTime())
    private var nextAvailableAtMillis = 0L

    override val name: String = "태양의 검기"
    override val description: List<String> = listOf(
        "<gray>맵 테두리에서 소환된 검기가 5초 동안 반대편 끝까지 나아간다.",
        "<gray>피격 시 0.5초마다 최대 체력의 20% 피해를 입고 태양의 저주 수치가 증가한다. 웅크리고 있으면 영향을 받지 않는다."
    )
    override val itemStack: ItemStack = ItemStack(Material.END_ROD)

    override fun inject(enemyData: EnemyData) {
        super.inject(enemyData)
        nextAvailableAtMillis = System.currentTimeMillis()
    }

    override fun canUse(): Boolean = System.currentTimeMillis() >= nextAvailableAtMillis

    override fun onUse() {
        nextAvailableAtMillis = System.currentTimeMillis() + cooldownMillis

        val world = enemyData.mapData.world()
        val target = game.playerDatas
            .map { it.player }
            .filter { it.isOnline }
            .randomOrNull(random) ?: return

        val spawn = randomEdgePoint()
        val direction = target.location.toVector().subtract(spawn.clone()).setY(0.0).normalizeSafe()
        if (direction.lengthSquared() <= 0.0) return

        val end = resolveEdgeIntersection(spawn, direction) ?: return
        val movement = end.clone().subtract(spawn)
        val step = movement.clone().multiply(1.0 / travelTicks.toDouble())

        world.playSound(
            org.bukkit.Location(world, spawn.x, slashY, spawn.z),
            Sound.ITEM_TRIDENT_THROW,
            SoundCategory.MASTER,
            0.9f,
            1.4f
        )

        val curseOfSun = enemyData.passives.filterIsInstance<CurseOfSun>().firstOrNull()
        val hitTracker = mutableMapOf<UUID, Int>()

        object : BukkitRunnable() {
            var tick = 0
            var current = spawn.clone()

            override fun run() {
                if (tick > travelTicks) {
                    cancel()
                    return
                }

                val center = org.bukkit.Location(world, current.x, slashY, current.z)
                spawnSwordParticles(center, direction, tick)
                applyHit(center, direction, tick, hitTracker, curseOfSun)

                current.add(step)
                tick += 1
            }
        }.runTaskTimer(BossProjectPlugin.instance, 0L, 1L)
    }

    private fun applyHit(
        center: org.bukkit.Location,
        direction: Vector,
        tick: Int,
        hitTracker: MutableMap<UUID, Int>,
        curseOfSun: CurseOfSun?
    ) {
        val axis = direction.clone().setY(0.0).normalizeSafe()
        if (axis.lengthSquared() <= 0.0) return

        center.world.players
            .asSequence()
            .filter { player ->
                if (!player.isOnline || player.isDead || player.isSneaking) return@filter false

                val relative = player.location.toVector().subtract(center.toVector())
                if (abs(relative.y) > hitHalfHeight) return@filter false

                val planar = relative.clone().setY(0.0)
                val forward = planar.dot(axis)
                if (forward !in -2.0..2.0) return@filter false

                val side = planar.subtract(axis.clone().multiply(forward)).length()
                side <= hitRadius
            }
            .forEach { player ->
                val lastHitTick = hitTracker[player.uniqueId] ?: Int.MIN_VALUE
                if (tick - lastHitTick < damageIntervalTicks) return@forEach

                hitTracker[player.uniqueId] = tick
                player.damage(player.maxHealth * damagePercent, enemyData.entity)
                curseOfSun?.increaseGauge(player, curseGaugeIncrease)
                player.world.playSound(player.location, Sound.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.MASTER, 0.8f, 1.2f)
            }
    }

    private fun spawnSwordParticles(center: org.bukkit.Location, direction: Vector, tick: Int) {
        val world = center.world
        val axis = direction.clone().setY(0.0).normalizeSafe()
        if (axis.lengthSquared() <= 0.0) return

        val side = axis.clone().crossProduct(Vector(0, 1, 0)).normalizeSafe()
        val bendProgress = tick.toDouble() / travelTicks.toDouble()
        val bend = max(0.0, 1.0 - (bendProgress - 0.5) * (bendProgress - 0.5) * 4.0)

        for (forwardOffset in -2..2) {
            val forward = axis.clone().multiply(forwardOffset * 0.4)

            for (verticalStep in -3..3) {
                val yOffset = verticalStep * 0.28
                val curve = side.clone().multiply((verticalStep * 0.22) * bend)
                val point = center.clone().add(forward).add(curve).add(0.0, yOffset, 0.0)
                world.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0)
            }
        }
    }

    private fun randomEdgePoint(): Vector {
        return when (random.nextInt(4)) {
            0 -> Vector(minX, 0.0, random.nextDouble(minZ, maxZ))
            1 -> Vector(maxX, 0.0, random.nextDouble(minZ, maxZ))
            2 -> Vector(random.nextDouble(minX, maxX), 0.0, minZ)
            else -> Vector(random.nextDouble(minX, maxX), 0.0, maxZ)
        }
    }

    private fun resolveEdgeIntersection(spawn: Vector, direction: Vector): Vector? {
        val txMin = ((minX - spawn.x) / direction.x).takeIf { direction.x < 0 }
        val txMax = ((maxX - spawn.x) / direction.x).takeIf { direction.x > 0 }
        val tzMin = ((minZ - spawn.z) / direction.z).takeIf { direction.z < 0 }
        val tzMax = ((maxZ - spawn.z) / direction.z).takeIf { direction.z > 0 }

        val candidates = listOfNotNull(txMin, txMax, tzMin, tzMax)
            .filter { it > 0.0 }
            .sorted()

        val t = candidates.firstOrNull { candidate ->
            val x = spawn.x + direction.x * candidate
            val z = spawn.z + direction.z * candidate
            x in (minX - 0.01)..(maxX + 0.01) && z in (minZ - 0.01)..(maxZ + 0.01)
        } ?: return null

        return spawn.clone().add(direction.clone().multiply(t))
    }

    private fun Vector.normalizeSafe(): Vector {
        if (lengthSquared() <= 1.0E-6) return Vector(0, 0, 0)
        return normalize()
    }
}

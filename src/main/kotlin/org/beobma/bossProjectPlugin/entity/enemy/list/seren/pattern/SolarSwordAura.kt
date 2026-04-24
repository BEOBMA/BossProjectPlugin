package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SolarSwordAura : PatternSkill() {
    private val cooldownMillis = 9_500L
    private val travelDurationTick = 100
    private val tickDamageIntervalMillis = 500L
    private val damagePerTickRatio = 0.2
    private val curseGaugeIncrease = 80
    private val effectHitRadius = 1.4
    private val worldY = -36.0

    private val minX = 32.0
    private val maxX = 61.0
    private val minZ = -91.0
    private val maxZ = -62.0

    private var lastUsedMillis: Long = Long.MIN_VALUE

    override val name: String = "태양의 검기"
    override val description: List<String> = listOf(
        "<gray>맵 테두리에서 소환된 검기가 5초 동안 반대편 끝까지 나아간다.",
        "<gray>피격 시 0.5초마다 최대 체력의 20% 피해를 입고 태양의 저주 수치가 증가한다. 웅크리고 있으면 영향을 받지 않는다."
    )
    override val itemStack: ItemStack = ItemStack(Material.GOLDEN_SWORD)

    override fun inject(enemyData: EnemyData) {
        super.inject(enemyData)
        lastUsedMillis = System.currentTimeMillis() - cooldownMillis
    }

    override fun canUse(): Boolean = System.currentTimeMillis() - lastUsedMillis >= cooldownMillis

    override fun onUse() {
        val world = enemyData.mapData.world()
        val players = game.playerDatas.map { it.player }.filter { it.isOnline }
        if (players.isEmpty()) return

        val targetPlayer = players.random()
        val start = randomEdgeSpawn(targetPlayer)
        val directionToTarget = targetPlayer.location.toVector().setY(worldY).subtract(start.toVector()).normalize()
        if (directionToTarget.lengthSquared() == 0.0) return

        val end = findMapBoundaryIntersection(start.toVector(), directionToTarget) ?: return
        val travelVector = end.clone().subtract(start.toVector())
        if (travelVector.lengthSquared() == 0.0) return

        lastUsedMillis = System.currentTimeMillis()
        world.playSound(start, Sound.ITEM_TRIDENT_THROW, SoundCategory.MASTER, 0.8f, 1.25f)

        val normal = Vector(-travelVector.z, 0.0, travelVector.x).normalize()
        val hitCooldownByPlayer = mutableMapOf<java.util.UUID, Long>()

        object : BukkitRunnable() {
            var livedTick = 0

            override fun run() {
                if (livedTick > travelDurationTick) {
                    cancel()
                    return
                }

                val progress = livedTick.toDouble() / travelDurationTick.toDouble()
                val basePosition = start.toVector().add(travelVector.clone().multiply(progress))
                val bend = sin(progress * PI) * 3.0
                val center = basePosition.clone().add(normal.clone().multiply(bend))

                spawnBladeParticles(world, center, normal)
                applyDamage(world.players, center, hitCooldownByPlayer)

                livedTick++
            }
        }.runTaskTimer(BossProjectPlugin.instance, 0L, 1L)
    }

    private fun applyDamage(
        worldPlayers: List<Player>,
        center: Vector,
        hitCooldownByPlayer: MutableMap<java.util.UUID, Long>
    ) {
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun ?: return

        worldPlayers.forEach { player ->
            if (!player.isOnline) return@forEach
            if (player.isSneaking) return@forEach

            val playerPos = player.location.toVector()
            if (abs(playerPos.y - center.y) > 2.0) return@forEach
            if (playerPos.distanceSquared(center) > effectHitRadius * effectHitRadius) return@forEach

            val nowMillis = System.currentTimeMillis()
            val lastHitMillis = hitCooldownByPlayer[player.uniqueId] ?: Long.MIN_VALUE
            if (nowMillis - lastHitMillis < tickDamageIntervalMillis) return@forEach

            hitCooldownByPlayer[player.uniqueId] = nowMillis
            player.damage(player.maxHealth * damagePerTickRatio, enemyData.entity)
            curseOfSun.increaseGauge(player, curseGaugeIncrease)
            player.world.playSound(player.location, Sound.ITEM_TRIDENT_HIT, SoundCategory.MASTER, 0.35f, 1.6f)
        }
    }

    private fun spawnBladeParticles(world: org.bukkit.World, center: Vector, normal: Vector) {
        val dust = Particle.DustOptions(Color.fromRGB(255, 227, 82), 1.5f)
        for (i in -3..3) {
            val offset = normal.clone().multiply(i * 0.33)
            val arcY = cos((i / 3.0) * (PI / 2.0)) * 0.8
            world.spawnParticle(
                Particle.DUST,
                center.x + offset.x,
                center.y + arcY,
                center.z + offset.z,
                1,
                0.0,
                0.0,
                0.0,
                0.0,
                dust
            )
        }
    }

    private fun randomEdgeSpawn(targetPlayer: Player): org.bukkit.Location {
        val world = enemyData.mapData.world()
        return when (Random.nextInt(4)) {
            0 -> org.bukkit.Location(world, minX, worldY, Random.nextDouble(minZ, maxZ + 1.0))
            1 -> org.bukkit.Location(world, maxX, worldY, Random.nextDouble(minZ, maxZ + 1.0))
            2 -> org.bukkit.Location(world, Random.nextDouble(minX, maxX + 1.0), worldY, minZ)
            else -> org.bukkit.Location(world, Random.nextDouble(minX, maxX + 1.0), worldY, maxZ)
        }.apply {
            val toTarget = targetPlayer.location.toVector().setY(worldY).subtract(toVector())
            direction = toTarget.normalize()
        }
    }

    private fun findMapBoundaryIntersection(start: Vector, direction: Vector): Vector? {
        val candidates = mutableListOf<Double>()

        if (direction.x != 0.0) {
            candidates += (minX - start.x) / direction.x
            candidates += (maxX - start.x) / direction.x
        }
        if (direction.z != 0.0) {
            candidates += (minZ - start.z) / direction.z
            candidates += (maxZ - start.z) / direction.z
        }

        return candidates
            .asSequence()
            .filter { it > 0.0 }
            .map { t -> start.clone().add(direction.clone().multiply(t)) }
            .filter { point ->
                point.x in (minX - 0.01)..(maxX + 0.01) &&
                        point.z in (minZ - 0.01)..(maxZ + 0.01)
            }
            .minByOrNull { start.distanceSquared(it) }
            ?.setY(worldY)
    }
}

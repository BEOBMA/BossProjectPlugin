package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.manager.PlayerDeathLifecycleManager
import org.beobma.bossProjectPlugin.manager.PlayerStatusEffectManager
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class JudgmentLight : PatternSkill(), Listener {
    private val cooldownTick = 20L * 15
    private val previewDelayTick = 20L * 9 / 5 // 1.8 seconds
    private val rayCount = 8
    private val rayLength = 80.0
    private val rayStep = 0.2
    private val rayWidth = 0.5
    private val previewRayWidth = 0.1
    private val damageRatio = 0.5
    private val curseGaugeIncrease = 150
    private val attackMissDurationMillis = 5_000L

    private var listenerRegistered = false

    override val name: String = "심판의 빛"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 8갈래의 심판의 빛을 발사한다.",
        "<gray>피격 시 50%의 피해를 입고, 일정 시간동안 빗나감 상태이상에 빠지며 태양의 저주 수치가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.END_ROD)
    override val validPhases: Set<Int> = setOf(1)

    override fun inject(enemyData: org.beobma.bossProjectPlugin.entity.enemy.EnemyData) {
        super.inject(enemyData)
        if (listenerRegistered) return
        Bukkit.getPluginManager().registerEvents(this, BossProjectPlugin.instance)
        listenerRegistered = true
    }

    override fun canUse(): Boolean = canUseOnCooldown(cooldownTick)

    override fun onUse() {
        markUsedNow()

        val baseAngle = kotlin.random.Random.nextDouble(0.0, PI * 2)
        val directions = buildDirections(baseAngle)

        renderRays(directions, isPreview = true)
        BossProjectPlugin.instance.server.scheduler.runTaskLater(BossProjectPlugin.instance, Runnable {
            renderRays(directions, isPreview = false)
            applyHitEffects(directions)
        }, previewDelayTick)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerAttack(event: EntityDamageByEntityEvent) {
        if (!isPhaseValid()) return
        val attacker = event.damager as? Player
        val shooter = (event.damager as? org.bukkit.entity.Projectile)?.shooter as? Player
        val player = attacker ?: shooter ?: return

        if (!PlayerStatusEffectManager.isActive(player.uniqueId, PlayerStatusEffectManager.Effect.ATTACK_MISS)) return
        event.isCancelled = true
    }

    private fun buildDirections(baseAngle: Double): List<Vector> {
        return (0 until rayCount).map { index ->
            val angle = baseAngle + (2 * PI / rayCount) * index
            Vector(cos(angle), 0.0, sin(angle)).normalize()
        }
    }

    private fun renderRays(directions: List<Vector>, isPreview: Boolean) {
        val world = enemyData.mapData.world()
        val center = enemyData.entity.location.clone().apply {
            x = 47.0
            y = -35.57565
            z = -76.0
        }

        val density = 0.55
        val offsetStep = 0.25
        val renderRayWidth = if (isPreview) previewRayWidth else rayWidth

        directions.forEach { direction ->
            val side = Vector(-direction.z, 0.0, direction.x).normalize()
            var traveled = 0.0
            while (traveled <= rayLength) {
                val point = center.clone().add(direction.clone().multiply(traveled))

                var sideOffset = -renderRayWidth / 2
                while (sideOffset <= renderRayWidth / 2) {
                    var yOffset = -renderRayWidth / 2
                    while (yOffset <= renderRayWidth / 2) {
                        val sample = point.clone()
                            .add(side.clone().multiply(sideOffset))
                            .add(0.0, yOffset, 0.0)
                        world.spawnParticle(
                            Particle.END_ROD,
                            sample,
                            1,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            null,
                            true
                        )
                        yOffset += offsetStep
                    }
                    sideOffset += offsetStep
                }
                traveled += rayStep / density
            }
        }

        val pitch = if (isPreview) 1.6f else 0.9f
        val volume = if (isPreview) 0.3f else 0.7f
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.MASTER, volume, pitch)
    }

    private fun applyHitEffects(directions: List<Vector>) {
        val world = enemyData.mapData.world()
        val center = enemyData.entity.location.clone().apply {
            x = 47.0
            y = -35.57565
            z = -76.0
        }
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun

        world.players
            .filter { PlayerDeathLifecycleManager.canBeTargetedByPattern(it) }
            .filter { isHitByAnyRay(it, center.toVector(), directions) }
            .forEach { player ->
                player.damage(player.maxHealth * damageRatio, enemyData.entity)
                curseOfSun?.increaseGauge(player, curseGaugeIncrease)
                PlayerStatusEffectManager.apply(player.uniqueId, PlayerStatusEffectManager.Effect.ATTACK_MISS, attackMissDurationMillis)
                player.world.playSound(player.location, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.MASTER, 0.4f, 0.7f)
            }

        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.MASTER, 0.7f, 1.4f)
    }

    private fun isHitByAnyRay(player: Player, center: Vector, directions: List<Vector>): Boolean {
        val target = player.location.toVector()
        if (abs(target.y - center.y) > 2.0) return false

        return directions.any { direction ->
            val relative = target.clone().subtract(center)
            val projection = relative.dot(direction)
            if (projection < 0.0 || projection > rayLength) return@any false

            val closest = center.clone().add(direction.clone().multiply(projection))
            val horizontalDistance = target.clone().setY(0).distance(closest.clone().setY(0))
            horizontalDistance <= rayWidth / 2 + 0.15
        }
    }

}

package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.BossProjectPlugin
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class WrathOfSun : PatternSkill() {
    private val cooldownTick = 20L * 5
    private val strikeDelayTick = 30L
    private val strikeGapTick = 8L
    private val strikesPerCycle = 3
    private val curseGaugeIncrease = 120
    private val floorStates: MutableList<FloorState> = mutableListOf()

    override val name: String = "태양의 분노"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 맵 곳곳의 바닥에서 빛의 검을 소환한다.",
        "<gray>피격 시 40%의 피해를 입고 태양의 저주 수치가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.BLAZE_POWDER)

    override fun inject(enemyData: EnemyData) {
        super.inject(enemyData)

        if (floorStates.isNotEmpty()) return

        val random = Random(System.nanoTime())
        buildFloorAreas().forEach { area ->
            floorStates += FloorState(
                area = area,
                nextAvailableTick = random.nextLong(0L, cooldownTick + 1)
            )
        }
    }

    override fun canUse(): Boolean = true

    override fun onUse() {
        val nowTick = enemyStatus.elapsedTicks
        val readyFloors = floorStates.filter { nowTick >= it.nextAvailableTick }
        if (readyFloors.isEmpty()) return

        val picked = readyFloors.shuffled().take(min(strikesPerCycle, readyFloors.size))
        picked.forEachIndexed { index, floorState ->
            floorState.nextAvailableTick = nowTick + cooldownTick

            val delay = strikeDelayTick + (index * strikeGapTick)
            BossProjectPlugin.instance.server.scheduler.runTaskLater(BossProjectPlugin.instance, Runnable {
                triggerStrike(floorState.area)
            }, delay)
        }
    }

    private fun triggerStrike(area: FloorArea) {
        val world = enemyData.mapData.world()
        if (world.uid != area.worldUid) return

        world.players
            .asSequence()
            .filter { player ->
                val block = player.location.block
                block.x in area.minX..area.maxX &&
                        block.z in area.minZ..area.maxZ &&
                        block.y in area.floorY..(area.floorY + 2)
            }
            .forEach { player ->
                player.damage(player.maxHealth * 0.4, enemyData.entity)

                val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun ?: return@forEach
                curseOfSun.increaseGauge(player, curseGaugeIncrease)
            }
    }

    private fun buildFloorAreas(): List<FloorArea> {
        val world = enemyData.mapData.world()
        val floorY = -37

        val startX = 61
        val endX = 32
        val startZ = -62
        val endZ = -91

        val areas = mutableListOf<FloorArea>()

        var x = startX
        while (x >= endX) {
            var z = startZ
            while (z >= endZ) {
                val cellMinX = max(min(startX, endX), x - 4)
                val cellMaxX = min(max(startX, endX), x)
                val cellMinZ = max(min(startZ, endZ), z - 4)
                val cellMaxZ = min(max(startZ, endZ), z)

                areas += FloorArea(
                    worldUid = world.uid,
                    minX = min(cellMinX, cellMaxX),
                    maxX = max(cellMinX, cellMaxX),
                    minZ = min(cellMinZ, cellMaxZ),
                    maxZ = max(cellMinZ, cellMaxZ),
                    floorY = floorY
                )

                z -= 5
            }
            x -= 5
        }

        return areas
    }

    private data class FloorState(
        val area: FloorArea,
        var nextAvailableTick: Long
    )

    private data class FloorArea(
        val worldUid: java.util.UUID,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
        val floorY: Int
    )
}

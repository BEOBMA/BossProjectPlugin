package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.ItemStack

class WrathOfSun : PatternSkill() {
    private val cooldownTick = 20L * 5
    private val curseGaugeIncrease = 120

    override val name: String = "태양의 분노"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 맵 곳곳의 바닥에서 빛의 검을 소환한다.",
        "<gray>피격 시 40%의 피해를 입고 태양의 저주 수치가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.BLAZE_POWDER)

    override fun canUse(): Boolean = canUseOnCooldown(cooldownTick)

    override fun onUse() {
        markUsedNow()
    }

    override fun onPlayerHit(player: Player, event: EntityDamageByEntityEvent) {
        event.damage = player.maxHealth * 0.4
        val curseOfSun = enemyData.passives.firstOrNull { it is CurseOfSun } as? CurseOfSun ?: return
        curseOfSun.increaseGauge(player, curseGaugeIncrease)
    }
}

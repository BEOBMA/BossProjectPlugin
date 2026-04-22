package org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern

import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class WrathOfSun : PatternSkill() {
    override val name: String = "태양의 분노"
    override val description: List<String> = listOf(
        "<gray>일정 시간마다 맵 곳곳의 바닥에서 빛의 검을 소환한다.",
        "<gray>피격 시 40%의 피해를 입고 태양의 저주 수치가 증가한다."
    )
    override val itemStack: ItemStack = ItemStack(Material.BLAZE_POWDER)

    override fun canUse(): Boolean = true
}

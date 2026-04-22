package org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive

import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class CurseOfSun : BossPassive() {
    override val name: String = "태양의 저주"
    override val description: List<String> = listOf(
        "<gray>세렌의 일부 패턴에 피격당할 경우 게이지가 증가한다.",
        "<gray>게이지가 가득 차면 5초간 행동 불가 및 회복 불가 상태가 된다."
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)
}
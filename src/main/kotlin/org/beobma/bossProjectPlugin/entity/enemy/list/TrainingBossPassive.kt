package org.beobma.bossProjectPlugin.entity.enemy.list

import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class TrainingBossPassive : BossPassive() {
    override val name: String = "<gold>기믹 게이지</gold>"
    override val description: List<String> = listOf(
        "<gray>초기 게이지는 1000입니다.</gray>",
        "<gray>게이지가 0이 되면 보스전에서 패배합니다.</gray>"
    )
    override val itemStack: ItemStack = ItemStack(Material.TOTEM_OF_UNDYING)
    override val functionId: String = "bossproject:training_boss/passive"
}

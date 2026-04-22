package org.beobma.bossProjectPlugin.entity.enemy.list

import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class TrainingBossPatternSkill : PatternSkill() {
    override val name: String = "<red>충격파 패턴</red>"
    override val description: List<String> = listOf(
        "<gray>플레이어가 보스 근처에 모이면 사용합니다.</gray>",
        "<gray>데이터팩 function으로 파티클/사운드를 재생할 수 있습니다.</gray>"
    )
    override val itemStack: ItemStack = ItemStack(Material.BLAZE_POWDER)
    override val functionId: String = "bossproject:training_boss/pattern/shockwave"

    override fun canUse(): Boolean = true
}

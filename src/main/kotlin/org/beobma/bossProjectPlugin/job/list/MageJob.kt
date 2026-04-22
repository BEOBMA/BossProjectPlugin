package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class MageJob : Job() {
    override val name: String = "<light_purple><bold>마법사</bold></light_purple>"
    override val description: List<String> = listOf(
        "<gray>강력한 마법으로 전장을 제어합니다.</gray>",
        "<dark_gray>기본 직업(시범용)</dark_gray>"
    )
    override val classItem: ItemStack = ItemStack(Material.BLAZE_ROD)
    override val weapon: Weapon = BasicWeapon(Material.BLAZE_ROD)
    override val skills: List<Skill> = listOf(NoOpSkill())
    override var passives: List<Passive> = listOf(NoOpPassive())
}

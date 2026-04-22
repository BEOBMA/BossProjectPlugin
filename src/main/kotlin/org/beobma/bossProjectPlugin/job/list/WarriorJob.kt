package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class WarriorJob : Job() {
    override val name: String = "<red><bold>전사</bold></red>"
    override val description: List<String> = listOf(
        "<gray>근접 전투에 특화된 직업입니다.</gray>",
        "<dark_gray>기본 직업(시범용)</dark_gray>"
    )
    override val classItem: ItemStack = ItemStack(Material.IRON_SWORD)
    override val weapon: Weapon = BasicWeapon(Material.IRON_SWORD)
    override val skills: List<Skill> = listOf(NoOpSkill())
    override var passives: List<Passive> = listOf(NoOpPassive())
}

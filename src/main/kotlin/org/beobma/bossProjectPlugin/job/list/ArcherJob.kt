package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class ArcherJob : Job() {
    override val name: String = "<green><bold>궁수</bold></green>"
    override val description: List<String> = listOf(
        "<gray>원거리 견제에 유리한 직업입니다.</gray>",
        "<dark_gray>기본 직업(시범용)</dark_gray>"
    )
    override val classItem: ItemStack = ItemStack(Material.BOW)
    override val weapon: Weapon = BasicWeapon(Material.BOW)
    override val skills: List<Skill> = listOf(NoOpSkill())
    override var passives: List<Passive> = listOf(NoOpPassive())
}

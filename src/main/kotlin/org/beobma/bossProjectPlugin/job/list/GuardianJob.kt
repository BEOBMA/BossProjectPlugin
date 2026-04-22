package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class GuardianJob : Job() {
    override val name: String = "<gold><bold>수호자</bold></gold>"
    override val description: List<String> = listOf(
        "<gray>높은 생존력으로 아군을 지켜냅니다.</gray>",
        "<dark_gray>기본 직업(시범용)</dark_gray>"
    )
    override val classItem: ItemStack = ItemStack(Material.SHIELD)
    override val weapon: Weapon = BasicWeapon(Material.SHIELD)
    override val skills: List<Skill> = listOf(NoOpSkill())
    override var passives: List<Passive> = listOf(NoOpPassive())
}

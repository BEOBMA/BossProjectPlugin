package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class BasicWeapon(material: Material) : Weapon() {
    override val itemStack: ItemStack = ItemStack(material)
}

class NoOpSkill : Skill() {
    override val name: String = "<gray>스킬 미구현</gray>"
    override val description: List<String> = listOf("<dark_gray>추후 구현 예정</dark_gray>")
    override val itemStack: ItemStack = ItemStack(Material.GRAY_DYE)
    override val cooldown: Long? = null
    override val delay: Long? = null

    override fun use() {
        // no-op
    }
}

class NoOpPassive : Passive() {
    override val name: String = "<gray>패시브 미구현</gray>"
    override val description: List<String> = listOf("<dark_gray>추후 구현 예정</dark_gray>")
    override val itemStack: ItemStack = ItemStack(Material.GRAY_DYE)
}

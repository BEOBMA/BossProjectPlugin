package org.beobma.bossProjectPlugin.job.list

import org.beobma.bossProjectPlugin.job.Job
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class TestOnlyJob : Job() {
    override val name: String = "<yellow>테스트 전용 직업</yellow>"
    override val description: List<String> = listOf(
        "<gray>테스트/검증용으로만 사용하세요.</gray>",
        "<gray>전투 스킬/패시브가 없는 최소 직업입니다.</gray>"
    )
    override val classItem: ItemStack = ItemStack(Material.BOOK)
    override val weapon: Weapon = object : Weapon() {
        override val itemStack: ItemStack = ItemStack(Material.WOODEN_SWORD)
    }
    override val skills: List<Skill> = emptyList()
    override var passives: List<Passive> = emptyList()
}
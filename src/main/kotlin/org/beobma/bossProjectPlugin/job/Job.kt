package org.beobma.bossProjectPlugin.job

import net.kyori.adventure.text.minimessage.MiniMessage
import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.entity.player.PlayerStatus
import org.beobma.bossProjectPlugin.game.Game
import org.beobma.bossProjectPlugin.job.skill.Passive
import org.beobma.bossProjectPlugin.job.skill.Skill
import org.beobma.bossProjectPlugin.job.weapon.Weapon
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

abstract class Job {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val classItem: ItemStack
    abstract val weapon: Weapon
    abstract val skills: List<Skill>
    abstract var passives: List<Passive>
    open val extraItemMaterials: List<ItemStack> = listOf()

    fun inject(playerData: PlayerData) {
        if (playerData.status !is PlayerStatus) return

        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.status
        this.game = playerData.initGame
    }

    fun toItem(): ItemStack {
        val item = classItem.clone()
        val meta = item.itemMeta ?: return item
        val miniMessage = MiniMessage.miniMessage()

        meta.displayName(miniMessage.deserialize(name))
        meta.lore(description.map(miniMessage::deserialize))
        item.itemMeta = meta

        return item
    }
}

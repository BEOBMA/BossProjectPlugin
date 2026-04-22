package org.beobma.bossProjectPlugin.job.skill

import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.entity.player.PlayerStatus
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

abstract class Skill {
    protected lateinit var playerData: PlayerData
    protected lateinit var player: Player
    protected lateinit var playerStatus: PlayerStatus
    protected lateinit var game: Game

    abstract val name: String
    abstract val description: List<String>
    abstract val itemStack: ItemStack
    abstract val cooldown: Long?
    abstract val delay: Long?

    abstract fun use()
    open fun isUseSuccess(): Boolean = true

    fun inject(playerData: PlayerData) {
        if (playerData.status !is PlayerStatus) return
        this.playerData = playerData
        this.player = playerData.player
        this.playerStatus = playerData.status
        this.game = playerData.initGame
    }
}
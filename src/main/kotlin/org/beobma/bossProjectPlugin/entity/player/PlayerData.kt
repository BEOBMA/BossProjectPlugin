package org.beobma.bossProjectPlugin.entity.player

import org.beobma.bossProjectPlugin.entity.EntityData
import org.beobma.bossProjectPlugin.entity.EntityStatus
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.entity.Entity
import org.bukkit.entity.Player

class PlayerData(
    val player: Player,
    val initGame: Game
) : EntityData() {
    override val entity: Entity = player
    override val game: Game = initGame
    override val status: EntityStatus = PlayerStatus()
}
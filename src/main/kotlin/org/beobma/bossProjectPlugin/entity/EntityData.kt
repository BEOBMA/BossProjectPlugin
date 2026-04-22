package org.beobma.bossProjectPlugin.entity

import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.entity.Entity

abstract class EntityData {
    abstract val entity: Entity
    abstract val game: Game
    abstract val status: EntityStatus
}
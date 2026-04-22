package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.bukkit.attribute.Attribute

object PlayerManager {

    fun PlayerData.getMaxHealth(): Double {
        return player.getAttribute(Attribute.MAX_HEALTH)?.baseValue ?: 0.0
    }
}
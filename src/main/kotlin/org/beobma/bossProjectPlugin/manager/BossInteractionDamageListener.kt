package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent

object BossInteractionDamageListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBossInteractionDamaged(event: EntityDamageByEntityEvent) {
        if (!event.entity.scoreboardTags.contains(BossCombatConstants.BOSS_INTERACTION_TAG)) {
            return
        }

        GameManager.applyBossInteractionDamage(
            attacker = event.damager,
            damaged = event.entity,
            finalDamage = event.finalDamage
        )
    }
}

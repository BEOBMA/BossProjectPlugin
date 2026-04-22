package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent

object BossInteractionDamageListener : Listener {
    @EventHandler
    fun onBossInteractionDamaged(event: EntityDamageByEntityEvent) {
        if (!event.entity.scoreboardTags.contains(BossCombatConstants.BOSS_INTERACTION_TAG)) {
            return
        }

        val finalDamage = event.finalDamage
        val cause = event.cause

        event.entity.customName = "boss_interaction: ${"%.2f".format(finalDamage)} (${cause.name})"
    }

    @EventHandler
    fun onGenericBossInteractionDamage(event: EntityDamageEvent) {
        if (!event.entity.scoreboardTags.contains(BossCombatConstants.BOSS_INTERACTION_TAG)) {
            return
        }

        event.entity.customName = "boss_interaction: ${"%.2f".format(event.finalDamage)}"
    }
}

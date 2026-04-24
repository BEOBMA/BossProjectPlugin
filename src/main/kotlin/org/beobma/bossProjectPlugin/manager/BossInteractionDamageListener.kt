package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.bukkit.Sound
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.entity.Player

object BossInteractionDamageListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBossInteractionDamaged(event: EntityDamageByEntityEvent) {
        if (!event.entity.scoreboardTags.contains(BossCombatConstants.BOSS_INTERACTION_TAG)) {
            return
        }

        if (event.damager is Player) {
            event.entity.world.playSound(event.entity.location, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.0f)
        }

        GameManager.applyBossInteractionDamage(
            attacker = event.damager,
            damaged = event.entity,
            damageAmount = event.damage
        )
    }
}

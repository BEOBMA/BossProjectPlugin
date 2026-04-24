package org.beobma.bossProjectPlugin.manager

import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractArrow
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
            damageAmount = resolveDamage(event)
        )
    }

    private fun resolveDamage(event: EntityDamageByEntityEvent): Double {
        val damager = event.damager
        if (damager is Player) {
            val attackDamage = damager.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0
            return maxOf(event.finalDamage, attackDamage)
        }

        if (damager is AbstractArrow) {
            return maxOf(event.finalDamage, damager.damage)
        }

        return event.finalDamage
    }
}

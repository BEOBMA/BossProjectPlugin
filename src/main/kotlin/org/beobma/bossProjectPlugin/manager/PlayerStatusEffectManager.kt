package org.beobma.bossProjectPlugin.manager

import java.util.UUID

object PlayerStatusEffectManager {
    enum class Effect {
        ATTACK_MISS,
        ACTION_RESTRICTED
    }

    private val activeUntilByPlayer: MutableMap<UUID, MutableMap<Effect, Long>> = mutableMapOf()

    fun apply(uuid: UUID, effect: Effect, durationMillis: Long) {
        if (durationMillis <= 0L) {
            clear(uuid, effect)
            return
        }

        val activeUntil = System.currentTimeMillis() + durationMillis
        val effects = activeUntilByPlayer.getOrPut(uuid) { mutableMapOf() }
        effects[effect] = activeUntil
    }

    fun isActive(uuid: UUID, effect: Effect): Boolean {
        val effects = activeUntilByPlayer[uuid] ?: return false
        val activeUntil = effects[effect] ?: return false

        if (System.currentTimeMillis() <= activeUntil) {
            return true
        }

        effects.remove(effect)
        if (effects.isEmpty()) {
            activeUntilByPlayer.remove(uuid)
        }
        return false
    }

    fun clear(uuid: UUID, effect: Effect) {
        val effects = activeUntilByPlayer[uuid] ?: return
        effects.remove(effect)
        if (effects.isEmpty()) {
            activeUntilByPlayer.remove(uuid)
        }
    }

    fun clearAllStates() {
        activeUntilByPlayer.clear()
    }
}

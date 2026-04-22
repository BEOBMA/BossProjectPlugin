package org.beobma.bossProjectPlugin.entity.enemy

import org.beobma.bossProjectPlugin.entity.EntityData

abstract class EnemyData : EntityData() {
    abstract val maxHealth: Double
    abstract var health: Double
}
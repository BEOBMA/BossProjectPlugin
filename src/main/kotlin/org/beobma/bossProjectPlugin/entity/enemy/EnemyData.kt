package org.beobma.bossProjectPlugin.entity.enemy

import org.beobma.bossProjectPlugin.entity.EntityData
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill

abstract class EnemyData : EntityData() {
    abstract val maxHealth: Double
    abstract var health: Double
    open val displayName: String
        get() = this::class.simpleName ?: "Unknown Boss"

    abstract val passives: List<BossPassive>
    abstract val patternSkills: List<PatternSkill>
    abstract val mapData: BossBattleMapData

    open val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG
}

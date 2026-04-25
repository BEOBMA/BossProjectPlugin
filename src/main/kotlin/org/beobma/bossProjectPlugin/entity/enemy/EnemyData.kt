package org.beobma.bossProjectPlugin.entity.enemy

import org.beobma.bossProjectPlugin.entity.EntityData
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.bukkit.Bukkit
import org.bukkit.entity.Entity

abstract class EnemyData : EntityData() {
    abstract val maxHealth: Double
    abstract var health: Double
    open val phase: Int = 1
    open val displayName: String
        get() = this::class.simpleName ?: "Unknown Boss"

    abstract val passives: List<BossPassive>
    abstract val patternSkills: List<PatternSkill>
    abstract val mapData: BossBattleMapData

    open val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG
    open val interactionSummonCommand: String? = null
    open fun createNextPhase(): EnemyData? = null

    protected fun resolveBossEntity(): Entity {
        val world = mapData.world()
        interactionSummonCommand?.let { summonCommand ->
            world.entities
                .filter { it.scoreboardTags.contains(interactionTag) }
                .forEach { it.remove() }
            val normalizedCommand = summonCommand.removePrefix("/")
            Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "execute in ${world.key.asString()} run $normalizedCommand"
            )
        }

        return world.entities.firstOrNull { it.scoreboardTags.contains(interactionTag) }
            ?: error("보스 엔티티 태그 '$interactionTag' 를 가진 엔티티를 월드 '${world.name}' 에서 찾지 못했습니다.")
    }
}

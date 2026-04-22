package org.beobma.bossProjectPlugin.entity.enemy.list.seren

import org.beobma.bossProjectPlugin.entity.EntityStatus
import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.beobma.bossProjectPlugin.entity.enemy.DeathCountMode
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern.WrathOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.entity.Entity

class ChosenSerenData(
    private val initGame: Game
) : EnemyData() {
    companion object {
        val MAP_DATA = BossBattleMapData(
            id = "chosen_seren_arena",
            worldName = "world",
            spawnX = 61.5,
            spawnY = -37.0,
            spawnZ = -62.5,
            deathCountMode = DeathCountMode.PER_PLAYER,
            deathLimit = 8,
            timeLimitMinutes = 30
        )
    }

    override val game: Game = initGame
    override val status: EntityStatus = ChosenSerenStatus()

    override val maxHealth: Double = 2000.0
    override var health: Double = maxHealth

    override val passives: List<BossPassive> = listOf(CurseOfSun())
    override val patternSkills: List<PatternSkill> = listOf(WrathOfSun())
    override val mapData: BossBattleMapData = MAP_DATA

    override val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG

    override val entity: Entity = findExistingBossEntity()

    init {
        passives.forEach { it.inject(this) }
        patternSkills.forEach { it.inject(this) }
    }

    private fun findExistingBossEntity(): Entity {
        val world = mapData.world()
        return world.entities.firstOrNull { it.scoreboardTags.contains(interactionTag) }
            ?: error("보스 엔티티 태그 '$interactionTag' 를 가진 엔티티를 월드 '${world.name}' 에서 찾지 못했습니다.")
    }
}
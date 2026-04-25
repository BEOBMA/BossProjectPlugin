package org.beobma.bossProjectPlugin.entity.enemy.list.seren

import org.beobma.bossProjectPlugin.entity.EntityStatus
import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.beobma.bossProjectPlugin.entity.enemy.DeathCountMode
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.passive.CurseOfSun
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern.JudgmentLight
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern.LightArrow
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.pattern.WrathOfSun
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.game.Game

class ChosenSerenData(
    private val initGame: Game,
    override val phase: Int = 1
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

    override val maxHealth: Double = if (phase == 1) 2000.0 else 2600.0
    override var health: Double = maxHealth
    override val displayName: String = "선택받은 세렌"

    override val passives: List<BossPassive> = listOf(CurseOfSun())
    override val patternSkills: List<PatternSkill> = listOf(
        WrathOfSun(),
        JudgmentLight(),
        LightArrow()
    )
    override val mapData: BossBattleMapData = MAP_DATA

    override val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG
    override val interactionSummonCommand: String =
        "/summon interaction 47.0 -35.57565 -76.0 {width:2f,height:3f,Tags:[\"boss_interaction\"]}"
    override fun createNextPhase(): EnemyData? {
        if (phase >= 2) return null
        return ChosenSerenData(initGame, phase + 1)
    }

    override val entity = resolveBossEntity()

    init {
        passives.forEach { it.inject(this) }
        patternSkills.forEach { it.inject(this) }
    }
}

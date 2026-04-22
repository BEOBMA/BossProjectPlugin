package org.beobma.bossProjectPlugin.entity.enemy.list

import org.beobma.bossProjectPlugin.entity.EntityStatus
import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import org.beobma.bossProjectPlugin.entity.enemy.BossCombatConstants
import org.beobma.bossProjectPlugin.entity.enemy.DeathCountMode
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.skill.BossPassive
import org.beobma.bossProjectPlugin.entity.enemy.skill.PatternSkill
import org.beobma.bossProjectPlugin.game.Game
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Interaction

class TrainingBossData(
    private val initGame: Game,
    spawnLocation: Location
) : EnemyData() {
    companion object {
        val MAP_DATA = BossBattleMapData(
            id = "training_arena",
            worldName = "world",
            spawnX = 0.5,
            spawnY = 80.0,
            spawnZ = 0.5,
            deathCountMode = DeathCountMode.PER_PLAYER,
            deathLimit = 3,
            timeLimitMinutes = 30
        )
    }

    override val game: Game = initGame
    override val status: EntityStatus = TrainingBossStatus()

    override val maxHealth: Double = 2000.0
    override var health: Double = maxHealth

    override val passives: List<BossPassive> = listOf(TrainingBossPassive())
    override val patternSkills: List<PatternSkill> = listOf(TrainingBossPatternSkill())
    override val mapData: BossBattleMapData = MAP_DATA

    override val interactionTag: String = BossCombatConstants.BOSS_INTERACTION_TAG

    override val entity: Entity = spawnInteraction(spawnLocation)

    init {
        passives.forEach { it.inject(this) }
        patternSkills.forEach { it.inject(this) }
    }

    private fun spawnInteraction(location: Location): Entity {
        val interaction = Bukkit.worlds.first().spawnEntity(location, EntityType.INTERACTION) as Interaction
        interaction.scoreboardTags.add(interactionTag)
        interaction.interactionWidth = 2.5f
        interaction.interactionHeight = 3.0f
        return interaction
    }
}

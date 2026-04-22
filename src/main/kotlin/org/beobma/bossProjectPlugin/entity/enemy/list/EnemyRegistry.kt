package org.beobma.bossProjectPlugin.entity.enemy.list

import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.enemy.list.seren.ChosenSerenData
import org.beobma.bossProjectPlugin.game.Game
import kotlin.random.Random

typealias EnemyFactory = (Game) -> EnemyData

data class RegisteredEnemy(
    val mapData: BossBattleMapData,
    val factory: EnemyFactory
)

object EnemyRegistry {
    private val enemies: List<RegisteredEnemy> = listOf(
        RegisteredEnemy(ChosenSerenData.MAP_DATA, ::ChosenSerenData)
    )

    fun randomEnemy(): RegisteredEnemy = enemies[Random.nextInt(enemies.size)]
}

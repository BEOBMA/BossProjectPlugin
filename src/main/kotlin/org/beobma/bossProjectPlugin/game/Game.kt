package org.beobma.bossProjectPlugin.game

import org.beobma.bossProjectPlugin.entity.enemy.EnemyData
import org.beobma.bossProjectPlugin.entity.player.PlayerData
import org.beobma.bossProjectPlugin.entity.enemy.BossBattleMapData
import java.util.UUID

class Game {
    val playerDatas: MutableList<PlayerData> = mutableListOf()

    lateinit var mapData: BossBattleMapData
        private set

    lateinit var bossData: EnemyData
        private set
    val isBossInitialized: Boolean
        get() = this::bossData.isInitialized

    private val deathCountByPlayer: MutableMap<UUID, Int> = mutableMapOf()
    var sharedDeathCount: Int = 0
        private set

    var startedAtMillis: Long = 0L
        private set

    fun setupMap(mapData: BossBattleMapData) {
        this.mapData = mapData
    }

    fun setupBoss(bossData: EnemyData) {
        this.bossData = bossData
    }

    fun initializeBattleState() {
        startedAtMillis = System.currentTimeMillis()
        sharedDeathCount = 0
        deathCountByPlayer.clear()
        playerDatas.forEach { deathCountByPlayer[it.player.uniqueId] = 0 }
    }

    fun increasePlayerDeath(uuid: UUID) {
        deathCountByPlayer[uuid] = (deathCountByPlayer[uuid] ?: 0) + 1
    }

    fun increaseSharedDeath() {
        sharedDeathCount += 1
    }

    fun playerDeathCount(uuid: UUID): Int = deathCountByPlayer[uuid] ?: 0
}
